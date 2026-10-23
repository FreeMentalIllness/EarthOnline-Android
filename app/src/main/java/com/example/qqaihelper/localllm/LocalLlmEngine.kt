package com.example.qqaihelper.localllm

import android.util.Log

/**
 * 本地大模型引擎（JNI 桥接层）—— 移植自开源项目 messageAIHelper（SWSP-Git/messageAIHelper）。
 *
 * **包名与类名被预编译的 liblocalllm.so 锁定**：该 .so 导出的 JNI 符号为
 * `Java_com_example_qqaihelper_localllm_LocalLlmEngine_native*`，因此本类必须精确落在
 * `com.example.qqaihelper.localllm.LocalLlmEngine`，否则 native 方法无法解析（沙箱无 NDK，
 * 无法重编 .so 改符号）。这是移植 MNN 的唯一硬约束。
 *
 * 底层 = libMNN.so（含 MNN::Transformer::Llm：createLLM/load/reset/response/destroy）
 *        + liblocalllm.so（本桥）+ libc++_shared.so（依赖）。
 * 对上层只暴露 4 个动作：load / chat / release（nativeCreate/nativeLoad/nativeChat/nativeRelease）。
 *
 * 线程模型：MNN 的 Llm 实例非线程安全，所有原生调用由桥内同一把锁串行化；
 * 调用方必须在后台线程（IO 线程）使用 chat()。
 */
class LocalLlmEngine {

    @Volatile
    private var handle: Long = 0L

    /** 模型是否已成功加载进内存 */
    @Volatile
    var isLoaded: Boolean = false
        private set

    private val lock = Any()

    companion object {
        const val TAG = "LocalLlm"

        @Volatile
        private var instance: LocalLlmEngine? = null

        @Volatile
        private var libsLoaded = false

        fun get(): LocalLlmEngine = instance ?: synchronized(this) {
            instance ?: LocalLlmEngine().also { instance = it }
        }

        /** 加载 native 库，失败时返回 false（例如非 arm64 设备 / 模拟器）。 */
        @Synchronized
        fun loadNativeLibs(): Boolean {
            if (libsLoaded) return true
            return try {
                System.loadLibrary("MNN")
                System.loadLibrary("localllm")
                libsLoaded = true
                true
            } catch (t: Throwable) {
                Log.e(TAG, "加载本地推理引擎失败: ${t.message}", t)
                false
            }
        }
    }

    /**
     * 从 config.json 路径加载模型（MNN 的 Llm::createLLM 入口）。
     * @return 是否加载成功
     */
    fun load(configPath: String): Boolean {
        if (!loadNativeLibs()) return false
        synchronized(lock) {
            if (isLoaded) return true
            val h = nativeCreate(configPath)
            if (h == 0L) {
                Log.e(TAG, "createLLM 失败，配置路径: $configPath")
                return false
            }
            val ok = nativeLoad(h)
            if (ok) {
                handle = h
                isLoaded = true
            } else {
                nativeRelease(h)
                Log.e(TAG, "模型 load() 返回失败")
            }
            return ok
        }
    }

    /**
     * 发起一次对话补全（阻塞，必须在后台线程调用）。
     * @return 模型输出的纯文本；未加载或出错时返回空串
     */
    fun chat(systemPrompt: String, userMessage: String, maxNewTokens: Int): String {
        synchronized(lock) {
            if (!isLoaded || handle == 0L) return ""
            return nativeChat(handle, systemPrompt, userMessage, maxNewTokens)
        }
    }

    fun release() {
        synchronized(lock) {
            if (handle != 0L) {
                nativeRelease(handle)
                handle = 0L
            }
            isLoaded = false
        }
    }

    private external fun nativeCreate(configPath: String): Long
    private external fun nativeLoad(handle: Long): Boolean
    private external fun nativeChat(handle: Long, systemPrompt: String, userMessage: String, maxNewTokens: Int): String
    private external fun nativeRelease(handle: Long)
}
