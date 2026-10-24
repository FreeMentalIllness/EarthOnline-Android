package com.example.earthonline.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Place
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.entity.AchievementEntity
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.CollectionEntity
import com.example.earthonline.data.local.entity.ItemEntity
import com.example.earthonline.data.local.entity.LocationEntity
import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.data.local.entity.ProfileEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.data.model.CustomField
import com.example.earthonline.data.repository.AchievementRepository
import com.example.earthonline.data.repository.ActivityRepository
import com.example.earthonline.data.repository.CollectionRepository
import com.example.earthonline.data.repository.ItemRepository
import com.example.earthonline.data.repository.LocationRepository
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.data.repository.ProfileRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.data.repository.XpEventRepository
import com.example.earthonline.data.repository.XpLedger
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.util.Greeting
import com.example.earthonline.util.GrowthStreak
import com.example.earthonline.util.LifeStats
import com.example.earthonline.util.XpRules
import com.example.earthonline.util.lifeStatsOf
import com.example.earthonline.util.localDayOf
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** 人生时间轴条目（任务完成 / 成就解锁 / 足迹标记）
 *  v1.2.0：`day` 精确到日（YYYY-MM-DD，本地时区），此前 `year` 只到年，
 *  同一个月完成的三件事在轴上看不出先后。 */
data class TimelineItem(
    val key: String,
    val day: String,
    val kind: String,   // task / ach / loc / custom
    val title: String,
    val time: String
)

/** 用户在主页「人生时间轴」手动添加的里程碑事件（持久化在 DataStore，不改动 Room 表结构） */
@kotlinx.serialization.Serializable
private data class CustomTimelineEvent(
    val id: String,
    val title: String,
    val day: String,
    val note: String
)

/**
 * v1.2.1 全局搜索命中项（任务 / 物品 / 收藏）。
 *
 * 为什么放在主页而不是单独开一个搜索页：搜索的目的是「少点几下找到东西」，
 * 点进去再回来反而多一次导航。命中项带 route，点一下直接落到对应 Tab。
 */
data class SearchHit(
    val key: String,     // kind + id，列表 key
    val kind: String,    // task / item / collection / memo / achievement / location
    val route: String,   // 点击后跳转的路由
    /** v1.0.6 封板：命中项图标改为矢量（Icons.Rounded），彻底清除搜索结果里的 Emoji */
    val icon: ImageVector,
    val title: String,
    val sub: String
)

/**
 * 主页 UI 状态。
 *
 * 性能要点：整个主页只订阅 **一个** StateFlow。
 * 改造前是 8 个 collectAsState（profile / 4 个计数 / activities / memos / …），
 * 任意一个表变更都会让整棵主页树重组一次；现在 N 次重组收敛为 1 次。
 * 所有派生量（完成率、等级、签名标签、时间轴）都在 ViewModel 里算好，
 * 组合阶段只做渲染，不做解析、排序、JSON 解码。
 */
data class HomeUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val birthDate: String = "",
    val signature: String = "",
    val avatarKey: String = "",
    /** v1.0.0：头像原图文件路径（显示端按控件尺寸采样，见 ui/components/Avatar.kt） */
    val avatarPath: String? = null,
    /** 兼容字段：v1.0.0 之前头像直接存字节 */
    val avatarData: ByteArray? = null,
    val fieldTags: List<String> = emptyList(),
    val hiddenFieldCount: Int = 0,
    val life: LifeStats = LifeStats.Empty,
    val totalTasks: Int = 0,
    val doneTasks: Int = 0,
    val itemCount: Int = 0,
    val itemCategoryCount: Int = 0,
    val collectionCount: Int = 0,
    val unlockedAch: Int = 0,
    val totalAch: Int = 0,
    val totalMemos: Int = 0,
    val noteCount: Int = 0,
    val albumCount: Int = 0,
    val locationCount: Int = 0,
    val recentMemos: List<MemoEntity> = emptyList(),
    val recentActs: List<ActivityEntity> = emptyList(),
    /** 全量动态（类型筛选后，不限条数）——「全部动态」页使用 */
    val allActs: List<ActivityEntity> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
    /** 用户手动添加的里程碑事件（与时间轴自动事件合并展示） */
    val customTimeline: List<TimelineItem> = emptyList(),
    /** 最近动态显示类型筛选（供 UI 回显 FilterChip 选中态） */
    val feedKinds: Set<String> = setOf("task", "ach", "item", "memo"),
    /** 最近动态首页显示条数（0 = 不限制） */
    val feedLimit: Int = 3,
    /** 主页速览显示的入口 id 集合（DataStore 持久化，含数据看板共 9 个为默认） */
    val quickEntries: Set<String> = DEFAULT_QUICK_ENTRIES,
    /** v1.0.6.7：主页卡片堆叠区包含的次要卡片（timeline=时间轴 / life=人生卡片） */
    val stackCards: Set<String> = DEFAULT_STACK_CARDS,
    /** vc64：主页卡片堆叠平滑缩放（0.6~1.6，1.0 = 基准高度） */
    val stackScale: Float = 1f,
    /**
     * v1.0.6.6 主页日历卡：**带到期日的未完成任务**（日程即"有到期日的任务"）。
     * 已在 ViewModel 里过滤好，UI 只管渲染（月历打点 + 当天清单）。
     */
    val calendarTasks: List<TaskEntity> = emptyList(),
    // ————— v1.0.3 情绪价值与成长 —————
    /** 动态问候语（时间段 + 最近心情 + 连续天数） */
    val greeting: String = "",
    /** 当前连续记录天数（今天未记录不断档，宽限到昨天） */
    val streakDays: Int = 0,
    /** 生长阶段 0-4（萌芽/抽枝/繁茂/参天） */
    val streakStage: Int = 0,
    /** 断更 ≥2 天时的回归鼓励语（不惩罚） */
    val comeback: String? = null,
    /** 「历年今日」回忆卡（去年今天留下的日志 / 完成任务 / 足迹） */
    val throwback: List<ThrowbackItem> = emptyList(),
    /** 「今日一签」：每天从过去的记录里确定性抽出一条（同一天刷新结果不变） */
    val dailyDraw: ThrowbackItem? = null,
    /** 用户自定义称号（空 = 默认「旅行者」） */
    val customTitle: String = "",
    /** v1.0.4：待庆祝的新等级（null = 无）；等级=周岁，由 DataStore 已庆祝档位推导 */
    val levelUp: Int? = null
) {
    val doneRatio: Int get() = if (totalTasks > 0) Math.round(doneTasks * 100f / totalTasks) else 0
    /** v1.2.0：成就进度（概览卡第三行，四卡统一三行文案用） */
    val achRatio: Int get() = if (totalAch > 0) Math.round(unlockedAch * 100f / totalAch) else 0
    /** v1.2.0：今日记录条数（概览卡「灵感」第二行） */
    val todayMemoCount: Int
        get() = recentMemos.count { localDayOf(it.createdAt) == todayStr() }
    val displayName: String get() = name.ifBlank { "地球玩家" }
    val latestMemo: String
        get() = recentMemos.firstOrNull()?.text?.take(20)?.ifBlank { "暂无记录" } ?: "暂无记录"
}

/**
 * v1.0.3：回忆类条目（历年今日 / 今日一签共用）。
 */
data class ThrowbackItem(
    val day: String,
    val kind: String,   // memo / task / ach / loc
    val title: String
)

private data class TaskStat(val total: Int, val done: Int)
private data class MemoStat(val total: Int, val recent: List<MemoEntity>)
private data class AchStat(val unlocked: Int, val total: Int, val recent: List<AchievementEntity>)
private data class ItemStat(val total: Int, val categories: Int)
private data class LocStat(val total: Int, val recent: List<LocationEntity>)

/** 主页速览全部入口 id（与 HomeScreen 的 HomeEntry 一一对应；DataStore 空值回落全集）。
 *  v1.0.7：新增「数据」入口（data），默认收进「更多」收纳区，确保数据看板在首页有入口。 */
internal val DEFAULT_QUICK_ENTRIES = setOf("daily", "todo", "items", "collections", "notes", "album", "achievements", "map", "accounting", "ai", "data")

/** v1.0.6.7（入口拆分）：把旧版持久化的速览键迁移到拆分后的新键，防止老用户升级后入口消失。
 *  tasks → daily + todo；backpack → items + collections。 */
internal fun migrateQuickEntries(src: Set<String>): Set<String> {
    val out = src.toMutableSet()
    if ("tasks" in out) { out -= "tasks"; out += "daily"; out += "todo" }
    if ("backpack" in out) { out -= "backpack"; out += "items"; out += "collections" }
    return out
}

/**
 * v1.0.6.8（死命令 #3 堆叠扩容）：主页卡片堆叠区默认包含的卡片。
 * 今日一签（dailydraw）/ 最近动态（acts）/ 日程（calendar）整合进顶部堆叠，
 * 与时间轴（timeline）/ 人生卡片（life）一起左右滑动切换，全部纯展示。
 */
internal val DEFAULT_STACK_CARDS = setOf("dailydraw", "acts", "calendar", "timeline", "life")

/** v1.0.6.8：旧版堆叠键迁移 —— 老用户持久化集只有 timeline/life 时补入三个新堆叠页，
 *  防止升级后首页只剩两页、看不到一签/动态/日程。已知键原样保留。 */
internal fun migrateStackCards(src: Set<String>): Set<String> {
    val known = setOf("dailydraw", "acts", "calendar", "timeline", "life")
    val out = src.filter { it in known }.toMutableSet()
    if (out.isEmpty()) return DEFAULT_STACK_CARDS
    // 旧集（仅 timeline/life 子集）→ 补齐新三页
    if (out.none { it in setOf("dailydraw", "acts", "calendar") }) {
        out += setOf("dailydraw", "acts", "calendar")
    }
    return out
}

private data class Parts(
    val profile: ProfileEntity?,
    val task: TaskStat,
    val memo: MemoStat,
    val ach: AchStat,
    val item: ItemStat,
    val loc: LocStat = LocStat(0, emptyList()),
    val acts: List<ActivityEntity> = emptyList(),
    val doneTaskRows: List<TaskEntity> = emptyList(),
    val collectionCount: Int = 0,
    val customTimeline: List<TimelineItem> = emptyList(),
    val photoCount: Int = 0,
    val feedKinds: Set<String> = setOf("task", "ach", "item", "memo"),
    val feedLimit: Int = 3,
    /** 主页速览显示的入口 id 集合（DataStore 持久化，含数据看板共 9 个为默认） */
    val quickEntries: Set<String> = DEFAULT_QUICK_ENTRIES,
    /** v1.0.6.7：主页卡片堆叠区包含的次要卡片（timeline=时间轴 / life=人生卡片） */
    val stackCards: Set<String> = DEFAULT_STACK_CARDS,
    /** vc64：主页卡片堆叠平滑缩放（0.6~1.6，1.0 = 基准高度） */
    val stackScale: Float = 1f,
    /** v1.0.6.6：主页日历卡数据源 —— 带到期日且未完成的任务 */
    val scheduled: List<TaskEntity> = emptyList(),
    /** v1.0.3：全量数据包（历年今日 / 今日一签 / 连续成长都要全量口径） */
    val bundle: Bundle = Bundle(),
    val customTitle: String = "",
    /** v1.0.4：已庆祝过的等级（-1 = 从未记录，首次记录不庆祝） */
    val lastCelebrated: Int = -1
)

/** v1.0.3：回忆与成长计算所需的四份全量数据（都在现有 observeAll 流上，无新查询） */
private data class Bundle(
    val memos: List<MemoEntity> = emptyList(),
    val doneTasks: List<TaskEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList()
)

/**
 * 首页 ViewModel：把资料 / 计数 / 最近动态 / 日志 / 时间轴聚合成单一状态。
 * 对应 HTML renderHome() 一次读取全局 state 后整体渲染。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val taskRepo: TaskRepository,
    private val memoRepo: MemoRepository,
    private val activityRepo: ActivityRepository,
    private val achievementRepo: AchievementRepository,
    private val itemRepo: ItemRepository,
    private val locationRepo: LocationRepository,
    private val collectionRepo: CollectionRepository,
    private val settingsDs: SettingsDataStore,
    private val json: Json,
    // v1.0.4：XP 对账与流水（主页加载即对账，导出前亦有二次对账兜底）
    private val xpLedger: XpLedger,
    private val xpRepo: XpEventRepository
) : ViewModel() {

    init {
        // v1.0.4：主页是启动首页，这里对账一次 profile.xp（派生口径 + 自定义里程碑累计）
        viewModelScope.launch {
            runCatching { xpLedger.reconcile() }
            // 首录庆祝档位：-1（老用户升级上来）静默锁定当前年龄，
            // 否则档位永远是 -1，之后年龄增长永远不会触发升级庆祝
            runCatching {
                val cur = settingsDs.lastCelebratedLevel.first()
                if (cur < 0) {
                    val age = profileRepo.get()?.birthDate
                        ?.let { lifeStatsOf(it).age } ?: 0
                    settingsDs.setLastCelebratedLevel(age)
                }
            }
        }
    }

    private val profileFlow = profileRepo.observe()
    private val taskFlow = combine(taskRepo.count(), taskRepo.doneCount()) { t, d -> TaskStat(t, d) }
    private val memoFlow = combine(memoRepo.count(), memoRepo.observeRecent(MEMO_LIMIT)) { n, l -> MemoStat(n, l) }
    private val achFlow = combine(
        achievementRepo.unlockedCount(),
        achievementRepo.totalCount(),
        achievementRepo.observeUnlockedRecent(TIMELINE_LIMIT)
    ) { u, t, l -> AchStat(u, t, l) }
    private val itemFlow = combine(itemRepo.count(), itemRepo.categoryCount()) { i, c -> ItemStat(i, c) }
    private val locFlow = combine(locationRepo.count(), locationRepo.observeRecent(TIMELINE_LIMIT)) { n, l -> LocStat(n, l) }
    // 全量动态（表内环形裁剪最多 50 条），首页 take(feedLimit)，全部动态页完整展示
    private val actFlow = activityRepo.observeRecent(200)
    private val doneTaskFlow = taskRepo.observeDoneRecent(TIMELINE_LIMIT)
    /**
     * v1.0.6.6 主页日历卡：带到期日且**未完成**的任务（日程口径）。
     * 复用现有 observeAll 流做内存过滤，不新增 Room 查询、不动表结构。
     */
    private val scheduledFlow: Flow<List<TaskEntity>> = taskRepo.observeAll().map { list ->
        list.filter { !it.dueDate.isNullOrBlank() && it.status != "done" }
    }
    // v1.2.0：概览「背包」卡第三行要显示收藏数
    private val collectionFlow = collectionRepo.count()
    // v1.0.6：概览「相册」卡显示照片总数（DataStore JSON 内 path 数量）
    private val photoFlow = settingsDs.memoryPhotosJson.map { txt ->
        if (txt.isBlank()) 0 else Regex("\"path\"").findAll(txt).count()
    }

    /** 用户在时间轴手动添加的里程碑（DataStore 持久化） */
    private val customFlow: Flow<List<TimelineItem>> = settingsDs.customTimelineJson.map { txt ->
        if (txt.isBlank()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(CustomTimelineEvent.serializer()), txt)
                .map { e -> TimelineItem("custom-${e.id}", e.day, "custom", e.title, e.day + "T00:00:00.000Z") }
        }.getOrDefault(emptyList())
    }

    /** 最近动态显示类型筛选（task/ach/item/memo），解码为空集时回落全类型，防止「全部隐藏」死局 */
    private val feedFilterFlow: Flow<Set<String>> = settingsDs.homeFeedFilterJson.map { txt ->
        if (txt.isBlank()) DEFAULT_FEED_KINDS
        else runCatching { json.decodeFromString(SetSerializer(String.serializer()), txt) }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_FEED_KINDS
    }

    /** 最近动态首页显示条数（0 = 不限制；旧版本无该键默认 3，与原 ACT_LIMIT 一致） */
    private val feedLimitFlow: Flow<Int> = settingsDs.homeFeedLimit

    /** 主页速览自定义入口（DataStore 持久化；解码为空集时回落全入口，防止「全部隐藏」死局） */
    private val quickEntriesFlow: Flow<Set<String>> = settingsDs.homeQuickEntriesJson.map { txt ->
        if (txt.isBlank()) DEFAULT_QUICK_ENTRIES
        else runCatching { json.decodeFromString(SetSerializer(String.serializer()), txt) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
            ?.let { migrateQuickEntries(it) }
            ?: DEFAULT_QUICK_ENTRIES
    }

    /** v1.0.6.7：主页卡片堆叠区包含的次要卡片（timeline=时间轴 / life=人生卡片） */
    private val stackCardsFlow: Flow<Set<String>> = settingsDs.homeStackCardsJson.map { txt ->
        if (txt.isBlank()) DEFAULT_STACK_CARDS
        else runCatching { json.decodeFromString(SetSerializer(String.serializer()), txt) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
            ?.let { migrateStackCards(it) }
            ?: DEFAULT_STACK_CARDS
    }

    /** vc64：主页卡片堆叠平滑缩放（取代 vc63 三档预设） */
    private val stackScaleFlow: Flow<Float> = settingsDs.homeStackScale

    /** v1.0.3：自定义称号 */
    private val customTitleFlow: Flow<String> = settingsDs.customTitle

    /** v1.0.4：已庆祝过的等级（-1 = 从未记录） */
    private val lastCelebratedFlow: Flow<Int> = settingsDs.lastCelebratedLevel

    /** v1.0.3：全量数据包（回忆 / 成长计算口径） */
    private val bundleFlow: Flow<Bundle> = combine(
        memoRepo.observeAll(),
        taskRepo.observeAll(),
        locationRepo.observeAll(),
        achievementRepo.observeAll()
    ) { memos, tasks, locs, achs ->
        Bundle(memos, tasks.filter { it.status == "done" }, locs, achs.filter { it.unlocked })
    }

    val state: StateFlow<HomeUiState> =
        combine(profileFlow, taskFlow, memoFlow, achFlow, itemFlow) { p, t, m, a, i ->
            Parts(p, t, m, a, i)
        }
            .combine(locFlow) { parts, loc -> parts.copy(loc = loc) }
            .combine(actFlow) { parts, acts -> parts.copy(acts = acts) }
            .combine(doneTaskFlow) { parts, rows -> parts.copy(doneTaskRows = rows) }
            .combine(collectionFlow) { parts, c -> parts.copy(collectionCount = c) }
            .combine(photoFlow) { parts, pc -> parts.copy(photoCount = pc) }
            .combine(customFlow) { parts, custom -> parts.copy(customTimeline = custom) }
            .combine(feedFilterFlow) { parts, kinds -> parts.copy(feedKinds = kinds) }
            .combine(feedLimitFlow) { parts, limit -> parts.copy(feedLimit = limit) }
            .combine(quickEntriesFlow) { parts, quick -> parts.copy(quickEntries = quick) }
            .combine(stackCardsFlow) { parts, stack -> parts.copy(stackCards = stack) }
            .combine(stackScaleFlow) { parts, scale -> parts.copy(stackScale = scale) }
            .combine(scheduledFlow) { parts, sched -> parts.copy(scheduled = sched) }
            .combine(bundleFlow) { parts, bundle -> parts.copy(bundle = bundle) }
            .combine(customTitleFlow) { parts, title -> parts.copy(customTitle = title) }
            .combine(lastCelebratedFlow) { parts, lv -> parts.copy(lastCelebrated = lv) }
            .map { it.toUi() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /* ---------------- 主页自定义：时间轴里程碑 / 动态流筛选 ---------------- */

    /** 从 DataStore 读取原始自定义里程碑列表（解码失败回落空列表，绝不崩） */
    private suspend fun loadCustomEvents(): List<CustomTimelineEvent> {
        val txt = settingsDs.customTimelineJson.first()
        if (txt.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CustomTimelineEvent.serializer()), txt)
        }.getOrDefault(emptyList())
    }

    /** 添加一条用户自定义里程碑到时间轴（DataStore 持久化，不改动 Room 表结构） */
    fun addCustomTimelineEvent(title: String, day: String, note: String) {
        val t = title.trim()
        if (t.isBlank() || day.isBlank()) return
        viewModelScope.launch {
            val list = loadCustomEvents() + CustomTimelineEvent(
                id = uid("tl"),
                title = t.take(40),
                day = day,
                note = note.take(200)
            )
            settingsDs.setCustomTimelineJson(
                json.encodeToString(ListSerializer(CustomTimelineEvent.serializer()), list)
            )
            // v1.0.4：自定义里程碑记 XP 流水（kind=custom），并立即对账刷新 profile.xp
            xpRepo.record("custom", XpRules.CUSTOM_MILESTONE, "自定义里程碑：${t.take(40)}")
            runCatching { xpLedger.reconcile() }
        }
    }

    /** 删除一条自定义里程碑（[id] 为 UI 传入的 key：`custom-xxx`，需剥掉前缀） */
    fun deleteCustomTimelineEvent(id: String) {
        viewModelScope.launch {
            val rawId = id.removePrefix("custom-")
            val list = loadCustomEvents().filter { it.id != rawId }
            settingsDs.setCustomTimelineJson(
                json.encodeToString(ListSerializer(CustomTimelineEvent.serializer()), list)
            )
        }
    }

    /** 设置最近动态的显示类型（task/ach/item/memo） */
    fun setFeedKinds(kinds: Set<String>) {
        viewModelScope.launch {
            settingsDs.setHomeFeedFilterJson(
                json.encodeToString(SetSerializer(String.serializer()), kinds)
            )
        }
    }

    /** 设置最近动态首页显示条数（0 = 不限制） */
    fun setFeedLimit(n: Int) {
        viewModelScope.launch { settingsDs.setHomeFeedLimit(n) }
    }

    /** 设置主页速览显示的入口集合（至少保留一个，空集请求直接忽略） */
    fun setQuickEntries(entries: Set<String>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            settingsDs.setHomeQuickEntriesJson(
                json.encodeToString(SetSerializer(String.serializer()), entries)
            )
        }
    }

    /** v1.0.6.7：设置主页卡片堆叠区包含的次要卡片（至少保留一个，空集忽略） */
    /** vc64：主页卡片堆叠平滑缩放（0.6~1.6，滑块可停任意值，DataStore 持久化） */
    fun setStackScale(scale: Float) {
        viewModelScope.launch { settingsDs.setHomeStackScale(scale) }
    }

    fun setStackCards(cards: Set<String>) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            settingsDs.setHomeStackCardsJson(
                json.encodeToString(SetSerializer(String.serializer()), cards)
            )
        }
    }

    /** 用户在「全部动态」页手动添加一条自定义动态（kind=custom，随动态流展示） */
    fun addCustomActivity(title: String) {
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            activityRepo.add(
                ActivityEntity(id = uid("act"), time = nowIso(), kind = "custom", title = t.take(60))
            )
        }
    }

    /** 删除一条动态（主要供自定义动态删除） */
    fun deleteActivity(id: String) {
        viewModelScope.launch { activityRepo.delete(id) }
    }

    /* ---------------- v1.2.1：主页顶部全局搜索 ---------------- */

    private val queryFlow = MutableStateFlow("")
    val query: StateFlow<String> = queryFlow

    fun setQuery(q: String) { queryFlow.value = q }
    fun clearQuery() { queryFlow.value = "" }

    /**
     * 搜索结果流。
     * - debounce 200ms：逐字输入时每敲一个字就查一次库，在低端机上会掉帧；
     * - flatMapLatest：新词来了就取消上一次查询，避免旧结果后到覆盖新结果（经典竞态）；
     * - 空词直接发空列表，不查库。
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<SearchHit>> = queryFlow
        .debounce(200)
        .map { it.trim() }
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(
                    taskRepo.search(q),
                    itemRepo.search(q),
                    collectionRepo.search(q),
                    memoRepo.search(q),
                    achievementRepo.search(q)
                ) { tasks, items, cols, memos, achs ->
                    // 足迹搜索是日键表，单独流再合并
                    locationRepo.search(q).first().let { locs ->
                        buildHits(tasks, items, cols, memos, achs, locs)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildHits(
        tasks: List<TaskEntity>,
        items: List<ItemEntity>,
        cols: List<CollectionEntity>,
        memos: List<MemoEntity> = emptyList(),
        achs: List<AchievementEntity> = emptyList(),
        locs: List<LocationEntity> = emptyList()
    ): List<SearchHit> {
        val out = ArrayList<SearchHit>(tasks.size + items.size + cols.size + memos.size + achs.size + locs.size)
        tasks.take(8).forEach { t ->
            out += SearchHit(
                key = "task-${t.id}", kind = "task", route = "tasks", icon = Icons.Rounded.Checklist,
                title = t.title.ifBlank { "(未命名任务)" },
                sub = listOfNotNull(
                    STATUS_LABEL[t.status],
                    t.dueDate?.takeIf { it.isNotBlank() }?.let { "到期 $it" }
                ).joinToString(" · ")
            )
        }
        items.take(8).forEach { i ->
            out += SearchHit(
                key = "item-${i.id}", kind = "item", route = "backpack", icon = Icons.Rounded.Backpack,
                title = i.name.ifBlank { "(未命名物品)" },
                sub = i.category?.takeIf { it.isNotBlank() } ?: "未分类"
            )
        }
        cols.take(8).forEach { c ->
            out += SearchHit(
                key = "col-${c.id}", kind = "collection", route = "collections", icon = Icons.Rounded.Favorite,
                title = c.title.ifBlank { "(未命名收藏)" },
                sub = c.note?.takeIf { it.isNotBlank() } ?: (c.category?.takeIf { it.isNotBlank() } ?: "未分类")
            )
        }
        // v1.0.5：搜索范围扩展到世界日志 / 成就 / 足迹
        memos.take(6).forEach { m ->
            out += SearchHit(
                key = "memo-${m.id}", kind = "memo", route = "home", icon = Icons.Rounded.Lightbulb,
                title = m.text.take(40).ifBlank { "(空白灵感)" },
                sub = when (m.type) { "idea" -> "灵感"; "important" -> "重要"; else -> "日志" }
            )
        }
        achs.take(6).forEach { a ->
            out += SearchHit(
                key = "ach-${a.id}", kind = "achievement", route = "achievements", icon = Icons.Rounded.EmojiEvents,
                title = a.title.ifBlank { "(未命名成就)" },
                sub = if (a.unlocked) "已解锁" else "未解锁 · ${a.desc.take(20)}"
            )
        }
        locs.take(6).forEach { l ->
            out += SearchHit(
                key = "loc-${l.id}", kind = "location", route = "map", icon = Icons.Rounded.Place,
                title = l.name.ifBlank { "(未命名足迹)" },
                sub = l.date.takeIf { it.isNotBlank() }?.let { "到访 $it" } ?: "足迹"
            )
        }
        return out
    }

    /** 灵感闪念：回车即记录（对应 HTML 侧栏 quick-memo） */
    fun addQuickMemo(text: String, type: String = "note") {
        val t = text.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            memoRepo.insert(MemoEntity(id = uid("memo"), text = t, type = type, createdAt = nowIso()))
            activityRepo.add(
                ActivityEntity(id = uid("act"), time = nowIso(), kind = "memo", title = "记录了一条灵感")
            )
        }
    }

    fun deleteMemo(id: String) {
        viewModelScope.launch { memoRepo.delete(id) }
    }

    private fun Parts.toUi(): HomeUiState {
        val fields = parseFields(profile?.customFieldsJson)
        val shown = fields.take(3)
        // 连续成长：日键集合一次算好，当前连续与回归语共用
        val recordDays = recordDayKeys(bundle)
        val streakDays = GrowthStreak.currentStreak(recordDays)
        val life = lifeStatsOf(profile?.birthDate)
        // v1.0.4：升级庆祝判定 —— 等级（周岁）高于已庆祝档位时弹一次；
        // lastCelebrated = -1（首次记录）静默落档，避免升级后首启误弹
        val levelUp = if (life.hasBirth && lastCelebrated >= 0 && life.age > lastCelebrated) {
            life.age
        } else null
        return HomeUiState(
            loaded = true,
            name = profile?.name.orEmpty(),
            birthDate = profile?.birthDate.orEmpty(),
            signature = profile?.signature.orEmpty(),
            avatarKey = profile?.avatarKey.orEmpty(),
            avatarPath = profile?.avatarPath,
            avatarData = profile?.avatarData,
            fieldTags = shown.map { f ->
                val v = f.value
                "${f.label}：${if (v.length > 20) v.take(20) + "…" else v}"
            },
            hiddenFieldCount = (fields.size - shown.size).coerceAtLeast(0),
            life = life,
            totalTasks = task.total,
            doneTasks = task.done,
            itemCount = item.total,
            itemCategoryCount = item.categories,
            collectionCount = collectionCount,
            unlockedAch = ach.unlocked,
            totalAch = ach.total,
            totalMemos = memo.total,
            noteCount = memo.total,
            albumCount = photoCount,
            locationCount = loc.total,
            recentMemos = memo.recent,
            // allActs = 全量原始动态（不按类型过滤）—— ActsCard 的分类标签页需要覆盖筛选
            allActs = acts,
            // 自定义动态（kind=custom）始终展示，不受旧版筛选集合（可能不含 custom）影响
            recentActs = acts.filter { it.kind in feedKinds || it.kind == "custom" }
                .let { list -> if (feedLimit > 0) list.take(feedLimit) else list },
            // 自动事件 + 用户自定义里程碑合并按时间倒序；空集 = 显示全部类型
            timeline = (buildTimeline() + customTimeline)
                .sortedWith(compareByDescending { it.time })
                .take(TIMELINE_SHOW),
            customTimeline = customTimeline,
            feedKinds = feedKinds,
            feedLimit = feedLimit,
            quickEntries = quickEntries,
            stackCards = stackCards,
            stackScale = stackScale,
            // v1.0.6.6：主页轻量日历卡的数据源（带到期日的未完成任务）
            calendarTasks = scheduled,
            // ————— v1.0.3 情绪价值与成长 —————
            greeting = Greeting.build(
                Greeting.now(),
                bundle.memos.firstOrNull { it.type == "mood" }?.text,
                streakDays
            ),
            streakDays = streakDays,
            streakStage = GrowthStreak.stage(streakDays),
            comeback = GrowthStreak.comebackMessage(recordDays),
            throwback = buildThrowback(bundle),
            dailyDraw = buildDailyDraw(bundle),
            customTitle = customTitle,
            levelUp = levelUp
        )
    }

    /** 记录日键集合：日志日 ∪ 完成任务日 ∪ 足迹日（任意一种记录都算「记录」） */
    private fun recordDayKeys(b: Bundle): Set<String> = buildSet {
        b.memos.forEach { localDayOf(it.createdAt)?.let(::add) }
        b.doneTasks.forEach { it.doneAt?.let { iso -> localDayOf(iso)?.let(::add) } }
        b.locations.forEach { add(it.date.take(10)) }
    }

    /** 历年今日：去年今天留下的日志 / 完成任务 / 足迹（最多 6 条） */
    private fun buildThrowback(b: Bundle): List<ThrowbackItem> {
        val lastYear = lastYearToday() ?: return emptyList()
        val out = ArrayList<ThrowbackItem>(6)
        b.memos.forEach { m ->
            val d = localDayOf(m.createdAt) ?: return@forEach
            if (d == lastYear && m.text.isNotBlank()) out += ThrowbackItem(d, "memo", m.text.trim())
        }
        b.doneTasks.forEach { t ->
            val d = t.doneAt?.let { localDayOf(it) } ?: return@forEach
            if (d == lastYear) out += ThrowbackItem(d, "task", t.title.ifBlank { "任务" })
        }
        b.locations.forEach { l ->
            if (l.date.take(10) == lastYear) out += ThrowbackItem(l.date.take(10), "loc", l.name.ifBlank { "足迹" })
        }
        return out.take(6)
    }

    /** 「去年今天」的日键（YYYY-MM-DD）；解析失败返回 null */
    private fun lastYearToday(): String? {
        val today = todayStr()
        val year = today.take(4).toIntOrNull() ?: return null
        return (year - 1).toString() + today.drop(4)
    }

    /**
     * 今日一签：从过去的日志与解锁成就里确定性抽出一条。
     * 用「今天距 2000-01-01 的天数」当随机种子 —— 同一天任何人刷新结果都一致，
     * 明天自动换一张，不引入任何随机数状态。
     */
    private fun buildDailyDraw(b: Bundle): ThrowbackItem? {
        val pool = ArrayList<ThrowbackItem>(32)
        b.memos.forEach { m ->
            val d = localDayOf(m.createdAt) ?: return@forEach
            if (m.text.isNotBlank()) pool += ThrowbackItem(d, "memo", m.text.trim())
        }
        b.achievements.forEach { a ->
            val d = a.unlockedAt?.let { localDayOf(it) } ?: return@forEach
            pool += ThrowbackItem(d, "ach", "解锁成就「${a.title.ifBlank { "成就" }}」")
        }
        if (pool.isEmpty()) return null
        val seed = runCatching {
            val t = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(todayStr())
            ((t?.time ?: 0L) / (24 * 3600_000L)).toInt()
        }.getOrDefault(0)
        return pool[Math.floorMod(seed, pool.size)]
    }

    /* ————— 回忆彩蛋计数 ————— */

    /** 主页翻开「历年今日」卡时计数（彩蛋「时光回声」判定源） */
    fun onThrowbackSeen() {
        viewModelScope.launch { settingsDs.bumpEggThrowback() }
    }

    /**
     * v1.0.4：升级庆祝播完回调 —— 把当前等级落为已庆祝档位。
     * 若传入等级已落后于实时等级（庆祝期间又跨级），取 max 防止连环弹。
     */
    fun markLevelCelebrated(level: Int) {
        viewModelScope.launch {
            val cur = settingsDs.lastCelebratedLevel.first()
            settingsDs.setLastCelebratedLevel(maxOf(cur, level))
        }
    }

    private fun parseFields(jsonText: String?): List<CustomField> {
        if (jsonText.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CustomField.serializer()), jsonText)
        }.getOrDefault(emptyList()).filter { it.label.isNotBlank() && it.value.isNotBlank() }
    }

    /**
     * 人生时间轴 = 仅记录「里程碑意义」的重大事件，绝对不放鸡毛蒜皮的流水：
     *  - 成就解锁（解锁成就本身就是里程碑）；
     *  - 特殊里程碑动态：相册首次导入 / 彩蛋触发等（业务层写入 kind ∈ MILESTONE_KINDS）；
     *  - 用户在时间轴手动添加的自定义里程碑（在 toUi 处与自动事件合并）。
     * v1.0.6：已剔除「常规完成任务」与「日常足迹打卡」—— 这两类属于日常流水，
     *         归入主页「最近动态」，不再污染人生时间轴。
     */
    private fun Parts.buildTimeline(): List<TimelineItem> {
        val out = ArrayList<TimelineItem>(ach.recent.size + 4)
        ach.recent.forEach { a ->
            val time = a.unlockedAt ?: return@forEach
            out += TimelineItem(a.id, localDayOf(time) ?: time.take(10), "ach", a.title.ifBlank { "成就" }, time)
        }
        // 特殊里程碑动态（相册首导 / 彩蛋等），由业务层在对应节点写入这些 kind
        acts.filter { it.kind in MILESTONE_KINDS }.forEach { act ->
            out += TimelineItem(
                act.id,
                localDayOf(act.time) ?: act.time.take(10),
                act.kind,
                act.title,
                act.time
            )
        }
        out.sortWith(compareByDescending { it.time })
        return if (out.size > TIMELINE_SHOW) out.subList(0, TIMELINE_SHOW) else out
    }

    private companion object {
        val STATUS_LABEL = mapOf(
            "planning" to "计划中",
            "active" to "进行中",
            "paused" to "已暂停",
            "done" to "已完成"
        )
        /** 最近动态可筛选的自动类型（custom 自定义动态恒显示，不参与勾选） */
        val DEFAULT_FEED_KINDS = setOf("task", "ach", "item", "memo")
        /**
         * 人生时间轴「里程碑」动态 kind：相册首次导入 / 彩蛋触发等。
         * 仅这些特殊事件会进入时间轴，日常流水（task/item/memo）一律不进。
         */
        val MILESTONE_KINDS = setOf("milestone", "album", "egg")
        const val MEMO_LIMIT = 5
        const val ACT_LIMIT = 3
        const val TIMELINE_LIMIT = 16
        const val TIMELINE_SHOW = 16
    }
}
