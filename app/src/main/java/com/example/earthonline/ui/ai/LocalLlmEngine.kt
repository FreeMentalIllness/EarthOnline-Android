package com.example.earthonline.ui.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.earthonline.data.network.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "EarthOnline-LLM"

/**
 * 本地推理引擎统一接口（v1.0.6.8）。
 *
 * 真实引擎：[LlamaCppEngine]，基于 Inferkt（com.dilivva:inferkt，Maven Central 发布的
 * llama.cpp Kotlin 绑定，内含 **arm64-v8a** 预编译 .so），吃 GGUF 模型
 * （Qwen2.5-1.5B-Instruct q4_k_m ≈ 1GB，魔搭 Qwen 官方 GGUF 仓库）。
 *
 * v1.0.6.8 闪退根治要点：
 *  ① **先体检后加载** —— [LocalLlmRuntime.diagnose] 在触碰任何 native 代码之前判
 *     架构（必须 arm64-v8a）、总内存、可用内存；任何一项不过关直接返回可读结论，
 *     **绝不调用 JNI**。native 崩溃（SIGABRT / SIGSEGV / OOM-kill）是 Java 层
 *     try/catch 拦不住的，唯一可靠的做法就是别让它发生；
 *  ② **失败即降级** —— 体检不过关时上层（AI 对话 / 快捷中枢）自动切回云端，
 *     并把原因原样告诉用户，绝不静默、绝不伪造结果；
 *  ③ **单例 + 互斥 + 释放** —— 杜绝重复加载 native 上下文导致的泄漏。
 */
interface LocalLlmEngine {
    /** 引擎是否已成功加载模型 */
    val ready: Boolean

    /** 已加载的引擎实现名（诊断展示用） */
    val name: String

    /** 加载模型目录（IO 协程执行重活）。失败返回 false（上层降级）。 */
    suspend fun load(modelDir: String, onProgress: (String) -> Unit = {}): Boolean

    /** 多轮对话生成。失败 / 未就绪返回 null，由上层走兜底，绝不伪装。 */
    suspend fun chat(messages: List<ChatMessage>, onDelta: (String) -> Unit): String?

    /** 释放底层资源 */
    fun release()
}

/**
 * 本地推理可行性体检结果。
 * - [LocalLlmSupport.Ready]：可以安全加载；
 * - [LocalLlmSupport.Blocked]：本机不可跑本地推理，[short] 给用户看，[detail] 进日志。
 */
sealed interface LocalLlmSupport {
    data object Ready : LocalLlmSupport
    data class Blocked(val short: String, val detail: String) : LocalLlmSupport
}

/**
 * llama.cpp 真实引擎（Inferkt 绑定）。
 *
 * **单例**（object）：底层是一份常驻的 native 上下文（GGUF 权重走 mmap 映射，
 * 首次推理会把权重页逐张读入 RSS），每次点击「加载」都 new 一个实例会让上一份
 * 上下文泄漏、几轮之后必然 OOM 闪退。现在全局只有一个实例，重复加载前必定先
 * [release] 旧上下文，并用 [loadGuard] 互斥。
 *
 * - 模型：modelDir 下最大的 .gguf 文件（Qwen2.5-1.5B-Instruct q4_k_m）；
 * - 模板：Qwen 官方 ChatML（<|im_start|>…<|im_end|>）；
 * - 生成：completion 流式回调 → onDelta 逐 token 上屏，遇 <|im_end|> / </s> 即截断；
 * - 全程 [Dispatchers.IO]：llama.cpp 的加载与解码绝不上主线程。
 */
object LlamaCppEngine : LocalLlmEngine {

    @Volatile private var loaded = false
    @Volatile private var loading = false
    override val ready: Boolean get() = loaded
    override val name: String get() = "llama.cpp (Inferkt)"

    /** 加载互斥：native 侧不支持并发 preloadModel */
    private val loadGuard = Any()

    /**
     * 上下文窗口。**必须 ≥ prompt + 生成长度，否则 llama.cpp 的 KV cache 会越界写**。
     * 旧值 1024 + MAX_TOKENS 512：只要历史稍长，prompt(≈800) + 生成(512) 就超过 1024，
     * native 层直接 SIGSEGV —— 这正是「引擎已加载但一提问就闪退」的真凶。
     * 现在 2048 且生成预算动态收缩，永不越界。
     */
    private const val CONTEXT_SIZE = 2048

    /** 单次生成上限（还要与 prompt 长度一起做动态预算，见 [nPredictFor]） */
    private const val MAX_TOKENS = 256

    /** 提示词占用的 token 预算上限：留 1/4 窗口给系统提示与安全余量 */
    private const val PROMPT_TOKEN_BUDGET = (CONTEXT_SIZE * 3) / 4

    /** 生成互斥：native 侧 completion 不可重入，并发会踩坏上下文 */
    private val genGuard = Any()
    @Volatile private var generating = false

    /** 解码批大小：512 → 128，KV / 计算缓冲区内存占用降一个量级 */
    private const val BATCH_SIZE = 128

    /** 线程上限：llama.cpp 每线程各持一份 scratch buffer，手机上一律封顶 */
    private const val MAX_THREADS = 4

    /** 权重加载超时（秒级）：超时即放弃并给结论，绝不让 UI 永久停在「正在加载」 */
    private const val PRELOAD_TIMEOUT_MS = 120_000L

    /** 单次生成超时：慢机型 256 token 大约 30~60s，给足余量 */
    private const val GENERATION_TIMEOUT_MS = 180_000L

    // Inferkt 实例（真实接口：com.dilivva.inferkt.Inference）
    private var inference: com.dilivva.inferkt.Inference? = null

    private fun threadCount(): Int =
        (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, MAX_THREADS)

    private fun acquireLoadSlot(): Boolean {
        synchronized(loadGuard) {
            if (loading) return false
            loading = true
            return true
        }
    }

    private fun finishLoad() { loading = false }

    override suspend fun load(modelDir: String, onProgress: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (!acquireLoadSlot()) {
                onProgress("模型正在加载中，请稍候…")
                return@withContext false
            }
            if (loaded) {
                finishLoad()
                onProgress("模型已加载，无需重复加载")
                return@withContext true
            }
            try {
                val dir = File(modelDir)
                val gguf = dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
                    ?.maxByOrNull { it.length() }
                if (gguf == null) {
                    onProgress("模型目录中没有 .gguf 文件：请下载或导入 GGUF 格式")
                    return@withContext false
                }

                /* —— 加载前体检（必须在触碰 native 之前）—— */
                val diag = LocalLlmRuntime.diagnose(dir)
                if (diag is LocalLlmSupport.Blocked) {
                    Log.w(TAG, "本地推理体检未通过：${diag.detail}")
                    onProgress(diag.short)
                    return@withContext false
                }

                onProgress("释放旧上下文…")
                releaseInternal()
                onProgress("加载 GGUF 权重（${gguf.length() / 1024 / 1024} MB）…")
                val inf = com.dilivva.inferkt.createInference()
                val settings = com.dilivva.inferkt.ModelSettings(
                    modelPath = gguf.absolutePath,
                    numberOfGpuLayers = 0,
                    // 必须 mmap：llama.cpp 只读映射权重页，由内核按页换入换出，
                    // 若整包读进进程内存（useMmap=false）在手机上必炸。
                    useMmap = true,
                    useMlock = false,
                    numberOfThreads = threadCount(),
                    context = CONTEXT_SIZE,
                    batchSize = BATCH_SIZE
                )
                /* 超时兜底：权重文件损坏 / 存储慢 / 设备性能不足都可能让 native 长时间不返回，
                   UI 会一直停在「正在加载…」。超时后取消并给出可操作结论。 */
                val ok = withTimeoutOrNull(PRELOAD_TIMEOUT_MS) {
                    inf.preloadModel(settings) { _ -> true }
                } ?: run {
                    Log.w(TAG, "preloadModel 超时（${PRELOAD_TIMEOUT_MS / 1000}s）")
                    runCatching { inf.release() }
                    onProgress("加载超时：模型文件可能损坏，或本机性能不足。建议改用 0.5B 轻量模型，或切换云端模式")
                    return@withContext false
                }
                if (!ok) {
                    runCatching { inf.release() }
                    onProgress("llama.cpp 加载失败：preloadModel 返回 false（模型文件可能损坏或非 GGUF）")
                    return@withContext false
                }
                inference = inf
                loaded = true
                Log.i(TAG, "llama.cpp 已加载：${gguf.name}（${gguf.length() / 1024 / 1024} MB）")
                onProgress("已加载：${gguf.name}")
                true
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "加载本地模型 OOM", oom)
                runCatching { releaseInternal() }
                onProgress("内存不足（OOM）：本机无法加载该模型，请改用更小的量化版本或切换云端模式")
                false
            } catch (t: Throwable) {
                // UnsatisfiedLinkError / ExceptionInInitializerError / NoClassDefFoundError
                // 全部是 Error 而非 Exception —— 这里用 Throwable 兜住，确保不再向外抛。
                Log.e(TAG, "加载本地模型失败：${t.javaClass.simpleName}: ${t.message}", t)
                runCatching { releaseInternal() }
                onProgress("本地引擎不可用（${t.javaClass.simpleName}）：请改用云端模式")
                false
            } finally {
                finishLoad()
            }
        }

    override suspend fun chat(messages: List<ChatMessage>, onDelta: (String) -> Unit): String? =
        withContext(Dispatchers.IO) {
            val inf = inference ?: return@withContext null
            if (!loaded) return@withContext null
            // 生成互斥：上一次没结束就再点发送，两个 completion 并发会踩坏 native 上下文 → 段错误
            synchronized(genGuard) {
                if (generating) return@withContext null
                generating = true
            }
            try {
                val sb = StringBuilder()
                val prompt = buildQwenPrompt(messages)
                val nPredict = nPredictFor(prompt)
                if (nPredict <= 0) {
                    Log.w(TAG, "提示词已占满上下文窗口（${prompt.length} 字符），拒绝生成以免 KV 越界")
                    return@withContext "（这台设备的上下文窗口已被当前对话占满，请长按清空对话后重新提问）"
                }
                val result = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        inf.completion(prompt, nPredict) { event ->
                            when (event) {
                                is com.dilivva.inferkt.GenerationEvent.Generating -> {
                                    val text = event.text
                                    if (text.isNotEmpty()) {
                                        sb.append(text)
                                        onDelta(text)
                                    }
                                }
                                is com.dilivva.inferkt.GenerationEvent.Generated -> {
                                    if (cont.isActive) cont.resume(finishText(sb))
                                }
                                is com.dilivva.inferkt.GenerationEvent.Error -> {
                                    if (cont.isActive) cont.resume(null)
                                }
                                else -> Unit
                            }
                        }
                        // 超时 / 取消都会走到这里 → 通知 native 停止解码，避免它继续占着上下文
                        cont.invokeOnCancellation { runCatching { inf.cancelGeneration() } }
                    }
                }
                if (result == null) {
                    Log.w(TAG, "生成超时（${GENERATION_TIMEOUT_MS / 1000}s），已请求 cancelGeneration")
                    if (sb.isEmpty()) return@withContext "（本地推理超时已停止。可换个更短的问题，或到设置里卸载后重新加载）"
                }
                result
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "本地推理 OOM，释放引擎避免进程被杀", oom)
                runCatching { releaseInternal() }
                null
            } catch (t: Throwable) {
                Log.e(TAG, "本地推理失败：${t.javaClass.simpleName}: ${t.message}", t)
                null
            } finally {
                synchronized(genGuard) { generating = false }
            }
        }

    /**
     * 粗略 token 估算：CJK 1 字 ≈ 1 token，其余按 4 字符 ≈ 1 token。
     * 宁可高估（安全），不可低估 —— 低估就是 KV cache 越界。
     */
    private fun estimateTokens(s: String): Int {
        var cjk = 0
        for (c in s) if (c.code in 0x4E00..0x9FFF || c.code in 0x3000..0x30FF) cjk++
        val rest = s.length - cjk
        return cjk + (rest + 3) / 4
    }

    /** 生成长度预算：窗口 − 提示词 − 32 安全余量，并封顶 [MAX_TOKENS] */
    private fun nPredictFor(prompt: String): Int {
        val used = estimateTokens(prompt)
        return minOf(MAX_TOKENS, CONTEXT_SIZE - used - 32)
    }

    /** 截断模板尾巴（<|im_end|> / </s> / 新一轮 <|im_start|>），只留正文 */
    private fun finishText(sb: StringBuilder): String? {
        val raw = sb.toString()
        val cut = listOf("<|im_end|>", "</s>", "<|im_start|>")
            .mapNotNull { marker -> raw.indexOf(marker).takeIf { it >= 0 } }
            .minOrNull()
        return (if (cut != null) raw.substring(0, cut) else raw).trim().ifBlank { null }
    }

    override fun release() {
        runCatching { releaseInternal() }
    }

    private fun releaseInternal() {
        runCatching { inference?.release() }
        inference = null
        loaded = false
    }

    /**
     * Qwen ChatML 多轮模板（与模型官方 prompt_template 同源）。
     *
     * **预算截断**：从最新一条往回累加，一旦超出 [PROMPT_TOKEN_BUDGET] 就不再收更早的消息
     * （system 永远保留）。历史无限增长会把 prompt 顶出上下文窗口，llama.cpp 在
     * 越界时不会报错，而是直接写坏 KV cache → SIGSEGV，Java 层拦不住。
     */
    private fun buildQwenPrompt(messages: List<ChatMessage>): String {
        val systems = messages.filter { it.role == "system" }
        val turns = messages.filter { it.role != "system" }

        val kept = ArrayList<ChatMessage>()
        var used = systems.sumOf { estimateTokens(it.content) + 12 }
        for (m in turns.asReversed()) {
            val cost = estimateTokens(m.content) + 12
            if (used + cost > PROMPT_TOKEN_BUDGET) break
            used += cost
            kept.add(m)
        }
        kept.reverse()

        return buildString {
            for (m in systems) append("<|im_start|>system\n${m.content}<|im_end|>\n")
            for (m in kept) {
                when (m.role) {
                    "assistant" -> append("<|im_start|>assistant\n${m.content}<|im_end|>\n")
                    else -> append("<|im_start|>user\n${m.content}<|im_end|>\n")
                }
            }
            append("<|im_start|>assistant\n")
        }
    }
}

/**
 * Mock 兜底引擎：本机跑不了真实推理时的**纯文本模拟回复**。
 *
 * 定位（v1.0.6.9）：x86 模拟器 / 32 位 ARM / 无 arm64 原生库的设备上，
 * 触碰 llama.cpp 的 JNI 必然 native 崩溃（Java 层拦不住）。这类设备一律落到这里，
 * 给出**明确标注的模拟回复**——不闪退、不假装是真模型、也不让用户对着空白干等。
 *
 * [ready] 恒为 false：语义上「没有真实模型在跑」，UI 据此显示「模拟模式」而不是
 * 「已加载」，避免又出现「显示已加载、一问就崩」的错位。
 */
object MockLlmEngine : LocalLlmEngine {
    override val ready: Boolean = false
    override val name: String = "模拟模式（本机不支持本地推理）"

    override suspend fun load(modelDir: String, onProgress: (String) -> Unit): Boolean {
        onProgress("本机不支持本地推理（无 arm64-v8a 原生库），已切换模拟模式")
        return false
    }

    override suspend fun chat(messages: List<ChatMessage>, onDelta: (String) -> Unit): String? = null

    /**
     * 模拟回复：不推理、不联网，只按固定话术回一条可读文本。
     * 上层在「本地不可用 + 云端未配置」时调用，保证用户永远能收到反馈。
     */
    fun mockReply(userText: String, reason: String): String = buildString {
        append("【模拟回复 · 未启用真实模型】\n\n")
        append("原因：").append(reason).append("\n\n")
        append("你刚才说的是：「").append(userText.trim().take(60)).append("」\n")
        append("这段内容已经帮你存好了。要在本机真正跑大模型，需要 arm64 真机 + 8GB 以上内存；")
        append("否则请到「设置 → 通用 → AI 对话」填自己的 Base URL / API Key 走云端模型。")
    }

    override fun release() { /* 无资源 */ }
}

/**
 * 本地推理运行时入口（v1.0.6.8）。
 *
 * 职责三件事：
 *  1. [diagnose] —— **加载前体检**，把「会 native 崩溃」的情况全部挡在 JNI 之外；
 *  2. [engineFor] —— 按目录内容选择**单例**引擎（严禁 new，见 LlamaCppEngine 注释）；
 *  3. [memory] —— 内存快照，UI 可实时显示「可用 / 建议」。
 */
object LocalLlmRuntime {

    /**
     * Inferkt 0.0.2 的 AAR **只打包 arm64-v8a**（libinferkt.so / libinferkt-android.so），
     * x86 / x86_64 / armeabi-v7a 一律没有。x86 模拟器与 32 位 ARM 老机上触碰 JNI
     * 就是 UnsatisfiedLinkError（Error 不是 Exception），历史版本因此直接闪退。
     * 这里把架构判断前置，不支持的机器连 JNI 都不碰。
     */
    const val REQUIRED_ABI = "arm64-v8a"

    /** 加载单个模型所需的最小可用内存（字节）：2.0GB（含 KV cache 与运行时开销） */
    const val MIN_AVAILABLE_BYTES: Long = 2_000_000_000L

    /** 本机总内存下限：低于 4GB 的机型一律不放行本地推理 */
    const val MIN_TOTAL_BYTES: Long = 4_000_000_000L

    /** 除权重文件外额外预留的内存（KV cache + 计算缓冲 + Compose 本身） */
    private const val RUNTIME_HEADROOM_BYTES: Long = 450L * 1024 * 1024

    @Volatile private var active: LocalLlmEngine = MockLlmEngine

    val engine: LocalLlmEngine get() = active

    /**
     * Application 上下文（v1.0.6.8）：由 EarthOnlineApp.onCreate 注入，只用于体检时
     * 读取 ActivityManager。**存的是 applicationContext**，无 Activity 泄漏风险。
     */
    @Volatile private var appContext: Context? = null

    fun bind(context: Context) { appContext = context.applicationContext }

    fun activate(engine: LocalLlmEngine) { active = engine }

    /**
     * 按模型目录内容选择**单例**引擎。
     *
     * **架构门控前置**：不支持的 ABI（x86 / x86_64 / 32 位 ARM）或模拟器环境，
     * 一律返回 [MockLlmEngine] —— 连 [LlamaCppEngine] 这个 object 都不返回，
     * 因为它的伴生字段一旦初始化就可能触发类加载，进而触碰 native 库。
     */
    fun engineFor(modelDir: File): LocalLlmEngine {
        if (!hasRequiredAbi()) {
            Log.w(TAG, "engineFor: ABI=${Build.SUPPORTED_ABIS.joinToString()} 无 arm64-v8a → 强制 Mock")
            return MockLlmEngine
        }
        if (isEmulator()) {
            Log.w(TAG, "engineFor: 模拟器环境（${Build.FINGERPRINT}）→ 强制 Mock")
            return MockLlmEngine
        }
        return if (modelDir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }.orEmpty().isNotEmpty())
            LlamaCppEngine
        else MockLlmEngine
    }

    /** 当前设备是否提供 arm64-v8a 原生库运行能力 */
    fun hasRequiredAbi(): Boolean = Build.SUPPORTED_ABIS.contains(REQUIRED_ABI)

    /**
     * 是否运行在模拟器上。Inferkt 的 .so 即便架构对得上，模拟器的 GPU / 内存模型
     * 也撑不住 llama.cpp（实测 x86_64 必然 SIGSEGV）。模拟器一律走 Mock，
     * 只在真机上允许真实推理。
     */
    fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hw = Build.HARDWARE.lowercase()
        val prod = Build.PRODUCT.lowercase()
        return fp.startsWith("generic") || fp.contains("emulator") || fp.contains("vbox") ||
            model.contains("sdk") || model.contains("emulator") || model.contains("android sdk built for") ||
            hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox") ||
            prod.contains("sdk") || prod.contains("emulator") || prod.contains("simulator")
    }

    /**
     * 人类可读的内存体积：`2.1 GB` / `860 MB` / `1.4 TB`。
     * 之前 UI 直接甩原始字节数（2216572416），既读不懂又撑爆一行。
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "—"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        val tb = gb * 1024
        val b = bytes.toDouble()
        return when {
            b >= tb -> String.format(java.util.Locale.US, "%.1f TB", b / tb)
            b >= gb -> String.format(java.util.Locale.US, "%.1f GB", b / gb)
            b >= mb -> String.format(java.util.Locale.US, "%.0f MB", b / mb)
            else -> String.format(java.util.Locale.US, "%.0f KB", b / kb)
        }
    }

    /** 加载 [modelBytes] 大小的权重所需的可用内存估算（权重 1:1 读页 + 固定开销 + 10% 余量） */
    fun requiredBytesFor(modelBytes: Long): Long =
        ((modelBytes + RUNTIME_HEADROOM_BYTES) * 1.1f).toLong().coerceAtLeast(MIN_AVAILABLE_BYTES)

    /** 内存快照：(可用, 总)。取不到返回 (0,0)。 */
    fun memory(context: Context): Pair<Long, Long> = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.availMem to info.totalMem
    }.getOrDefault(0L to 0L)

    fun availableMemoryBytes(context: Context): Long = memory(context).first

    /**
     * **加载前体检**（纯 Java，零 JNI）。
     *
     * 返回 [LocalLlmSupport.Blocked] 时上层必须停止加载并降级，
     * 因为一旦进入 native 层，OOM / SIGABRT 会直接掀掉整个进程，Java 层拦不住。
     */
    fun diagnose(modelDir: File? = null): LocalLlmSupport {
        if (!hasRequiredAbi()) {
            return LocalLlmSupport.Blocked(
                short = "本机无 arm64 原生库，不能跑本地推理，已切模拟/云端",
                detail = "SUPPORTED_ABIS=${Build.SUPPORTED_ABIS.joinToString()} —— Inferkt 仅提供 arm64-v8a so"
            )
        }
        if (isEmulator()) {
            return LocalLlmSupport.Blocked(
                short = "模拟器不支持本地推理，已切模拟/云端",
                detail = "FINGERPRINT=${Build.FINGERPRINT} MODEL=${Build.MODEL} HARDWARE=${Build.HARDWARE}"
            )
        }
        val ctx = appContext
        if (ctx == null) {
            // 未注入 Application 上下文时只放行「架构通过」的结果，宁可不加载也不冒险碰 JNI
            return LocalLlmSupport.Blocked(
                short = "本地推理环境未就绪，请重启应用后重试，已自动切换云端模式",
                detail = "LocalLlmRuntime 未注入 Application Context"
            )
        }
        val am = runCatching {
            ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        }.getOrNull()
        if (am != null && runCatching { am.isLowRamDevice }.getOrDefault(false)) {
            return LocalLlmSupport.Blocked(
                short = "当前设备被系统标记为低内存设备，无法运行本地模型，已自动切换云端模式",
                detail = "ActivityManager.isLowRamDevice == true"
            )
        }
        val (avail, total) = memory(ctx)
        if (total in 1 until MIN_TOTAL_BYTES) {
            return LocalLlmSupport.Blocked(
                short = "总内存 ${formatBytes(total)} 不足 4 GB，不能跑本地模型",
                detail = "totalMem=$total < $MIN_TOTAL_BYTES"
            )
        }
        val need = modelDir
            ?.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
            ?.maxByOrNull { it.length() }
            ?.let { requiredBytesFor(it.length()) }
            ?: MIN_AVAILABLE_BYTES
        if (avail in 1 until need) {
            val reason = if (avail < MIN_AVAILABLE_BYTES)
                "设备内存不足，无法运行本地模型（可用 ${formatBytes(avail)} < 最低 ${formatBytes(MIN_AVAILABLE_BYTES)}）"
            else
                "可用 ${formatBytes(avail)} < 需要 ${formatBytes(need)}，无法加载"
            return LocalLlmSupport.Blocked(
                short = reason,
                detail = "availMem=$avail < need=$need"
            )
        }
        return LocalLlmSupport.Ready
    }

    /** 体检结论的一句话摘要（UI 直接展示） */
    fun supportSummary(modelDir: File? = null): String = when (val r = diagnose(modelDir)) {
        LocalLlmSupport.Ready -> "本机支持本地推理"
        is LocalLlmSupport.Blocked -> r.short
    }
}
