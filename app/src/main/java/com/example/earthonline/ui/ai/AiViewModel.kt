package com.example.earthonline.ui.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.AiRepository
import com.example.earthonline.data.network.ChatMessage
import com.example.earthonline.data.network.LocalModelConfig
import com.example.earthonline.ui.ai.LocalModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class UiMessage(val role: String, val content: String)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val repo: AiRepository,
    private val settings: SettingsDataStore,
    private val json: Json,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _messages = MutableStateFlow<List<UiMessage>>(
        listOf(UiMessage("assistant", "你好，我是地球Online的「系统」。告诉我你今天想完成什么？"))
    )
    val messages = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val aiConfigFlow = settings.aiConfig
    /** 本地模型（离线推理）配置 JSON */
    val localModelJson: Flow<String> = settings.localModel

    fun onInputChange(s: String) { _input.value = s }

    fun parseAiConfig(s: String): AiConfig =
        runCatching { json.decodeFromString<AiConfig>(s) }.getOrDefault(AiConfig())

    fun saveAiConfig(c: AiConfig) = viewModelScope.launch {
        settings.setAiConfig(json.encodeToString(AiConfig.serializer(), c))
    }

    fun parseLocalModel(s: String): LocalModelConfig =
        runCatching { json.decodeFromString<LocalModelConfig>(s) }.getOrDefault(LocalModelConfig())

    fun saveLocalModel(c: LocalModelConfig) = viewModelScope.launch {
        settings.setLocalModel(json.encodeToString(LocalModelConfig.serializer(), c))
    }

    fun clearError() { _error.value = null }

    /**
     * 发送（v1.0.6.5 三态开关）：
     *  - off：直接提示已关闭，不发任何请求；
     *  - local：走本地引擎（当前占位 → 诚实提示；真实 MNN 引擎接入后自动流式）；
     *  - cloud（默认）：OpenAI 兼容 SSE 流式，逐 token 打字机。
     */
    fun send() {
        val text = _input.value.trim()
        if (text.isBlank() || _loading.value) return
        _input.value = ""
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            val rawJson = settings.aiConfig.first()
            var mode = runCatching { json.decodeFromString<AiConfig>(rawJson) }.getOrNull()
                ?.runMode?.ifBlank { "cloud" } ?: "cloud"

            if (mode == "off") {
                _loading.value = false
                _error.value = "AI 已关闭，请到「设置 → 通用 → AI 对话」开启"
                return@launch
            }

            _messages.value = _messages.value + UiMessage("user", text)
            // 流式：先占位一条空 assistant 消息，再逐 token 增量拼接（打字机效果）
            _messages.value = _messages.value + UiMessage("assistant", "")
            val idx = _messages.value.lastIndex
            val sb = StringBuilder()
            fun commit(s: String) {
                // 防御：并发流式 / 用户清空对话都可能让 idx 失效，
                // 直接下标赋值会抛 IndexOutOfBounds 把整个页面掀掉
                _messages.value = _messages.value.toMutableList().apply {
                    if (idx in indices) this[idx] = UiMessage("assistant", s)
                    else add(UiMessage("assistant", s))
                }
            }

            /* 本地模式「体检」：任何一项不过关都在触碰 JNI 之前降级到云端。
               native 层的 OOM / SIGABRT 是 try/catch 拦不住的进程级崩溃，
               唯一可靠的做法就是不让它发生。 */
            val degradeNotice: String? = if (mode == "local") {
                val localCfg = parseLocalModel(settings.localModel.first())
                val dir = java.io.File(
                    localCfg.modelDir.ifBlank {
                        LocalModelManager.modelDir(appContext, ModelDownloader.MODEL_NAME).absolutePath
                    }
                )
                when (val diag = LocalLlmRuntime.diagnose(dir)) {
                    is LocalLlmSupport.Blocked -> diag.short
                    LocalLlmSupport.Ready -> if (LocalLlmRuntime.engine.ready) null
                    else "本地模型尚未加载：请先到「设置 → 通用 → AI 对话」导入 GGUF 并点击「加载模型」。当前已改用云端模式。"
                }
            } else null
            if (degradeNotice != null) mode = "cloud"

            try {
                if (mode == "local") {
                    val engine = LocalLlmRuntime.engine
                    val history = _messages.value.take(idx).map { ChatMessage(it.role, it.content) }
                    val reply = engine.chat(history) { delta ->
                        sb.append(delta)
                        commit(sb.toString())
                    }
                    if (reply.isNullOrBlank() && sb.isEmpty()) {
                        commit("本地推理无返回：可在「设置 → 通用 → AI 对话」卸载后重试，或改用云端模式")
                    }
                } else {
                    val cfg = parseAiConfig(rawJson)
                    val history = _messages.value.take(idx).map { ChatMessage(it.role, it.content) }
                    when {
                        degradeNotice != null && (cfg.baseUrl.isBlank() || cfg.apiKey.isBlank() || cfg.model.isBlank()) ->
                            // 本地不可用 + 云端未配置：给一条**明确标注的模拟回复**，
                            // 绝不留空白让用户以为又卡死了（也绝不伪装成真模型输出）
                            commit(
                                com.example.earthonline.ui.ai.MockLlmEngine.mockReply(text, degradeNotice)
                            )
                        cfg.baseUrl.isBlank() || cfg.apiKey.isBlank() ->
                            commit("请先在「设置 → AI 对话」中配置 Base URL 与 API Key")
                        cfg.model.isBlank() ->
                            commit("请先在「设置 → AI 对话」中填写模型名（按服务商文档照抄）")
                        else -> {
                            if (degradeNotice != null) commit(degradeNotice)
                            // 降级提示与真实回答分两条消息：提示不参与上下文，避免污染模型输入
                            val clean = cfg
                            repo.streamChat(clean, history).collect { delta ->
                                sb.append(delta)
                                commit(
                                    (if (degradeNotice != null) degradeNotice + "\n\n" else "") + sb.toString()
                                )
                            }
                            if (sb.isEmpty()) commit(degradeNotice ?: "（无返回内容）")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 注意抓 Throwable 而不是 Exception：UnsatisfiedLinkError / OOM 都是 Error，
                // 历史上正是它们穿透了 Exception 分支，直接把进程掀掉
                android.util.Log.e("EarthOnline-AI", "chat failed: ${t.javaClass.simpleName}", t)
                val friendly = when (t) {
                    is java.net.UnknownHostException, is java.net.ConnectException ->
                        "无网络连接，请检查网络后重试"
                    is java.net.SocketTimeoutException ->
                        "请求超时，请稍后重试"
                    is java.io.IOException ->
                        "网络异常，请检查网络后重试"
                    is OutOfMemoryError ->
                        "内存不足，已中止本次请求。可到「设置 → AI 对话」点「卸载释放」后重试"
                    is UnsatisfiedLinkError, is NoClassDefFoundError ->
                        "本地推理原生库不可用，已切模拟模式。请改用云端模型"
                    else -> "服务异常：${t.message ?: t.javaClass.simpleName}"
                }
                commit(friendly)
            } finally {
                _loading.value = false
            }
        }
    }
}
