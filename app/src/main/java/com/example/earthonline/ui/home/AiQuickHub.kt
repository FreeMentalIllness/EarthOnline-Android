package com.example.earthonline.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.ItemEntity
import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.AiRepository
import com.example.earthonline.data.network.ChatMessage
import com.example.earthonline.data.repository.ActivityRepository
import com.example.earthonline.data.repository.ItemRepository
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.ui.components.IconChip
import com.example.earthonline.ui.components.ModuleTints
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.GlassSurface
import com.example.earthonline.ui.theme.OnAmber
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/* ============================================================
 * v1.0.19：主页「AI 快捷中枢」—— 一句话自动归档。
 * 语音/文字输入一句话 → 意图解析（本地规则 + 可选 LLM 增强）→
 * 自动分发到 任务 / 背包 / 笔记 / 记账（兜底）→ Toast 反馈，不打断当前页面。
 * ============================================================ */

/** 意图种类（与 App 内四大落点一一对应） */
enum class QuickKind(val label: String, val icon: ImageVector, val tint: Color) {
    TASK("任务", Icons.Rounded.TaskAlt, ModuleTints.Tasks),
    ITEM("背包", Icons.Rounded.Backpack, ModuleTints.Backpack),
    MEMO("笔记", Icons.Rounded.EditNote, ModuleTints.Notes),
    LEDGER("记账", Icons.Rounded.BarChart, ModuleTints.Accounting)
}

/** 解析结果：清洗后的标题 / 截止日（yyyy-MM-dd）/ 金额 / 解析来源 */
data class QuickIntent(
    val kind: QuickKind,
    val title: String,
    val dueDate: String? = null,
    val timeHint: String? = null,
    val amount: Double? = null,
    val source: String = "rules"
)

/**
 * 本地规则解析器（零网络、零依赖，永远可用；LLM 只是锦上添花）。
 * 判定顺序：记账 → 背包 → 任务 → 笔记（先识别强特征，再落回兜底）。
 */
object QuickCaptureParser {

    /** 相对日词 → 偏移天数 */
    private val DAY_OFFSET = mapOf(
        "大后天" to 3, "后天" to 2, "明早" to 1, "明晚" to 1, "明天" to 1,
        "今早" to 0, "今晚" to 0, "今日" to 0, "今天" to 0
    )

    /** 时间词（用于探测任务意图 + 从标题里剥掉） */
    private val TIME_HINT = Regex(
        "(大后天|后天|明天|今天|今晚|明晚|今早|明早|今日|" +
            "[零一二两三四五六七八九十]+\\s*[点时][半零一二两三四五六七八九十]*分?|" +
            "\\d{1,2}\\s*[点:：]\\s*\\d{0,2}分?|" +
            "(?:下{1,2})?(?:周|星期|礼拜)[一二三四五六日天末]?)"
    )

    /** 时刻（区分上下午）：中文/数字小时 + 可选分钟 */
    private val CLOCK = Regex(
        "(凌晨|早上|清晨|上午|中午|下午|傍晚|晚上|夜里)?\\s*" +
            "([0-9零一二两三四五六七八九十]{1,3})\\s*[点时]\\s*" +
            "(半|[0-9零一二两三四五六七八九十]{1,3})?\\s*分?"
    )

    /** 记账特征 */
    private val LEDGER_HINT = Regex("(花了|支出|消费|付了|缴费|充值|房租|水电|油费|话费|[¥￥]\\s*[0-9])")
    private val LEDGER_AMOUNT =
        Regex("(?:[¥￥]\\s*([0-9]+(?:\\.[0-9]+)?))|(?:(?:花了|支出|消费|付了|缴费)\\s*([0-9]+(?:\\.[0-9]+)?))|(?:([0-9]+(?:\\.[0-9]+)?)\\s*(?:元|块))")

    /** 背包特征（已完成的「买了」） */
    private val ITEM_HINT = Regex("(买了|购入|入手|新买|收了|囤了|添置|到货|开箱)")
    /** 未来购买 / 待办式购买 → 任务 */
    private val TASK_BUY = Regex("(要买|想买|打算买|计划买|去买|代购)")

    /** 任务特征（安排 / 提醒类动词） */
    private val TASK_HINT = Regex(
        "(记得|提醒|别忘|要去|得去|需要去|开会|提交|完成|预约|归还|还钱|打卡|" +
            "取快递|寄件|面试|体检|交作业|还款|开会|上课|值班|出差|接机|接站)"
    )

    private val CN_NUM = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10
    )

    private fun cnToInt(s: String): Int? {
        if (s.isBlank()) return null
        s.toIntOrNull()?.let { return it }
        // 简易中文数字：十/十一/二十三 级别足够（口语时刻不会超过两位数）
        if (s.length == 1) return CN_NUM[s[0]]
        return when {
            s.startsWith("十") -> 10 + (CN_NUM[s[1]] ?: 0)
            s.contains("十") -> {
                val parts = s.split("十")
                val tens = CN_NUM[parts[0].firstOrNull()] ?: return null
                val ones = if (parts[1].isBlank()) 0 else CN_NUM[parts[1].firstOrNull()] ?: return null
                tens * 10 + ones
            }
            else -> null
        }
    }

    /** 解析主入口：一句话 → 意图 */
    fun parse(raw0: String): QuickIntent {
        val raw = raw0.trim()
        if (raw.isBlank()) return QuickIntent(QuickKind.MEMO, "")

        /* —— 1) 记账：有花钱特征就优先按账目处理 —— */
        LEDGER_HINT.find(raw)?.let {
            val m = LEDGER_AMOUNT.find(raw)
            val amount = m?.groupValues?.let { g ->
                g.drop(1).firstOrNull { it.isNotBlank() }?.toDoubleOrNull()
            }
            return QuickIntent(QuickKind.LEDGER, raw, amount = amount)
        }

        /* —— 2) 背包：已经买到的物品 —— */
        if (ITEM_HINT.containsMatchIn(raw) && !TASK_BUY.containsMatchIn(raw)) {
            return QuickIntent(QuickKind.ITEM, cleanItemName(raw))
        }

        /* —— 3) 任务：时间词或安排类动词 —— */
        val hasTime = TIME_HINT.containsMatchIn(raw)
        val hasVerb = TASK_HINT.containsMatchIn(raw) || TASK_BUY.containsMatchIn(raw)
        if (hasTime || hasVerb) {
            val (day, clock) = parseWhen(raw)
            var title = raw
                .replace(TIME_HINT, "")
                .replace(Regex("^我(们|们大家)?"), "")
                .trim(' ', '，', '。', '！', '？', ',', '.', '!', '?')
            if (title.isBlank()) title = raw
            if (!clock.isNullOrBlank()) title = "$title（$clock）"
            return QuickIntent(QuickKind.TASK, title, dueDate = day, timeHint = clock)
        }

        /* —— 4) 兜底：笔记 —— */
        return QuickIntent(QuickKind.MEMO, raw)
    }

    /** 意图预览标签（输入框下方实时提示用） */
    fun previewLabel(intent: QuickIntent): String = when (intent.kind) {
        QuickKind.TASK -> buildString {
            append("将创建任务")
            intent.dueDate?.let { append(" · 截止 ").append(it) }
        }
        QuickKind.ITEM -> "将加入背包"
        QuickKind.LEDGER -> "将记一笔账"
        QuickKind.MEMO -> "将存为笔记"
    }

    /** 物品名清洗：剥掉动词 / 量词 / 时间，只留名字 */
    private fun cleanItemName(raw: String): String {
        var name = raw
            .replace(TIME_HINT, "")
            .replace(ITEM_HINT, "")
            .replace(Regex("^我(们)?"), "")
            .replace(Regex("^[0-9一二两三四五六七八九十]+\\s*[个只台件辆支张把部本轮双盒包罐枚架台套]"), "")
            .trim(' ', '，', '。', '！', '？', ',', '.', '!', '?', '了')
        if (name.isBlank()) name = raw
        return name.take(40)
    }

    /**
     * 解析日期与时刻。返回 (yyyy-MM-dd 或 null, "HH:MM" 或 null)。
     * 相对日：今天/明天/后天/大后天/周X（取未来最近的一个）。
     */
    private fun parseWhen(raw: String): Pair<String?, String?> {
        val cal = Calendar.getInstance()
        var daySet = false

        DAY_OFFSET.forEach { (word, off) ->
            if (!daySet && raw.contains(word)) {
                cal.add(Calendar.DAY_OF_YEAR, off)
                daySet = true
            }
        }
        if (!daySet) {
            Regex("(?:下{1,2})?(?:周|星期|礼拜)([一二三四五六日天])").find(raw)?.let { m ->
                val cn = m.groupValues[1]
                val target = when (cn) {
                    "一" -> Calendar.MONDAY; "二" -> Calendar.TUESDAY; "三" -> Calendar.WEDNESDAY
                    "四" -> Calendar.THURSDAY; "五" -> Calendar.FRIDAY; "六" -> Calendar.SATURDAY
                    else -> Calendar.SUNDAY
                }
                var diff = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (diff == 0) diff = 7
                if (raw.contains("下周") || raw.contains("下星期") || raw.contains("下礼拜")) diff += 7
                cal.add(Calendar.DAY_OF_YEAR, diff)
                daySet = true
            }
        }

        var clock: String? = null
        CLOCK.find(raw)?.let { m ->
            val period = m.groupValues[1]
            val h = cnToInt(m.groupValues[2]) ?: return@let
            if (h !in 0..23) return@let
            var hour = h
            when (period) {
                "下午", "傍晚", "晚上", "夜里" -> if (hour < 12) hour += 12
                "中午" -> if (hour < 11) hour += 12
            }
            val minute = when {
                m.groupValues[3] == "半" -> 30
                m.groupValues[3].isNotBlank() -> cnToInt(m.groupValues[3]) ?: 0
                else -> 0
            }
            clock = String.format(Locale.US, "%02d:%02d", hour, minute)
        }

        val day = if (daySet || clock != null) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        } else null
        return day to clock
    }
}

/**
 * AI 快捷中枢 ViewModel：
 * 本地规则永远兜底；用户配置了 AI（baseUrl/apiKey/model 齐全）时先走 LLM 分类，
 * 失败 / 超时 / 未配置一律静默回落本地规则 —— 快捷入口绝不能因为网络而卡住。
 */
@HiltViewModel
class AiQuickHubViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val itemRepo: ItemRepository,
    private val memoRepo: MemoRepository,
    private val activityRepo: ActivityRepository,
    private val aiRepo: AiRepository,
    private val settings: SettingsDataStore,
    private val json: Json
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** AI 配置是否可用（LLM 增强路径） */
    private suspend fun aiConfigOrNull(): AiConfig? {
        val raw = settings.aiConfig.first()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<AiConfig>(raw) }.getOrNull()
            ?.takeIf { it.baseUrl.isNotBlank() && it.apiKey.isNotBlank() && it.model.isNotBlank() }
    }

    /**
     * 解析 + 落库。结果通过 [onDone] 回传可直接 Toast 的中文文案。
     * 任何异常都不抛出 —— 快捷入口是「顺手」功能，失败也要安静地兜底。
     */
    fun dispatch(raw0: String, onDone: (String) -> Unit) {
        val raw = raw0.trim()
        if (raw.isBlank() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val rules = withContext(Dispatchers.Default) { QuickCaptureParser.parse(raw) }
                val intent = enhanceWithLlm(raw, rules)
                val message = save(intent, raw)
                onDone(message)
            } catch (t: Throwable) {
                // 最后防线：连落库都异常时，至少告诉用户没存上
                onDone("快捷记录失败：${t.message ?: "未知错误"}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** LLM 增强：未配置 / 超时 / 返回不合规格 → 静默用本地规则结果 */
    private suspend fun enhanceWithLlm(raw: String, rules: QuickIntent): QuickIntent {
        val config = aiConfigOrNull() ?: return rules
        return try {
            val sys = "你是个人事务解析器。把用户这句话分类并输出严格 JSON（不要多余文字）：" +
                "{\"kind\":\"task|item|note|ledger\",\"title\":\"清洗后的简短标题\",\"due\":\"yyyy-MM-dd或空\",\"amount\":数字或null}。" +
                "规则：花钱=ledger；已买到的物品=item；有安排/时间/待办=task；其余=note。"
            val resp = withTimeout(12_000) {
                aiRepo.chat(config, listOf(ChatMessage("system", sys), ChatMessage("user", raw)))
            }
            val text = resp.choices.firstOrNull()?.message?.content ?: return rules
            val cleaned = text
                .replace("```json", "")
                .replace("```", "")
                .trim()
            val obj = json.parseToJsonElement(cleaned).let { it as kotlinx.serialization.json.JsonObject }
            val kindStr = (obj["kind"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: return rules
            val kind = when (kindStr.lowercase()) {
                "task" -> QuickKind.TASK
                "item" -> QuickKind.ITEM
                "ledger" -> QuickKind.LEDGER
                else -> QuickKind.MEMO
            }
            val title = ((obj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content)
                ?.trim()?.takeIf { it.isNotBlank() } ?: rules.title
            val due = ((obj["due"] as? kotlinx.serialization.json.JsonPrimitive)?.content)
                ?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) }
            val amount = (obj["amount"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?.toDoubleOrNull() ?: rules.amount
            QuickIntent(
                kind = kind,
                title = if (kind == QuickKind.MEMO) raw else title,
                dueDate = due ?: if (kind == QuickKind.TASK) rules.dueDate else null,
                timeHint = rules.timeHint.takeIf { kind == QuickKind.TASK },
                amount = amount.takeIf { kind == QuickKind.LEDGER },
                source = "llm"
            )
        } catch (t: Throwable) {
            rules
        }
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
 * AI 快捷中枢底部抽屉：半透明液态玻璃面板 + 语音（默认）/键盘双输入 + 实时意图预览。
 * 语音走系统 SpeechRecognizer（RECORD_AUDIO 运行时授权），
 * 不可用 / 未授权 / 失败一律自动降级键盘输入并提示，绝不无声失败。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiQuickHubSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    vm: AiQuickHubViewModel = hiltViewModel()
) {
    if (!visible) return
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()

    // true = 语音输入（默认），false = 键盘输入
    var voiceMode by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var autoStartDone by remember { mutableStateOf(false) }

    val speech = remember { SpeechInputController(context) }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun startVoice() {
        if (!speechAvailable(context)) {
            voiceMode = false
            toast("当前设备无语音识别服务，已切换键盘输入")
            return
        }
        speech.start(
            onFinal = { text ->
                partial = ""
                input = (input + " " + text).trim()
            },
            onError = { msg -> partial = ""; toast(msg) }
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoice()
        } else {
            voiceMode = false
            toast("未授予麦克风权限，已切换为键盘输入")
        }
    }

    fun beginVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startVoice()
        } else {
            runCatching { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }

    // 弹窗打开：默认拉起语音输入（死命令）。无服务 → 键盘；无权限 → 现场申请
    LaunchedEffect(visible) {
        if (visible && !autoStartDone) {
            autoStartDone = true
            if (voiceMode) beginVoice()
        }
        if (!visible) autoStartDone = false
    }

    fun submit() {
        val text = (if (voiceMode) input else input).trim()
        if (text.isBlank()) {
            toast("先说点什么或输入点什么")
            return
        }
        vm.dispatch(text) { msg ->
            toast(msg)
            input = ""
            partial = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (listening) speech.stop()
            onDismiss()
        },
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /* —— 标题行：图标芯片 + 标题 + 输入模式开关 —— */
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
                            "说一句话，自动归档到任务 / 背包 / 笔记",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            if (listening) speech.stop()
                            voiceMode = !voiceMode
                            if (voiceMode) beginVoice()
                        }
                    ) {
                        Icon(
                            if (voiceMode) Icons.Rounded.Keyboard else Icons.Filled.Mic,
                            contentDescription = if (voiceMode) "切换键盘输入" else "切换语音输入"
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (voiceMode) {
                    /* —— 语音输入：大麦克风按钮 + 呼吸光环 + 实时识别文字 —— */
                    val pulse = rememberInfiniteTransition(label = "micPulse")
                    val halo by pulse.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.12f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "haloScale"
                    )
                    val haloAlpha by pulse.animateFloat(
                        initialValue = 0.28f,
                        targetValue = 0.08f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "haloAlpha"
                    )
                    Box(contentAlignment = Alignment.Center) {
                        // 呼吸光环（收音中显示）
                        if (listening) {
                            Box(
                                Modifier
                                    .size(96.dp)
                                    .scale(halo)
                                    .clip(CircleShape)
                                    .background(AmberPrimary.copy(alpha = haloAlpha))
                            )
                        }
                        Box(
                            Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(AmberPrimary, AmberPrimary.copy(alpha = 0.82f)))
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (listening) speech.stop() else beginVoice()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = if (listening) "停止收音" else "开始说话",
                                tint = OnAmber,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            listening -> "正在听你说…（点击结束）"
                            partial.isNotBlank() -> partial
                            input.isNotBlank() -> input
                            else -> "点一下麦克风开始说话"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    /* —— 键盘输入：胶囊输入框 + 发送 —— */
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(200) },
                        placeholder = { Text("例如：明天下午三点开会") },
                        shape = RoundedCornerShape(50),
                        leadingIcon = { Icon(Icons.Rounded.SmartToy, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { submit() }, enabled = input.isNotBlank() && !busy) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                /* —— 实时意图预览：当前文本将被归档到哪 —— */
                val draft = (partial.ifBlank { input }).trim()
                if (draft.isNotBlank()) {
                    val preview = QuickCaptureParser.parse(draft)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(preview.kind.tint.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(preview.kind.icon, contentDescription = null, tint = preview.kind.tint, modifier = Modifier.size(14.dp))
                        Text(
                            QuickCaptureParser.previewLabel(preview) +
                                if (preview.source == "llm") " · AI" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = preview.kind.tint,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    "试试：明天下午三点开会 · 买了一个新耳机 · 今天天气真好 · 中午吃饭花了30",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "记账由独立应用 Verifin 提供，账目类内容会先存为带「记账」标签的笔记",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
