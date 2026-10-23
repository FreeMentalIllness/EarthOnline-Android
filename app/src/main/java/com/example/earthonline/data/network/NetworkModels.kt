package com.example.earthonline.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 单条对话消息（OpenAI 兼容格式） */
@Serializable
data class ChatMessage(val role: String, val content: String)

/** chat/completions 请求体 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

@Serializable
data class ChatChoice(
    val message: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    val index: Int = 0
)

@Serializable
data class ChatError(
    val message: String = "",
    val type: String = ""
)

/** chat/completions 响应体 */
@Serializable
data class ChatResponse(
    val id: String = "",
    val choices: List<ChatChoice> = emptyList(),
    val error: ChatError? = null
)

/** AI 配置（持久化于 DataStore 的 ai_config 键，JSON） */
@Serializable
data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    /** v1.2.1：模型名默认留空，由用户按自己服务商的文档填写。
     *  之前默认填 gpt-3.5-turbo，会让用户误以为这是官方推荐/可用的模型，
     *  而实际各家地址与模型名都不一样。留空 + 发送前校验更诚实。 */
    val model: String = "",
    /** v1.0.6：模型运行位置。「cloud」= 走上面填写的云端接口；「local」= 走本机本地推理服务（离线）。 */
    val runMode: String = "cloud"
)

/** 本地模型（离线推理）配置，持久化于 DataStore 的 local_model 键，JSON。
 *  仅在 AiConfig.runMode == "local" 时生效。本阶段完成 UI 架构与状态逻辑，
 *  真实推理通过 http://127.0.0.1:<port> 的 OpenAI 兼容接口调用（与云端同一套 chat/completions），
 *  本公版不内嵌大模型运行时，仅预留本地推理调用入口（见 ui.ai.LocalModelManager）。 */
@Serializable
data class LocalModelConfig(
    val enabled: Boolean = false,
    val port: Int = 8090,
    val pluginName: String = "",
    val pluginPath: String = "",
    val autoDiscover: Boolean = true,
    val loaded: Boolean = false,
    val discovered: List<String> = emptyList(),
    /** v1.0.6.5：MNN 模型是否已下载并通过核心文件校验（llm.mnn / llm.mnn.weight） */
    val downloaded: Boolean = false,
    /** v1.0.6.5：模型目录（filesDir/mnn_models/<modelId>） */
    val modelDir: String = "",
    /** v1.0.6.5：已下载模型总字节数（展示用） */
    val sizeBytes: Long = 0L,
    /** v1.0.6.5：模型名（如 Qwen2.5-1.5B-Instruct-MNN），下载目标与展示用 */
    val modelName: String = ""
)

/**
 * WebDAV 配置（持久化于 DataStore 的 webdav_config 键，JSON）。
 * path 为远端文件路径，留空则用 CloudSyncManager.DEFAULT_REMOTE_PATH（与 Web 端默认一致），
 * 新增字段带默认值，旧存档反序列化不受影响。
 */
@Serializable
data class WebDavConfig(
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val path: String = ""
)
