package com.example.earthonline.ui.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.earthonline.data.network.ChatMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "EarthOnline-LLM"

/**
 * 内存看门狗中断生成时的固定文案（v1.0.6.12 · 死命令 #2）。
 * 引擎返回该串 = 因可用内存跌破 500MB 而中断；AiViewModel 据此弹同文案 Toast。
 */
const val AI_MEMORY_ABORT_MSG = "设备内存不足，AI 已中断生成，请尝试清理后台"

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

    /**
     * 挂起等待「进行中的生成结束 + native 资源真正释放」后返回（v1.0.6.13 死命令 #1）。
     * 默认实现退化为 [release]；真实引擎覆写为在专属线程上持 Mutex 执行，
     * 供「卸载并删文件」等需要确定 fd 已关闭的调用方使用。
     */
    suspend fun releaseAwait() { release() }

    /** 取消正在进行的生成（不释放模型，供页面退出时中断推理协程，避免向已销毁 UI 写数据） */
    fun cancel()
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
     * v1.0.6.12（Redmi Note 12 Pro / 8GB 专项）：2048 → 1024。
     * **终极重构 · 工程四（死命令）：1024 → 512。**
     *  - 死命令要求 n_ctx = 512 防 OOM：KV cache 随 n_ctx 线性增长，512 相比 1024
     *    再砍半，中低端机（6~8GB）加载 1.5B 权重后仍有充足余量，彻底消除内存悬崖；
     *  - 512 配合「2 轮历史截断 + 384 token prompt 预算 + 动态生成预算（≤160）」
     *    数学上不可能越界（见 nPredictFor / buildQwenPrompt）；
     *  - 死命令红线：**绝不允许超过 2048**（现为 512，远在红线内）。
     */
    private const val CONTEXT_SIZE = 512

    /**
     * 单次生成上限（还要与 prompt 长度一起做动态预算，见 [nPredictFor]）。
     * 终极重构：256 → **160** —— 512 窗口下 prompt 预算 384，剩余须给生成留足余量，
     * 160 是「够说完一句话」与「绝不越界」的平衡点。
     */
    private const val MAX_TOKENS = 160

    /** 提示词占用的 token 预算上限：留 1/4 窗口给系统提示与安全余量 */
    private const val PROMPT_TOKEN_BUDGET = (CONTEXT_SIZE * 3) / 4

    /** 滑动窗口硬上限：**只保留最近 2 轮对话（= 2 条 user + 2 条 assistant）**，更早的整段丢弃。
     *  v1.0.6.12 死命令：8GB 中端机上历史无限增长 = KV 越界 SIGSEGV（Java 层拦不住），
     *  2 轮足够支撑连贯追问，又把 prompt 尺寸彻底钉死。 */
    private const val MAX_TURNS = 4

    /**
     * 生成互斥（v1.0.6 融合重构 · 死命令 #5 防闪退）：
     * 从「并发即丢弃」改为 **Mutex 排队** —— 对话页与主页快捷中枢同时发起请求时，
     * 后到的一个挂起等待前者完成再进 native，请求一条不丢；llama.cpp 上下文仍严格
     * 串行，绝不并发踩坏 KV cache（那是 Java 层拦不住的 SIGSEGV）。
     */
    private val genMutex = Mutex()

    /** 解码批大小：512 → 128，KV / 计算缓冲区内存占用降一个量级 */
    private const val BATCH_SIZE = 128

    /** 线程上限（v1.0.6.12 死命令）：**恒为 4** —— 天玑 1080 只有 4 个 A78 大核有效，
     *  开到 6~7 线程会把 A55 小核也拉进来做浮点重活，反而互相抢 L3 造成卡顿掉帧。 */
    private const val MAX_THREADS = 4

    /** 权重加载超时（秒级）：超时即放弃并给结论，绝不让 UI 永久停在「正在加载」 */
    private const val PRELOAD_TIMEOUT_MS = 120_000L

    /** 单次生成超时：慢机型 256 token 大约 30~60s，给足余量 */
    private const val GENERATION_TIMEOUT_MS = 180_000L

    /**
     * 内存看门狗安全阈值（v1.0.6.12 · 死命令 #2）：可用内存低于 **500MB** 即中断生成。
     * 低于此线继续解码 = 系统随时 LMK 杀进程或 native OOM，宁可主动停也不闪退。
     */
    private const val MEMORY_ABORT_THRESHOLD = 500_000_000L

    /**
     * 采样参数（v1.0.6.10）：Inferkt 的 [com.dilivva.inferkt.ModelSettings] 不含 temperature，
     * 必须加载后显式 [com.dilivva.inferkt.Inference.setSamplingParams] 才生效；不设置则模型用库
     * 默认采样（实测极易「变哑巴」或疯狂重复）——这正是此前「模型能加载、但一对话就没反应」的根因之一。
     * temp=0.7 兼顾创造与稳定；topK=40 / topP=0.9 / minP=0.05 是 Qwen2.5-Instruct 的标准推理配置。
     */
    private val SAMPLING = com.dilivva.inferkt.SamplingSettings(
        temp = 0.7f, topP = 0.9f, minP = 0.05f, topK = 40
    )

    // Inferkt 实例（真实接口：com.dilivva.inferkt.Inference）
    private var inference: com.dilivva.inferkt.Inference? = null

    private fun threadCount(): Int =
        // v1.0.6.12：恒 min(4, 物理核数) —— 大小核异构 SoC 上超过 4 线程必卡（死命令 #1）
        minOf(MAX_THREADS, Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)

    private fun acquireLoadSlot(): Boolean {
        synchronized(loadGuard) {
            if (loading) return false
            loading = true
            return true
        }
    }

    private fun finishLoad() { loading = false }

    override suspend fun load(modelDir: String, onProgress: (String) -> Unit): Boolean =
        withContext(LocalLlmRuntime.llmDispatcher) {
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
                // 释放必须与进行中的生成互斥（v1.0.6.13）：等 chat 跑完、锁拿到后才释放，
                // 杜绝「边生成边释放」的 use-after-free SIGSEGV
                genMutex.withLock { releaseInternal() }
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
                        .also { if (it) runCatching { inf.setSamplingParams(SAMPLING) } }
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
        withContext(LocalLlmRuntime.llmDispatcher) {
            if (!loaded) return@withContext null
            // Mutex 排队（v1.0.6.13 死命令 #1）：对话页与快捷中枢同时请求时后到者挂起等待，
            // 请求一条不丢；native completion 严格串行，绝不并发踩坏上下文。
            // **推理实例必须在锁内捕获** —— 锁外捕获的引用可能已被并发的 load/release 释放
            // （use-after-free 是快捷中枢闪退的残留根因）。
            genMutex.withLock {
            val inf = inference ?: return@withLock null
            if (!loaded) return@withLock null
            try {
                val sb = StringBuilder()
                // 内存预检（死命令 #2）：可用 < 500MB 直接拒绝生成，连 native 都不碰
                if (LocalLlmRuntime.availableMemoryNow() < MEMORY_ABORT_THRESHOLD) {
                    Log.w(TAG, "生成前内存预检失败：可用低于 500MB，拒绝生成")
                    return@withLock AI_MEMORY_ABORT_MSG
                }
                val prompt = buildQwenPrompt(messages)
                val nPredict = nPredictFor(prompt)
                if (nPredict <= 0) {
                    Log.w(TAG, "提示词已占满上下文窗口（${prompt.length} 字符），拒绝生成以免 KV 越界")
                    return@withLock "（这台设备的上下文窗口已被当前对话占满，请长按清空对话后重新提问）"
                }
                // done 标志：Generated / Error 任一触发后只 resume 一次，杜绝重复 resume 导致的
                // 「挂起函数已恢复」崩溃；同时兜底 Generated 事件偶发丢失时不会干等整个超时窗。
                val done = java.util.concurrent.atomic.AtomicBoolean(false)
                // 内存看门狗标志（死命令 #2）：看门狗协程置位后，生成结束按此决定中断文案
                val memoryAborted = java.util.concurrent.atomic.AtomicBoolean(false)
                val result = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                    coroutineScope {
                        // 看门狗：独立线程每秒轮询可用内存，跌破 500MB 立即请求 native 停止解码 ——
                        // 主动中断永远好过被系统 LMK 杀进程（死命令 #2）
                        val watchdog = launch(kotlinx.coroutines.Dispatchers.Default) {
                            while (isActive) {
                                delay(1_000)
                                val avail = LocalLlmRuntime.availableMemoryNow()
                                if (avail < MEMORY_ABORT_THRESHOLD) {
                                    memoryAborted.set(true)
                                    Log.w(TAG, "内存看门狗：可用 ${LocalLlmRuntime.formatBytes(avail)} < 500MB，中断生成")
                                    runCatching { inf.cancelGeneration() }
                                    break
                                }
                            }
                        }
                        try {
                            suspendCancellableCoroutine { cont ->
                                inf.completion(prompt, nPredict) { event ->
                                    when (event) {
                                        is com.dilivva.inferkt.GenerationEvent.Generating -> {
                                            val text = event.text
                                            if (text.isNotEmpty()) {
                                                sb.append(text)
                                                // 流式回调兜底：任何异常都吞掉并打印 Logcat，绝不因为
                                                // onDelta 里的意外（如 UI 已销毁）把进程掀掉（死命令要求）
                                                runCatching { onDelta(text) }
                                                    .onFailure { Log.e(TAG, "流式 onDelta 回调异常（已吞掉，不闪退）", it) }
                                            }
                                        }
                                        is com.dilivva.inferkt.GenerationEvent.Generated -> {
                                            if (done.compareAndSet(false, true)) cont.resume(finishText(sb))
                                        }
                                        is com.dilivva.inferkt.GenerationEvent.Error -> {
                                            val err = (event as? com.dilivva.inferkt.GenerationEvent.Error)?.error
                                            if (done.compareAndSet(false, true))
                                                cont.resume("（本地推理出错：${err ?: "未知错误"}）")
                                        }
                                        else -> Unit
                                    }
                                }
                                // 超时 / 取消都会走到这里 → 通知 native 停止解码，避免它继续占着上下文
                                cont.invokeOnCancellation { runCatching { inf.cancelGeneration() } }
                            }
                        } finally {
                            watchdog.cancel()
                        }
                    }
                }
                // 看门狗触发了内存中断：不管 native 吐了什么，统一按中断文案返回（上层据此弹 Toast）
                if (memoryAborted.get()) {
                    Log.w(TAG, "生成因内存不足被中断（已生成 ${sb.length} 字）")
                    return@withLock AI_MEMORY_ABORT_MSG
                }
                // 终态处理：正常返回 finishText；超时则把已流式输出的内容作为最终结果（不丢），
                // 否则给可读兜底文案 —— 绝不让「对话无反应」卡住。
                if (result != null) return@withLock result
                if (sb.isNotEmpty()) {
                    Log.w(TAG, "生成超时（${GENERATION_TIMEOUT_MS / 1000}s），返回已生成部分（${sb.length} 字）")
                    return@withLock sb.toString().trim()
                        .ifBlank { "（本地推理超时已停止，已返回部分内容。可换更短的问题）" }
                }
                Log.w(TAG, "生成超时且无任何输出")
                "（本地推理超时已停止。可换个更短的问题，或到设置里卸载后重新加载）"
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "本地推理 OOM，释放引擎避免进程被杀", oom)
                runCatching { releaseInternal() }
                "（本地推理内存不足，已中止并释放模型。请改用更小的量化模型，或切换云端模式）"
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // 真实报错必须上抛给上层气泡展示，不能再静默返回 null 让用户对着空白干等
                Log.e(TAG, "本地推理失败：${t.javaClass.simpleName}: ${t.message}", t)
                "（本地推理异常：${t.message ?: t.javaClass.simpleName}）"
            }
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

    /**
     * 释放底层资源（v1.0.6.13 死命令 #1 加固）。
     * **释放必须与生成互斥** —— 直接在调用线程释放会与进行中的 completion 产生
     * use-after-free → SIGSEGV（这正是「快捷中枢用着用着闪退」的残留根因之一：
     * 用户在设置里卸载/重载时，快捷中枢或对话页的生成可能还在跑）。
     * 现改为投递到专属推理线程 + Mutex 排队执行（异步、立即返回、绝不阻塞主线程）。
     */
    override fun release() {
        kotlinx.coroutines.CoroutineScope(LocalLlmRuntime.llmDispatcher).launch {
            genMutex.withLock { releaseInternal() }
        }
    }

    /** 挂起版本：等「生成结束 + 释放完成」才返回（卸载删盘路径用） */
    override suspend fun releaseAwait() {
        withContext(LocalLlmRuntime.llmDispatcher) {
            genMutex.withLock { releaseInternal() }
        }
    }

    override fun cancel() {
        // 仅中断当前生成，不释放模型：退出对话页时调用，避免下次进页面又得重载，
        // 同时杜绝「协程还在跑、页面已销毁」的越界写。
        runCatching { inference?.cancelGeneration() }
    }

    private fun releaseInternal() {
        runCatching { inference?.release() }
        inference = null
        loaded = false
        // 反向通知全局状态流：引擎已不再就绪（快捷中枢 / 状态卡据此刷新）
        runCatching { LocalLlmRuntime.engineReady.value = false }
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
            // ① 滑动窗口第一道闸：只保留最近 2 轮（4 条），更早的整段丢弃（死命令 #1）
            .takeLast(MAX_TURNS)

        // ② 兜底（v1.0.6.13）：最新一条消息（= 当前问题）永远保留 —— 超预算时截断内容
        //    而不是整条丢弃。旧逻辑下用户粘贴长文会让最新问题被预算闸丢掉，
        //    模型收不到问题直接「装聋」，看起来就像闪退级的故障。
        val systemCost = systems.sumOf { estimateTokens(it.content.orEmpty()) + 12 }
        val safeTurns = if (turns.isNotEmpty()) {
            val last = turns.last()
            val allowance = (PROMPT_TOKEN_BUDGET - systemCost).coerceAtLeast(96) - 12
            val lastCost = estimateTokens(last.content.orEmpty())
            if (lastCost > allowance) {
                // CJK 1 字 ≈ 1 token，取预算的 80% 作字符上限，留 20% 余量
                turns.dropLast(1) + last.copy(content = last.content.orEmpty().take((allowance * 4) / 5) + "…")
            } else turns
        } else turns

        val kept = ArrayList<ChatMessage>()
        var used = systemCost
        for (m in safeTurns.asReversed()) {
            val cost = estimateTokens(m.content.orEmpty()) + 12
            if (used + cost > PROMPT_TOKEN_BUDGET) break
            used += cost
            kept.add(m)
        }
        kept.reverse()

        return buildString {
            for (m in systems) append("<|im_start|>system\n${m.content.orEmpty()}<|im_end|>\n")
            for (m in kept) {
                when (m.role) {
                    "assistant" -> append("<|im_start|>assistant\n${m.content.orEmpty()}<|im_end|>\n")
                    else -> append("<|im_start|>user\n${m.content.orEmpty()}<|im_end|>\n")
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

    override fun cancel() { /* 无资源 */ }
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

    /** 本地引擎就绪状态流：供主页 AI 快捷中枢 / 状态卡实时订阅。
     *  加载成功 → true；卸载 / 释放 → false。快捷中枢据此判定「是否可用」。 */
    val engineReady = MutableStateFlow(false)

    /**
     * 专属推理上下文（v1.0.6.13 死命令 #1）：**Dispatchers.IO + 单并发**。
     *  - llama.cpp 是单例 native 上下文，多线程并发调用 completion 会踩坏 KV cache →
     *    直接 SIGSEGV（Java 层拦不住）；
     *  - `Dispatchers.IO.limitedParallelism(1)` = IO 线程池上的严格串行队列：快捷中枢与
     *    对话页的所有 load / chat / release 全部在此排队，互斥且不阻塞主线程；
     *  - 替换旧 `newSingleThreadContext`（delicate API，进程退出不回收线程）。
     */
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val llmDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1)

    /**
     * Application 上下文（v1.0.6.8）：由 EarthOnlineApp.onCreate 注入，只用于体检时
     * 读取 ActivityManager。**存的是 applicationContext**，无 Activity 泄漏风险。
     */
    @Volatile private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        // 启动时清理上次逻辑卸载（此时 fd 必然已释放）残留的模型目录，避免空间永久占用
        LocalModelManager.cleanupUnloadedOnStartup(context.applicationContext)
    }

    fun activate(engine: LocalLlmEngine) {
        active = engine
        // 引擎切换即广播就绪状态（快捷中枢订阅此流，无需轮询）
        runCatching { engineReady.value = active.ready }
    }

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
        return when {
            // MNN 模型（llm.mnn + llm.mnn.weight）优先：v1.0.6 移植自 messageAIHelper 的 MNN 栈，
            // 对 Qwen2.5 有专项优化、内存更省，作为 arm64 真机的首选本地引擎。
            LocalModelManager.hasMnn(modelDir) -> MnnEngine
            // 否则若有 GGUF，回落 llama.cpp（Inferkt）引擎
            modelDir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }.orEmpty().isNotEmpty() -> LlamaCppEngine
            else -> MockLlmEngine
        }
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
     * 当前可用内存（字节）—— 内存看门狗专用（v1.0.6.12）。
     * 未注入 Application 上下文 / 读取失败时返回 [Long.MAX_VALUE]：看门狗永不误报中断。
     */
    fun availableMemoryNow(): Long {
        val ctx = appContext ?: return Long.MAX_VALUE
        return runCatching { availableMemoryBytes(ctx) }.getOrDefault(Long.MAX_VALUE)
    }

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
            ?.let { dir ->
                val gguf = dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
                    .orEmpty().maxByOrNull { it.length() }?.length() ?: 0L
                // MNN 权重（llm.mnn.weight）通常占模型体积的绝大部分，一并计入内存预算
                val mnnW = dir.walkTopDown()
                    .firstOrNull { it.isFile && it.name == "llm.mnn.weight" && it.length() > 0 }
                    ?.length() ?: 0L
                maxOf(gguf, mnnW)
            }
            ?.let { requiredBytesFor(it) }
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
