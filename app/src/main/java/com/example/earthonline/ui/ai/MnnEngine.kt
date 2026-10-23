package com.example.earthonline.ui.ai

import android.util.Log
import com.example.earthonline.data.network.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * MNN 本地推理引擎（移植自 messageAIHelper 的 MNN LLM 栈）。
 *
 * 底层 = libMNN.so（含 MNN::Transformer::Llm）+ liblocalllm.so（JNI 桥，包名锁定为
 * com.example.qqaihelper.localllm.LocalLlmEngine，与预编译 .so 的导出符号严格对应）。
 * 本对象实现统一 [LocalLlmEngine] 接口，对上层（对话页 / 快捷中枢）完全透明。
 *
 * —— 防闪退与 8GB 内存专项（死命令）——
 *  - **仅 arm64-v8a 可用**：.so 只打 arm64；非 arm64 设备 / 模拟器在
 *    [com.example.qqaihelper.localllm.LocalLlmEngine.loadNativeLibs] 抛错 → 返回 false，
 *    上层自动降级云端 / 模拟模式，绝不碰会崩的 native；
 *  - **生成长度封顶**：[MAX_NEW_TOKENS]=512。上下文窗口由模型 config.json 的
 *    context_length 决定（主流 Qwen2.5-1.5B-MNN 导出为 1024），本端绝不放大，
 *    KV 预算锁死，防中端机 OOM 闪退；
 *  - **加载 / 生成全程 IO 串行队列** [LocalLlmRuntime.llmDispatcher]，绝不主线程；
 *  - **原生异常全吞**：load / chat 的任何 Throwable 都转成可读结论，绝不向外抛。
 *
 * 流式：MNN 的 nativeChat 是阻塞整段返回（沙箱无 NDK 不可改 JNI 加 token 回调），
 * 这里把整段结果按 [STREAM_CHUNK] 分块 + 小延迟回显，保留打字机观感，且不阻塞 UI。
 */
object MnnEngine : LocalLlmEngine {

    private const val TAG = "EarthOnline-MNN"

    /** 单次生成上限 token（8GB 安全档，远小于模型 1024 上下文，锁死 KV 预算防 OOM） */
    private const val MAX_NEW_TOKENS = 512

    /** 流式回显分块大小（字符），配合小延迟模拟打字机，避免原生阻塞期间 UI 假死 */
    private const val STREAM_CHUNK = 24

    private const val STREAM_DELAY_MS = 12L

    /** 生成超时兜底（慢机型 512 token 约 30~60s，给足余量） */
    private const val GENERATION_TIMEOUT_MS = 180_000L

    /** 预编译 .so 的 JNI 桥（包名锁定，不可改名） */
    private val bridge: com.example.qqaihelper.localllm.LocalLlmEngine
        get() = com.example.qqaihelper.localllm.LocalLlmEngine.get()

    @Volatile private var loaded = false
    override val ready: Boolean get() = loaded
    override val name: String get() = "MNN (Qwen2.5-1.5B)"

    override suspend fun load(modelDir: String, onProgress: (String) -> Unit): Boolean =
        withContext(LocalLlmRuntime.llmDispatcher) {
            if (loaded) { onProgress("模型已加载，无需重复加载"); return@withContext true }
            try {
                // ① 加载 native 库（非 arm64 / 模拟器在此返回 false，绝不触碰后续 JNI）
                if (!com.example.qqaihelper.localllm.LocalLlmEngine.loadNativeLibs()) {
                    onProgress("本机不支持 MNN（缺少 arm64 原生库），已切云端/模拟模式")
                    return@withContext false
                }
                // ② 定位 MNN LLM 配置（Llm::createLLM 入口）
                val cfg = findLlmConfig(File(modelDir))
                if (cfg == null) {
                    onProgress("模型目录缺少 MNN 配置（config.json / llm_config.json），无法加载")
                    return@withContext false
                }
                onProgress("加载 MNN 权重（${cfg.parentFile?.name ?: cfg.name}）…")
                val ok = bridge.load(cfg.absolutePath)
                if (!ok) {
                    onProgress("MNN 加载失败：模型文件可能损坏或非 MNN 格式")
                    return@withContext false
                }
                loaded = true
                runCatching { LocalLlmRuntime.engineReady.value = true }
                Log.i(TAG, "MNN 已加载：${cfg.absolutePath}")
                onProgress("已加载：${cfg.name}")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "MNN 加载异常: ${t.javaClass.simpleName}: ${t.message}", t)
                runCatching { bridge.release() }
                onProgress("MNN 加载失败（${t.javaClass.simpleName}）：请改用云端模式")
                false
            }
        }

    override suspend fun chat(messages: List<ChatMessage>, onDelta: (String) -> Unit): String? =
        withContext(LocalLlmRuntime.llmDispatcher) {
            if (!loaded) return@withContext null
            try {
                // MNN nativeChat 为单轮（system + 最新 user）；多轮历史由云端路径承担，
                // 与 messageAIHelper 行为一致，避免伪造多轮导致模板错位。
                val system = messages.firstOrNull { it.role == "system" }?.content.orEmpty()
                val user = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
                if (user.isBlank()) return@withContext null
                val full = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                    bridge.chat(system, user, MAX_NEW_TOKENS)
                } ?: run {
                    Log.w(TAG, "MNN 生成超时（${GENERATION_TIMEOUT_MS / 1000}s）")
                    return@withContext "（MNN 生成超时，请缩短问题或切换云端）"
                }
                if (full.isBlank()) return@withContext null
                // 分块回显，模拟流式
                var i = 0
                while (i < full.length) {
                    val end = (i + STREAM_CHUNK).coerceAtMost(full.length)
                    val piece = full.substring(i, end)
                    i = end
                    runCatching { onDelta(piece) }
                        .onFailure { Log.e(TAG, "MNN onDelta 异常（已吞，不闪退）", it) }
                    if (i < full.length) delay(STREAM_DELAY_MS)
                }
                full
            } catch (t: Throwable) {
                Log.e(TAG, "MNN 对话异常: ${t.javaClass.simpleName}: ${t.message}", t)
                "（MNN 本地推理异常：${t.message ?: t.javaClass.simpleName}）"
            }
        }

    override fun release() {
        CoroutineScope(LocalLlmRuntime.llmDispatcher).launch {
            runCatching { bridge.release() }
            loaded = false
            runCatching { LocalLlmRuntime.engineReady.value = false }
        }
    }

    override suspend fun releaseAwait() {
        withContext(LocalLlmRuntime.llmDispatcher) {
            runCatching { bridge.release() }
            loaded = false
            runCatching { LocalLlmRuntime.engineReady.value = false }
        }
    }

    override fun cancel() { /* 原生 chat 阻塞不可中断；下一轮请求自然接管 */ }

    /**
     * 在模型目录递归寻找 MNN LLM 配置（Llm::createLLM 读取的入口）：
     * 优先匹配含 `backend_type` / `llm_model` / `context_length` 标记的 JSON，
     * 否则回退到首个 config.json / llm_config.json（兼容不同 MNN 包结构）。
     */
    private fun findLlmConfig(dir: File): File? {
        if (!dir.isDirectory) return null
        var fallback: File? = null
        dir.walkTopDown().forEach { f ->
            if (f.isFile && (f.name == "config.json" || f.name == "llm_config.json")) {
                if (fallback == null) fallback = f
                runCatching {
                    val txt = f.readText()
                    if (txt.contains("backend_type") || txt.contains("llm_model") || txt.contains("context_length"))
                        return f
                }
            }
        }
        return fallback
    }
}
