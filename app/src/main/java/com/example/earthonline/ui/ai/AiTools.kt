package com.example.earthonline.ui.ai

import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.data.network.ToolCallRequest
import com.example.earthonline.data.network.ToolFunctionDef
import com.example.earthonline.data.network.ToolSpec
import com.example.earthonline.data.repository.ActivityRepository
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.data.repository.XpEventRepository
import com.example.earthonline.util.XpRules
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.0.6（Tool Calling 批）：暴露给大模型的本地工具 JSON Schema（OpenAI 兼容 tools 参数）。
 *
 * 设计取舍：
 *  - 只给三个**低风险且高频**的操作（建任务 / 完任务 / 记笔记），不给删除、改资料、
 *    清数据等破坏性能力 —— AI 误判一次标题的代价是「多了条任务」，而不是「丢了数据」；
 *  - complete_task 按标题匹配而不是 id（模型看不到数据库主键，只能看到 system prompt
 *    里注入的任务标题），匹配逻辑在执行器里做模糊兜底；
 *  - 参数 Schema 尽量薄：字段越少，小参数量模型（1.5B 档）越不容易编错参。
 */
object AiToolSpecs {

    private fun sProp(desc: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun objSchema(props: JsonObject, required: List<String> = emptyList()): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("properties", props)
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }

    val all: List<ToolSpec> = listOf(
        ToolSpec(
            function = ToolFunctionDef(
                name = "create_task",
                description = "在用户的应用内创建一条待办任务。当用户说「帮我记个任务 / 加个待办 / 提醒我做某事」时调用。",
                parameters = objSchema(
                    buildJsonObject {
                        put("title", sProp("任务标题，简洁明了，不超过 20 个字"))
                        put(
                            "kind", buildJsonObject {
                                put("type", "string")
                                put("enum", buildJsonArray {
                                    add(JsonPrimitive("main")); add(JsonPrimitive("side")); add(JsonPrimitive("todo"))
                                })
                                put("description", "任务类别：main=主线，side=支线，todo=待办清单。用户未指明时用 todo")
                            }
                        )
                        put("note", sProp("补充备注，可选"))
                    },
                    listOf("title")
                )
            )
        ),
        ToolSpec(
            function = ToolFunctionDef(
                name = "complete_task",
                description = "把用户应用内的一条未完成任务标记为已完成。当用户说「我做完了 X / 把 X 打个勾」时调用。",
                parameters = objSchema(
                    buildJsonObject {
                        put("title", sProp("要完成的任务标题，须与系统提示中列出的未完成任务标题一致或高度相似"))
                    },
                    listOf("title")
                )
            )
        ),
        ToolSpec(
            function = ToolFunctionDef(
                name = "create_note",
                description = "在用户的应用内新建一条笔记（世界日志 / 灵感闪念）。当用户想记录想法、日记、灵感、账目时调用。",
                parameters = objSchema(
                    buildJsonObject {
                        put("text", sProp("笔记正文，保留用户原意，可稍作整理但不得虚构内容"))
                        put(
                            "tags", buildJsonObject {
                                put("type", "array")
                                put("items", buildJsonObject { put("type", "string") })
                                put("description", "1~3 个标签，如「记账」「灵感」。可选")
                            }
                        )
                    },
                    listOf("text")
                )
            )
        )
    )
}

/**
 * v1.0.6（Tool Calling 批）：本地工具执行器 —— AI 说要改数据，真正动手的是这里。
 *
 * 两条铁律：
 *  ① 走与手动操作**完全相同**的 Room 仓库链路（含最近动态与 XP 流水），
 *    保证 AI 建的任务 / 记的笔记与手建的口径一致，统计图表不会出现两套真相；
 *  ② 任何失败（参数坏 JSON、标题匹配不到、数据库异常）都折叠成
 *    {"ok":false,"error":...} 文本回传模型让它转告用户 —— 绝不抛出、绝不崩溃。
 */
@Singleton
class AiToolExecutor @Inject constructor(
    private val taskRepo: TaskRepository,
    private val memoRepo: MemoRepository,
    private val activityRepo: ActivityRepository,
    private val xpRepo: XpEventRepository,
    private val json: Json
) {

    /** 执行一次工具调用，返回回传给模型的结论文本（JSON 字符串，模型可读） */
    suspend fun execute(call: ToolCallRequest): String = runCatching { dispatch(call) }
        .getOrElse { t ->
            """{"ok":false,"error":"工具执行异常：${(t.message ?: t.javaClass.simpleName).take(120)}"}"""
        }

    /** 执行结果是否成功（VM 用于在 UI 上标 ✅ / ⚠️） */
    fun isOk(result: String): Boolean = result.contains("\"ok\":true")

    /** 给用户看的执行摘要（一行）；参数解析不出就用工具名兜底，绝不抛异常 */
    fun briefOf(call: ToolCallRequest): String {
        val title = runCatching {
            parseArgs(call.function.arguments)?.str("title").orEmpty()
        }.getOrDefault("")
        return when (call.function.name) {
            "create_task" -> "创建任务「${title.take(18).ifBlank { "?" }}」"
            "complete_task" -> "完成任务「${title.take(18).ifBlank { "?" }}」"
            "create_note" -> "新建笔记"
            else -> call.function.name.ifBlank { "未知工具" }
        }
    }

    private suspend fun dispatch(call: ToolCallRequest): String {
        val name = call.function.name
        val args = parseArgs(call.function.arguments)
            ?: return """{"ok":false,"error":"参数不是合法 JSON，请检查后重试"}"""
        return when (name) {
            "create_task" -> createTask(args)
            "complete_task" -> completeTask(args)
            "create_note" -> createNote(args)
            else -> """{"ok":false,"error":"未知工具：${name.take(40)}"}"""
        }
    }

    private fun parseArgs(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject }.getOrNull()

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    /* ---------------- create_task ---------------- */
    private suspend fun createTask(args: JsonObject): String {
        val title = args.str("title")
        if (title.isBlank()) return """{"ok":false,"error":"title 不能为空"}"""
        val t = title.take(40)
        val kind = listOf("main", "side", "todo").firstOrNull { it == args.str("kind") } ?: "todo"
        val note = args.str("note").ifBlank { null }
        // 排同层末尾（与手动新增同一规则）
        val order = taskRepo.observeAll().first().count { it.parentId == null }
        taskRepo.insert(
            TaskEntity(
                id = uid("task"),
                category = kind,
                title = t,
                status = "planning",
                createdAt = todayStr(),
                lastModified = todayStr(),
                note = note,
                order = order
            )
        )
        activityRepo.add(
            ActivityEntity(id = uid("act"), time = nowIso(), kind = "task", title = "创建了任务：$t")
        )
        return """{"ok":true,"message":"已创建任务「$t」（类别 $kind）"}"""
    }

    /* ---------------- complete_task ---------------- */
    private suspend fun completeTask(args: JsonObject): String {
        val q = args.str("title")
        if (q.isBlank()) return """{"ok":false,"error":"title 不能为空"}"""
        val candidates = taskRepo.observeAll().first()
            .filter { it.deletedAt == null && it.status != "done" && it.title.isNotBlank() }
        // 精确命中优先；否则双向包含里取标题最短的（最具体的那条）
        val hit = candidates.firstOrNull { it.title == q }
            ?: candidates.filter { it.title.contains(q) || q.contains(it.title) }
                .minByOrNull { it.title.length }
            ?: return """{"ok":false,"error":"未找到名为「${q.take(20)}」的未完成任务，请对照系统提示中的任务列表重试"}"""
        taskRepo.update(
            hit.copy(status = "done", doneAt = nowIso(), progress = 100, lastModified = todayStr())
        )
        activityRepo.add(
            ActivityEntity(id = uid("act"), time = nowIso(), kind = "task", title = "完成了任务：${hit.title}")
        )
        // 与手动勾选完成同一 XP 口径
        xpRepo.record(kind = "task", amount = XpRules.TASK_DONE, reason = "完成任务：${hit.title}")
        return """{"ok":true,"message":"已完成任务「${hit.title}」"}"""
    }

    /* ---------------- create_note ---------------- */
    private suspend fun createNote(args: JsonObject): String {
        val text = args.str("text")
        if (text.isBlank()) return """{"ok":false,"error":"text 不能为空"}"""
        val tags = (args["tags"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .orEmpty()
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        val body = text.take(2000)
        memoRepo.insert(
            MemoEntity(
                id = uid("memo"),
                text = body,
                // 双写：text 纯文本兜底（备份兼容），markdown 富文本源
                markdown = body,
                // 无标签写回旧默认值 "note"，与各端对 type 的既有理解一致；AI 落笔一律带「AI」标签可溯源
                type = if (tags.isEmpty()) "AI" else (listOf("AI") + tags).distinct().joinToString(","),
                createdAt = nowIso()
            )
        )
        return """{"ok":true,"message":"已创建笔记（${body.take(12)}…）"}"""
    }
}
