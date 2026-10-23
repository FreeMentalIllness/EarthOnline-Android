@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.example.earthonline.data.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * v1.0.6（Tool Calling 批）注解说明：
 * 全局 Json 配置是 encodeDefaults = true（备份/导出语义要求，不可动），
 * 新加的可空字段若不挂 @EncodeDefault(NEVER)，空值也会被序列化进请求体
 * （"tools":null / "tool_calls":null），部分严格网关会直接 400。
 * NEVER 模式 = 值等于默认值时绝不写出；有值时正常写出。解码不受影响。
 */

/** 单条对话消息（OpenAI 兼容格式）。
 *  content 可空：模型发起 tool_calls 时协议允许 content=null（多数服务端真的回 null），
 *  非空类型会在解码阶段直接抛异常，让整条对话报废。 */
@Serializable
data class ChatMessage(
    val role: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val content: String? = null,
    /** assistant 消息携带：模型请求调用的本地工具列表 */
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("tool_calls") val toolCalls: List<ToolCallRequest>? = null,
    /** role=tool 消息携带：本条执行结果对应的调用 id */
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("tool_call_id") val toolCallId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null
)

/** chat/completions 请求体 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    /** v1.0.6（Tool Calling 批）：本地工具定义；null = 不传（纯对话请求体与旧版完全一致） */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tools: List<ToolSpec>? = null
)

/** function 工具定义（tools 数组元素，OpenAI 兼容） */
@Serializable
data class ToolSpec(
    val type: String = "function",
    val function: ToolFunctionDef
)

@Serializable
data class ToolFunctionDef(
    val name: String,
    val description: String = "",
    /** JSON Schema，JsonObject 原样透传给服务端，客户端不做语义解析 */
    val parameters: JsonObject = JsonObject(emptyMap())
)

/** 模型发起的一次工具调用（assistant 消息 tool_calls 数组元素） */
@Serializable
data class ToolCallRequest(
    val id: String = "",
    val type: String = "function",
    val function: ToolCallFunction = ToolCallFunction()
)

@Serializable
data class ToolCallFunction(
    val name: String = "",
    /** OpenAI 约定：arguments 是「JSON 字符串」而非对象，由客户端自行解析 */
    val arguments: String = ""
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
