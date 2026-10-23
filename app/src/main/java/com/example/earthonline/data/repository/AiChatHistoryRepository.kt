package com.example.earthonline.data.repository

import com.example.earthonline.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 对话全局历史（v1.0.6 融合重构 · 死命令 #5 数据同步）。
 *
 * **单一事实源**：主页 AI 快捷中枢与 AI 对话页共享同一份 ChatHistory ——
 * 快捷中枢里说过的每句话与 AI 的结果，都会追加到这里；进入对话页能看见完整脉络，
 * 对话页的每次问答也会写回这里。持久化走 DataStore（JSON 列表，最近 [MAX_TURNS] 条封顶），
 * 不动 Room 表结构（规避迁移风险）。
 *
 * 序列化用独立 [ChatTurn] 而非 UiMessage：UiMessage 属于 UI 层，仓储不该依赖界面模型。
 */
@Singleton
class AiChatHistoryRepository @Inject constructor(
    private val settings: SettingsDataStore,
    private val json: Json
) {

    /** 一轮对话消息（role: user / assistant；content: 正文） */
    @Serializable
    data class ChatTurn(val role: String, val content: String)

    companion object {
        /** 历史封顶条数：超出一并裁掉最旧的（DataStore JSON 不能无限膨胀） */
        const val MAX_TURNS = 100
    }

    /** 读取全部历史（按时间升序）。解析失败一律返回空列表，绝不因脏数据闪退。 */
    suspend fun load(): List<ChatTurn> = runCatching {
        val raw = settings.aiChatHistoryJson.first()
        if (raw.isBlank()) emptyList()
        else json.decodeFromString(ListSerializer(ChatTurn.serializer()), raw)
    }.getOrDefault(emptyList())

    /** 追加一条用户消息 + 一条 AI 回复（快捷中枢与对话页统一入口）。 */
    suspend fun append(userText: String, assistantText: String) {
        if (userText.isBlank() && assistantText.isBlank()) return
        runCatching {
            val current = load().toMutableList()
            if (userText.isNotBlank()) current.add(ChatTurn("user", userText))
            if (assistantText.isNotBlank()) current.add(ChatTurn("assistant", assistantText))
            while (current.size > MAX_TURNS) current.removeAt(0)
            settings.setAiChatHistoryJson(json.encodeToString(ListSerializer(ChatTurn.serializer()), current))
        }
    }

    /** 清空全部对话历史（对话页「清空对话」与清除数据共用）。 */
    suspend fun clear() {
        runCatching { settings.setAiChatHistoryJson("") }
    }
}
