package com.example.earthonline.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.ItemEntity
import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.AiRepository
import com.example.earthonline.data.network.LocalModelConfig
import com.example.earthonline.ui.ai.LocalModelManager
import com.example.earthonline.data.network.ChatMessage
import com.example.earthonline.data.repository.ActivityRepository
import com.example.earthonline.data.repository.ItemRepository
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.IconChip
import com.example.earthonline.ui.components.ModuleTints
import com.example.earthonline.ui.theme.GlassSurface
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/* ============================================================
 * v1.0.19：主页「AI 快捷中枢」—— 一句话自动归档。
 * v1.0.20：语音输入方案重构 —— 放弃系统 SpeechRecognizer，
 *          改为「弹抽屉即聚焦 + 唤起软键盘」，语音用输入法键盘上的麦克风。
 * v1.0.21：意图识别「去伪存真」（死命令）——
 *   - 【废弃】v1.0.19/20 的本地正则 / 关键词猜测（QuickCaptureParser）已整段删除，
 *     它本质上是「关键词匹配」而不是 AI；
 *   - 【真实 AI】唯一识别路径改为调用「设置 → 通用 → AI 对话」里用户自己填的
 *     Base URL + API Key + 模型名，走标准 OpenAI 兼容 chat/completions；
 *   - 【Prompt → JSON】要求模型只回一行严格 JSON：
 *     {"type":"task|item|note|ledger","title":"...","time":"yyyy-MM-dd","amount":数字或null,"note":"..."}
 *     本地只做 JSON 解析与字段清洗，不再猜意图；
 *   - 【兜底】未配置 / 网络失败 / 超时 / 返回非法 → 原文追加为笔记（标签 AI）
 *     并提示「请先在设置中配置 AI 接口以解锁智能分类」，绝不丢用户输入。
 * ============================================================ */

/** 意图种类（与 App 内四大落点一一对应） */
enum class QuickKind(val label: String, val icon: ImageVector, val tint: Color) {
    TASK("任务", Icons.Rounded.TaskAlt, ModuleTints.Tasks),
    ITEM("背包", Icons.Rounded.Backpack, ModuleTints.Backpack),
    MEMO("笔记", Icons.Rounded.EditNote, ModuleTints.Notes),
    LEDGER("记账", Icons.Rounded.BarChart, ModuleTints.Accounting)
}

/** 解析结果：清洗后的标题 / 截止日（yyyy-MM-dd）/ 金额 */
data class QuickIntent(
    val kind: QuickKind,
    val title: String,
    val dueDate: String? = null,
    val amount: Double? = null
)

/**
 * 大模型返回结果的解码器。
 * 职责边界：只做「JSON 提取 + 字段清洗 + 合法性校验」，不做任何关键词/正则意图猜测。
 * 解析不出来就返回 null，由调用方走兜底 —— 不猜、不编、不伪装 AI。
 */
object QuickIntentDecoder {

    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * 系统提示词：把「分类 + 抽取」这件事完整交给大模型，并要求严格 JSON 输出。
     * 带上今天日期与星期，模型才能正确换算「明天 / 下周三」这类相对时间。
     */
    fun systemPrompt(today: String, weekday: String): String =
        "你是「地球Online」这个个人事务 App 的输入解析器。用户会随手说一句话，你要判断它属于哪一类并抽取字段。\n" +
            "今天的日期是 $today（$weekday）。\n" +
            "只输出一行 JSON，不要解释、不要 Markdown 代码块、不要多余文字：\n" +
            "{\"type\":\"task|item|note|ledger\",\"title\":\"\",\"time\":\"\",\"amount\":null,\"note\":\"\"}\n" +
            "type 判定规则：\n" +
            "- task：需要去做的事、有时间点或截止的安排（开会、记得交作业、明天要去体检、三点给妈妈打电话…）；只是「想买/要买/打算买」也属于 task。\n" +
            "- item：已经买到或已经拥有的物品（买了一个耳机、入手相机、到货了机械键盘…）。\n" +
            "- ledger：发生了金额的收入或支出（中午吃饭花了30、充了50话费、工资到账8000…）。\n" +
            "- note：想法、感受、备忘，以及以上都不属于的其他一切。\n" +
            "字段要求：\n" +
            "- title：去掉口语前缀、去掉时间词之后的简短标题，中文尽量不超过 20 字，保留核心名词/动词。\n" +
            "- time：能推断出日期时填 yyyy-MM-dd；推断不出就填空字符串。\n" +
            "- amount：金额（元，纯数字）；ledger 必填，其余填 null。\n" +
            "- note：补充说明，没有就填空字符串。"

    /** 从模型返回的文本里还原结构化意图；失败返回 null */
    fun decode(text: String, fallbackTitle: String): QuickIntent? {
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        val obverse = runCatching {
            Json.parseToJsonElement(trimmed.substring(start, end + 1)) as JsonObject
        }.getOrNull() ?: return null

        val type = (obverse["type"] as? JsonPrimitive)?.content?.trim()?.lowercase(Locale.US)
            ?: return null
        val kind = when (type) {
            "task" -> QuickKind.TASK
            "item" -> QuickKind.ITEM
            "ledger" -> QuickKind.LEDGER
            "note", "memo" -> QuickKind.MEMO
            else -> return null
        }

        val title = (obverse["title"] as? JsonPrimitive)?.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle

        val due = (obverse["time"] as? JsonPrimitive)?.content
            ?.trim()
            ?.takeIf { DATE.matches(it) }

        val amount = (obverse["amount"] as? JsonPrimitive)?.content
            ?.trim()
            ?.toDoubleOrNull()

        return QuickIntent(
            kind = kind,
            title = if (kind == QuickKind.MEMO) fallbackTitle else title,
            dueDate = due.takeIf { kind == QuickKind.TASK },
            amount = amount.takeIf { kind == QuickKind.LEDGER }
        )
    }
}

/** AI 接口就绪状态（抽屉顶部展示用） */
data class AiHubStatus(
    val configured: Boolean = false,
    val model: String = "",
    val host: String = ""
)

/**
 * AI 快捷中枢 ViewModel。
 * 识别链路（v1.0.21 起）：
 *   用户输入 → 读取「设置 → 通用 → AI 对话」配置 → 真实大模型 chat/completions
 *   → 模型返回结构化 JSON → 本地落库（任务 / 背包 / 笔记 / 记账标签笔记）
 * 未配置 Key、网络失败、超时、返回无法解析 → 一律原文追加为笔记并给出可操作提示。
 */
@HiltViewModel
class AiQuickHubViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val itemRepo: ItemRepository,
    private val memoRepo: MemoRepository,
    private val activityRepo: ActivityRepository,
    private val aiRepo: AiRepository,
    private val settings: SettingsDataStore,
    private val json: Json,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** AI 配置是否齐全（三项都填了才算可用） */
    val aiStatus: StateFlow<AiHubStatus> = settings.aiConfig
        .map { raw ->
            val cfg = parseConfig(raw)
            AiHubStatus(
                configured = cfg != null,
                model = cfg?.model.orEmpty(),
                host = cfg?.baseUrl
                    ?.trim()
                    ?.removePrefix("https://")
                    ?.removePrefix("http://")
                    ?.substringBefore("/")
                    .orEmpty()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiHubStatus())

    private fun parseConfig(raw: String): AiConfig? {
        if (raw.isBlank()) return null
        val cfg = runCatching { json.decodeFromString<AiConfig>(raw) }.getOrNull() ?: return null
        return cfg.takeIf {
            it.baseUrl.isNotBlank() && it.apiKey.isNotBlank() && it.model.isNotBlank()
        }
    }

    private fun parseLocal(raw: String): LocalModelConfig =
        runCatching { json.decodeFromString<LocalModelConfig>(raw) }.getOrDefault(LocalModelConfig())

    /** 缺哪一项就提示哪一项，别让用户猜 */
    private fun missingHint(cfg: AiConfig?): String? = when {
        cfg == null -> "请先在「设置 → 通用 → AI 对话」中配置 Base URL 与 API Key"
        cfg.baseUrl.isBlank() -> "缺少 Base URL，请在「设置 → 通用 → AI 对话」中填写"
        cfg.apiKey.isBlank() -> "缺少 API Key，请在「设置 → 通用 → AI 对话」中填写"
        cfg.model.isBlank() -> "缺少模型名，请在「设置 → 通用 → AI 对话」中按服务商文档填写"
        else -> null
    }

    /**
     * 解析 + 落库。结果通过 [onDone] 回传可直接 Toast 的中文文案。
     * 任何异常都不抛出 —— 快捷入口是「顺手」功能，失败也要安静地把内容留住。
     * v1.0.6.5：三态开关落地 —— off 直接原文存笔记；local 走本地引擎（未就绪诚实降级）；
     * cloud 走原有云端 chat/completions。
     */
    fun dispatch(raw0: String, onDone: (String) -> Unit) {
        val raw = raw0.trim()
        if (raw.isBlank() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val rawJson = settings.aiConfig.first()
                val mode = runCatching { json.decodeFromString<AiConfig>(rawJson) }.getOrNull()
                    ?.runMode?.ifBlank { "cloud" } ?: "cloud"
                val message = when (mode) {
                    "off" -> fallbackNote(raw, "AI 已关闭（设置 → 通用 → AI 对话）")
                    "local" -> localIntent(raw)
                    else -> cloudIntent(raw)
                }
                onDone(message)
            } catch (t: Throwable) {
                onDone(fallbackNote(raw, friendlyError(t)))
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * 云端识别（OpenAI 兼容 chat/completions）。
     * @param notice 非空时为「本地模式自动降级云端」的原因，会并入结果与提示文案，
     *               保证用户知道发生了什么（也用于 local 模式不可用时的兜底路径）。
     */
    private suspend fun cloudIntent(raw: String, notice: String? = null): String {
        val rawJson = settings.aiConfig.first()
        val cfg = parseConfig(rawJson)
        val missing = missingHint(cfg)
        val intent = if (missing != null) null else recognize(raw, cfg!!)
        return when {
            missing != null -> fallbackNote(raw, if (notice.isNullOrBlank()) missing else "$notice；$missing")
            intent == null -> fallbackNote(raw, if (notice.isNullOrBlank()) "AI 返回无法解析" else "$notice；AI 返回无法解析")
            else -> {
                val msg = save(intent, raw)
                if (notice.isNullOrBlank()) msg else "$notice\n$msg"
            }
        }
    }

    /**
     * 本地模式意图识别：强约束 JSON Prompt（与云端同一份 systemPrompt）→ 本地引擎 →
     * QuickIntentDecoder 清洗落库。本机不支持 / 引擎未就绪 / 返回无法解析 →
     * 自动降级云端（云端也没配则诚实兜底为笔记），绝不伪装。
     */
    private suspend fun localIntent(raw: String): String {
        val localCfg = parseLocal(settings.localModel.first())
        val dir = java.io.File(
            localCfg.modelDir.ifBlank {
                LocalModelManager.modelDir(appContext, com.example.earthonline.ui.ai.ModelDownloader.MODEL_NAME).absolutePath
            }
        )
        /* 加载/推理前先体检：架构、总内存、可用内存任一不过关都不碰 JNI，直接降级云端。
           native 崩溃（SIGABRT / SIGSEGV / OOM-kill）Java 层拦不住，必须在源头挡住。 */
        val diag = com.example.earthonline.ui.ai.LocalLlmRuntime.diagnose(dir)
        if (diag is com.example.earthonline.ui.ai.LocalLlmSupport.Blocked) {
            return cloudIntent(raw, diag.short)
        }
        val engine = com.example.earthonline.ui.ai.LocalLlmRuntime.engine
        if (!engine.ready) {
            return cloudIntent(raw, "本地模型未加载（请先导入 GGUF 并加载 llama.cpp 引擎）")
        }
        return try {
            val content = engine.chat(
                listOf(
                    ChatMessage("system", QuickIntentDecoder.systemPrompt(todayStr(), weekdayCn())),
                    ChatMessage("user", raw)
                )
            ) { /* 流式增量：快捷中枢一次出结果，无需逐段上屏 */ }
            val intent = content?.let { QuickIntentDecoder.decode(it, raw) }
            if (intent == null) cloudIntent(raw, "本地模型返回无法解析")
            else save(intent, raw)
        } catch (t: Throwable) {
            cloudIntent(raw, "本地推理失败：${friendlyError(t)}")
        }
    }

    /** 唯一识别路径：真实大模型 API。返回 null 表示这条路走不通。 */
    private suspend fun recognize(raw: String, cfg: AiConfig): QuickIntent? {
        val today = todayStr()
        val weekday = weekdayCn()
        // v1.0.6：本地模式映射到 http://127.0.0.1:<port>，复用云端同一套 chat/completions
        val effective = LocalModelManager.resolve(cfg, parseLocal(settings.localModel.first()))
        return try {
            val resp = withTimeout(15_000) {
                aiRepo.chat(
                    effective,
                    listOf(
                        ChatMessage("system", QuickIntentDecoder.systemPrompt(today, weekday)),
                        ChatMessage("user", raw)
                    )
                )
            }
            val serverError = resp.error?.message?.takeIf { it.isNotBlank() }
            if (serverError != null) return null
            val content = resp.choices.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) return null
            QuickIntentDecoder.decode(content, raw)
        } catch (t: Throwable) {
            null
        }
    }

    /** 兜底：原文追加为笔记，保证用户输入一条不丢 */
    private suspend fun fallbackNote(raw: String, reason: String): String =
        withContext(NonCancellable) {
            try {
                memoRepo.insert(
                    MemoEntity(
                        id = uid("memo"),
                        text = raw,
                        markdown = raw,
                        type = "AI",
                        createdAt = nowIso()
                    )
                )
                activityRepo.add(
                    ActivityEntity(
                        id = uid("act"),
                        time = nowIso(),
                        kind = "memo",
                        title = "AI 快捷记录（未分类）：$raw"
                    )
                )
                "未启用智能分类（$reason），已原文存为笔记"
            } catch (t: Throwable) {
                "快捷记录失败：${t.message ?: "未知错误"}"
            }
        }

    /** 异常 → 可操作的中文提示，不把英文堆栈怼给用户 */
    private fun friendlyError(t: Throwable): String = when (t) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "无网络连接"
        is java.net.SocketTimeoutException -> "请求超时"
        is kotlinx.coroutines.TimeoutCancellationException -> "AI 响应超时"
        is HttpException -> when (t.code()) {
            401, 403 -> "API Key 无效或无权限（${t.code()}）"
            404 -> "接口地址或模型名不存在（404）"
            429 -> "请求过于频繁（429）"
            in 500..599 -> "AI 服务端错误（${t.code()}）"
            else -> "AI 接口返回 ${t.code()}"
        }
        is java.io.IOException -> "网络异常"
        else -> t.message?.takeIf { it.isNotBlank() } ?: "未知错误"
    }

    /** 按意图落库 + 写动态流，返回用户可读的结果文案 */
    private suspend fun save(intent: QuickIntent, raw: String): String = when (intent.kind) {
        QuickKind.TASK -> {
            val title = intent.title.take(60)
            // dueDate 仅 todo 类目使用（TaskEntity 约定）：有截止日落「收集箱」，无则日常主线
            taskRepo.insert(
                TaskEntity(
                    id = uid("task"),
                    category = if (intent.dueDate != null) "todo" else "main",
                    title = title,
                    dueDate = intent.dueDate,
                    createdAt = todayStr(),
                    lastModified = todayStr()
                )
            )
            activityRepo.add(
                ActivityEntity(id = uid("act"), time = nowIso(), kind = "task", title = "AI 快捷创建了任务：$title")
            )
            buildString {
                append("已自动添加至任务：$title")
                intent.dueDate?.let { append("（").append(it).append("）") }
            }
        }
        QuickKind.ITEM -> {
            val name = intent.title.take(40)
            itemRepo.insert(
                ItemEntity(id = uid("item"), name = name, type = "physical", createdAt = todayStr())
            )
            activityRepo.add(
                ActivityEntity(id = uid("act"), time = nowIso(), kind = "item", title = "AI 快捷拾取了物品：$name")
            )
            "已自动加入背包：$name"
        }
        QuickKind.MEMO -> {
            memoRepo.insert(
                MemoEntity(
                    id = uid("memo"),
                    text = raw,
                    markdown = raw,
                    type = "AI",
                    createdAt = nowIso()
                )
            )
            activityRepo.add(
                ActivityEntity(id = uid("act"), time = nowIso(), kind = "memo", title = "AI 快捷记录了一条灵感")
            )
            "已自动保存为笔记（标签：AI）"
        }
        QuickKind.LEDGER -> {
            // 应用内无账本（记账由独立 App Verifin 提供）—— 兜底存为带「记账」标签的笔记
            val body = buildString {
                intent.amount?.let { append("¥").append(it).append(" ") }
                append(raw)
            }
            memoRepo.insert(
                MemoEntity(
                    id = uid("memo"),
                    text = body,
                    markdown = body,
                    type = "记账,AI",
                    createdAt = nowIso()
                )
            )
            activityRepo.add(
                ActivityEntity(id = uid("act"), time = nowIso(), kind = "memo", title = "AI 快捷记了一笔账")
            )
            "应用内无账本（记账由 Verifin 提供），已存为笔记（标签：记账）"
        }
    }
}

/**
 * AI 快捷中枢底部抽屉：半透明液态玻璃面板 + 自动聚焦输入框 + 接口状态提示。
 * 识别由用户在「设置 → 通用 → AI 对话」里配置的真实大模型完成；
 * 未配置或请求失败时，原文追加为笔记，不会静默丢失。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AiQuickHubSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfigure: () -> Unit = {},
    vm: AiQuickHubViewModel = hiltViewModel()
) {
    if (!visible) return
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.aiStatus.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    /**
     * 统一收口「关闭抽屉」：先清焦点再显式收起键盘，最后才回调 onDismiss。
     * 顺序不能反 —— 若先隐藏抽屉再清焦点，输入框在动画期间仍持有焦点，
     * 系统会在抽屉消失后重新拉起输入法（就是那个「莫名又弹键盘」的 Bug）。
     */
    fun close() {
        runCatching { focusRequester.freeFocus() }
        runCatching { focusManager.clearFocus() }
        runCatching { keyboard?.hide() }
        onDismiss()
    }

    fun submit() {
        val text = input.trim()
        if (text.isBlank()) {
            toast("先说点什么吧")
            return
        }
        vm.dispatch(text) { msg ->
            toast(msg)
            input = ""
        }
    }

    ModalBottomSheet(
        // v1.0.6 封板：下滑手势 / 返回键 / 点击外部，统一走 close() 收口
        onDismissRequest = { close() },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        // inset 自理（面板自带 navigationBarsPadding + imePadding），防双重垫高
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null
    ) {
        GlassSurface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            showBorder = true,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = 18.dp)
            ) {
                /* —— 标题行：图标芯片 + 标题 + 接口状态 —— */
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconChip(
                        Icons.Rounded.SmartToy,
                        chipColor = ModuleTints.Ai,
                        chipSize = 38.dp,
                        iconSize = 20.dp
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AI 快捷中枢", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "说一句话，AI 自动归档到任务 / 背包 / 笔记",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                /* —— 接口状态条：未配置可直接跳设置 —— */
                val statusTint = if (status.configured) ModuleTints.Ai else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(statusTint.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (status.configured) Icons.Rounded.AutoAwesome else Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (status.configured) "智能识别已启用 · ${status.model}" else "未配置 AI 接口，将原文存为笔记",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusTint,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (!status.configured) {
                        TextButton(onClick = onConfigure) { Text("去配置") }
                    }
                }

                Spacer(Modifier.height(12.dp))

                /* —— 输入框：弹出即聚焦 + 唤起软键盘；语音走输入法麦克风 —— */
                GlassTextField(
                    value = input,
                    onValueChange = { input = it.take(200) },
                    placeholder = "说点什么…（语音请用输入法键盘上的麦克风）",
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    leadingIcon = {
                        Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = ModuleTints.Ai)
                    },
                    trailingIcon = {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(22.dp)
                                    .padding(2.dp),
                                strokeWidth = 2.dp,
                                color = ModuleTints.Ai
                            )
                        } else {
                            IconButton(
                                onClick = { submit() },
                                enabled = input.isNotBlank()
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    minLines = 1,
                    maxLines = 4,
                    singleLine = false
                )

                Spacer(Modifier.height(10.dp))

                /* —— 归档去向提示（识别由大模型完成，本地不再猜测） —— */
                if (input.trim().isNotBlank()) {
                    Text(
                        if (status.configured) "AI 将自动判断并归档到 任务 / 背包 / 笔记 / 记账"
                        else "未配置 AI 接口，提交后将原文存为笔记",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.configured) ModuleTints.Ai else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    "语音输入：弹出键盘后点键盘上的「麦克风」说话，识别文字会直接进入输入框。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Left,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "识别由你在「设置 → 通用 → AI 对话」里配置的大模型完成；未配置或请求失败时，内容会原文存为笔记（标签 AI）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "试试：明天下午三点开会 · 买了一个新耳机 · 今天天气真好 · 中午吃饭花了30",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "记账由独立应用 Verifin 提供，账目类内容会先存为带「记账」标签的笔记",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 死命令：抽屉弹出即让输入框拿焦点并唤起软键盘（等抽屉入场动画到位再拉起，更稳）
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    /**
     * 兜底清理：抽屉真正从组合里移出时（关闭动画结束后）再清一次焦点并收起键盘。
     * 覆盖「动画期间焦点被系统交还给底层输入框」导致的二次唤起。
     */
    DisposableEffect(Unit) {
        onDispose {
            runCatching { focusManager.clearFocus() }
            runCatching { keyboard?.hide() }
        }
    }
}

/** 今天是星期几（中文），供大模型换算相对时间 */
private fun weekdayCn(): String {
    val w = Calendar.getInstance(Locale.CHINA).get(Calendar.DAY_OF_WEEK)
    return when (w) {
        Calendar.SUNDAY -> "星期日"
        Calendar.MONDAY -> "星期一"
        Calendar.TUESDAY -> "星期二"
        Calendar.WEDNESDAY -> "星期三"
        Calendar.THURSDAY -> "星期四"
        Calendar.FRIDAY -> "星期五"
        else -> "星期六"
    }
}
