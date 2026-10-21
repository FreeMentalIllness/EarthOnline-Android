package com.example.earthonline.ui.achievements

import androidx.compose.ui.graphics.vector.ImageVector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.local.entity.AchievementEntity
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.*
import com.example.earthonline.data.repository.*
import com.example.earthonline.util.XpRules
import com.example.earthonline.util.lifeStatsOf
import com.example.earthonline.util.localDayOf
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** combine 的中间载体（成就行 + 统计 + 事件时间源） */
private data class EngineInput(
    val ach: List<AchievementEntity>,
    val stats: AchStats,
    val sources: EventSources
)

/**
 * 成就引擎 ViewModel。
 * 对应 HTML achievements 模块：手动成就 + 自动成就（按规则解锁并写入世界日志动态）。
 * 引擎在 combine 流中反应式运行：
 *  - 缺失的自动规则先建行（锁定，带分类）
 *  - v2 旧行没有 category，这里回填
 *  - 条件满足即解锁并记一条动态
 */
@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val repo: AchievementRepository,
    private val taskRepo: TaskRepository,
    private val itemRepo: ItemRepository,
    private val collectionRepo: CollectionRepository,
    private val memoRepo: MemoRepository,
    private val locationRepo: LocationRepository,
    private val activityRepo: ActivityRepository,
    private val profileRepo: ProfileRepository,
    private val settings: SettingsDataStore,
    // v1.0.4：XP 流水（解锁成就落一条 ach 流水）
    private val xpRepo: XpEventRepository,
    // v1.0.5：成就解锁本地通知（跟随「到期提醒通知」总开关）
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    /** 成就行（引擎写入后由 Room 推回来） */
    val achievements: StateFlow<List<AchievementEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 实时计数快照（供 UI 画进度条） */
    private val _stats = MutableStateFlow(AchStats())
    val stats: StateFlow<AchStats> = _stats.asStateFlow()

    data class AchievementUi(
        val entity: AchievementEntity,
        val categoryId: String,
        val isAuto: Boolean,
        val current: Int,
        val goal: Int
    )

    data class CategorySummary(
        val id: String,
        val label: String,
        val icon: ImageVector,
        val unlocked: Int,
        val total: Int
    )

    val uiState: StateFlow<List<AchievementUi>> =
        combine(achievements, stats) { list, s -> list.map { it.toUi(s) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 分类概览（用于筛选 chip 上的计数） */
    val categorySummary: StateFlow<List<CategorySummary>> =
        uiState.map { ui ->
            ACH_CATEGORIES.map { c ->
                val sub = ui.filter { it.categoryId == c.id }
                CategorySummary(c.id, c.label, c.icon, sub.count { it.entity.unlocked }, sub.size)
            }.filter { it.total > 0 }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unlockedCount: StateFlow<Int> =
        achievements.map { it.count { a -> a.unlocked } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 会话内已解锁的自动 key，防止并发重复写动态 */
    private val processedAuto = mutableSetOf<String>()

    /**
     * v1.2.1：本次会话「刚刚解锁」的成就事件流，供全局右下角弹卡片 + 音效。
     * - 用 SharedFlow 而非 StateFlow：解锁是一次性事件，不该被「重新订阅时重放」；
     * - extraBufferCapacity=4：连续解锁（比如导入备份后一次解 6 个）时不会丢事件。
     */
    private val _unlockEvents = MutableSharedFlow<AchievementEntity>(extraBufferCapacity = 4)
    val unlockEvents: SharedFlow<AchievementEntity> = _unlockEvents.asSharedFlow()

    /**
     * 成就表结构是否还在变动（刚建行 / 刚回填分类）。
     * 为 true 的那一轮发生的解锁一律静默 —— 那是历史补账，不是用户刚干成的事。
     * 初值 true：首次启动必然要先建行。
     */
    private var structureDirty = true

    private val ruleByKey = AUTO_RULES.associateBy { it.key }

    init {
        viewModelScope.launch {
            val flows: List<Flow<Any?>> = listOf(
                repo.observeAll(),
                taskRepo.observeAll(),
                itemRepo.observeAll(),
                collectionRepo.observeAll(),
                memoRepo.observeAll(),
                locationRepo.observeAll(),
                profileRepo.observe(),
                // v1.2.0：彩蛋计数器（DataStore 的 int 流， belong 与其它流同源同节奏）
                settings.eggBlankTitle,
                // v1.0.3：新彩蛋数据源（历年今日查看次数 / 记忆相册照片数）
                settings.eggThrowback,
                settings.memoryPhotosJson,
                // v1.0.6：新功能成就数据源（AI 中枢归档次数 / 卡片·壁纸不透明度是否被调过）
                activityRepo.observeRecent(),
                settings.cardOpacity,
                settings.wallpaperAlpha
            )
            combine(flows) { arr ->
                @Suppress("UNCHECKED_CAST")
                val achList = arr[0] as List<AchievementEntity>
                @Suppress("UNCHECKED_CAST") val tasks = arr[1] as List<TaskEntity>
                @Suppress("UNCHECKED_CAST") val items = arr[2] as List<ItemEntity>
                @Suppress("UNCHECKED_CAST") val collections = arr[3] as List<CollectionEntity>
                @Suppress("UNCHECKED_CAST") val memos = arr[4] as List<MemoEntity>
                @Suppress("UNCHECKED_CAST") val locations = arr[5] as List<LocationEntity>
                @Suppress("UNCHECKED_CAST") val profile = arr[6] as ProfileEntity?
                val blankTitleTries = arr[7] as? Int ?: 0
                val throwbackSeen = arr[8] as? Int ?: 0
                val photosJson = arr[9] as? String ?: ""
                @Suppress("UNCHECKED_CAST")
                val activities = arr[10] as? List<ActivityEntity> ?: emptyList()
                val cardOpacity = arr[11] as? Float ?: 0.74f
                val wallpaperAlpha = arr[12] as? Float ?: 0.35f

                val life = lifeStatsOf(profile?.birthDate)
                val stats = AchStats(
                    tasksDone = tasks.count { it.status == "done" },
                    items = items.size,
                    collections = collections.size,
                    memos = memos.size,
                    locations = locations.size,
                    daysLived = life.daysLived,
                    age = life.age,
                    // —— v1.2.0 彩蛋字段 ——
                    gender = profile?.gender.orEmpty(),
                    am3Memos = countInHourRange(memos.map { it.createdAt }, 3, 4),
                    nightOwlMemos = countInHourRange(memos.map { it.createdAt }, 0, 5),
                    earlyBirdMemos = countInHourRange(memos.map { it.createdAt }, 5, 7),
                    am3TasksDone = countInHourRange(tasks.mapNotNull { it.doneAt }, 3, 4),
                    blankTitleTries = blankTitleTries,
                    longTitleTasks = tasks.count { it.title.length >= 30 },
                    emojiTitleTasks = tasks.count { countEmoji(it.title) >= 5 },
                    periodTitleTasks = tasks.count { it.title.trim().endsWith("。") },
                    testTitleTasks = tasks.count {
                        it.title.contains("测试") || it.title.contains("test", ignoreCase = true)
                    },
                    overdueOpenTodos = tasks.count {
                        it.status != "done" && it.category == "todo" &&
                            !it.dueDate.isNullOrBlank() && it.dueDate < todayStr()
                    },
                    maxDoneInDay = tasks.mapNotNull { it.doneAt }
                        .groupingBy { localDayOf(it) ?: it.take(10) }
                        .eachCount()
                        .values.maxOrNull() ?: 0,
                    recordStreak = longestStreak(memos.map { localDayOf(it.createdAt) ?: it.createdAt.take(10) }),
                    emojiOnlyMemos = memos.count { isEmojiOnlyText(it.text) },
                    newYearBirth = if (profile?.birthDate?.endsWith("-01-01") == true) 1 else 0,
                    // —— v1.0.3 新增彩蛋字段 ——
                    throwbackSeen = throwbackSeen,
                    memoryPhotos = parsePhotoCount(photosJson),
                    // —— v1.0.6 新功能成就字段 ——
                    aiHubCount = activities.count { it.title.startsWith("AI 快捷") },
                    longMemos = memos.count { it.text.length >= 300 },
                    // 卡片默认 0.74 / 壁纸默认 0.35：任一被改动即视为「调校过手感」
                    appearanceTuned = if (cardOpacity != 0.74f || wallpaperAlpha != 0.35f) 1 else 0
                )
                val sources = EventSources(
                    doneAtList = tasks.mapNotNull { it.doneAt }.sorted(),
                    itemCreatedList = items.map { it.createdAt },
                    collectionCreatedList = collections.map { it.createdAt },
                    memoCreatedList = memos.map { it.createdAt },
                    locationDateList = locations.map { it.date },
                    birthDate = profile?.birthDate
                )
                EngineInput(achList, stats, sources)
            }.collect { input ->
                _stats.value = input.stats
                // 「结构还没稳定」= 上一轮还在建行 / 回填分类。这种轮次里发生的解锁
                // 都是历史补账（老存档升级、或首次启动建行后的补判），不该弹提示。
                val silent = structureDirty
                val changed = runEngine(input.ach, input.stats, input.sources, silent = silent)
                structureDirty = changed
            }
        }
    }

    /** v1.0.3：从 DataStore 的记忆相册 JSON 里数照片（解析失败按 0，绝不崩） */
    private fun parsePhotoCount(jsonText: String): Int {
        if (jsonText.isBlank()) return 0
        return runCatching {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(jsonText)
                as? kotlinx.serialization.json.JsonArray
            arr?.size ?: 0
        }.getOrDefault(0)
    }

    private fun AchievementEntity.toUi(s: AchStats): AchievementUi {
        val rule = autoKey?.let { ruleByKey[it] }
        val catId = category?.takeIf { it.isNotBlank() }
            ?: rule?.category
            ?: ACH_CAT_CUSTOM
        return AchievementUi(
            entity = this,
            categoryId = catId,
            isAuto = type == "auto",
            current = if (rule != null) rule.progressOf(s) else if (unlocked) 1 else 0,
            goal = if (rule != null) rule.goalOf() else 1
        )
    }

    /**
     * 成就引擎：建行 / 回填分类 / 按规则解锁。
     * 批次2：解锁与历史回溯均以「真实事件时间」(eventAtFor) 写入 unlockedAt，
     * 而非解锁瞬间的当前时间戳。
     */
    /**
     * @param silent true = 本轮解锁不弹右下角卡片（历史补账轮）
     * @return 本轮是否改动了成就表结构（建行 / 回填分类）；用于决定下一轮是否继续静默
     */
    private suspend fun runEngine(
        achList: List<AchievementEntity>,
        stats: AchStats,
        sources: EventSources,
        silent: Boolean
    ): Boolean {
        var structureChanged = false
        val byKey = achList.associateBy { it.autoKey }
        for (rule in AUTO_RULES) {
            val existing = byKey[rule.key]
            if (existing == null) {
                // 首次见该规则：创建锁定行（id 稳定，便于后续更新）
                repo.insert(
                    AchievementEntity(
                        id = "auto_${rule.key}",
                        title = rule.title,
                        desc = rule.desc,
                        type = "auto",
                        autoKey = rule.key,
                        unlocked = false,
                        category = rule.category
                    )
                )
                structureChanged = true
                continue
            }
            // v2 及以前建的行没有分类，回填（只写一次，之后不再触发）
            if (existing.category.isNullOrBlank()) {
                repo.update(existing.copy(category = rule.category))
                structureChanged = true
                continue
            }
            if (rule.satisfied(stats)) {
                val et = eventAtFor(rule.key, sources)
                if (!existing.unlocked) {
                    if (!processedAuto.contains(rule.key)) {
                        processedAuto.add(rule.key)
                        val unlockedRow = existing.copy(unlocked = true, unlockedAt = et ?: nowIso())
                        repo.update(unlockedRow)
                        activityRepo.add(
                            ActivityEntity(id = uid("act"), time = nowIso(), kind = "ach", title = "解锁成就：${rule.title}")
                        )
                        // v1.0.4：XP 流水（仅在「未解锁 -> 解锁」跳变时记一笔，回溯不改流水）
                        xpRepo.record("ach", XpRules.ACHIEVEMENT, "解锁成就：${rule.title}")
                        if (!silent) {
                            _unlockEvents.tryEmit(unlockedRow)
                            notifyUnlocked(unlockedRow)
                        }
                    }
                } else if (et != null && existing.unlockedAt != et) {
                    // 历史回溯：用真实事件时间覆盖「今天」误标的解锁时间
                    repo.update(existing.copy(unlockedAt = et))
                }
            }
        }
        return structureChanged
    }

    /** v1.0.5：解锁成功发一条本地通知（跟随通知总开关；失败静默不影响业务） */
    private suspend fun notifyUnlocked(a: AchievementEntity) {
        val enabled = runCatching { settings.notify.first() }.getOrElse { false }
        if (!enabled) return
        com.example.earthonline.reminder.AppNotifier.post(
            appContext,
            com.example.earthonline.reminder.AppNotifier.CHANNEL_ACH,
            com.example.earthonline.reminder.AppNotifier.ID_ACH,
            "解锁成就：${a.title}",
            a.desc.ifBlank { "又迈过一道里程碑，继续加油！" }
        )
    }

    /** 新增手动成就（默认未解锁，可指定分类） */
    fun addManual(title: String, desc: String, category: String? = null) {
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            repo.insert(
                AchievementEntity(
                    id = uid("ach"),
                    title = t,
                    desc = desc.trim(),
                    type = "manual",
                    unlocked = false,
                    category = category?.takeIf { it.isNotBlank() } ?: ACH_CAT_CUSTOM
                )
            )
        }
    }

    /** 切换手动成就的解锁状态 */
    fun toggleManual(ach: AchievementEntity) {
        if (ach.type != "manual") return
        viewModelScope.launch {
            val nowUnlocked = !ach.unlocked
            repo.update(ach.copy(unlocked = nowUnlocked, unlockedAt = if (nowUnlocked) nowIso() else null))
            if (nowUnlocked) {
                activityRepo.add(
                    ActivityEntity(id = uid("act"), time = nowIso(), kind = "ach", title = "解锁成就：${ach.title}")
                )
                // v1.0.4：XP 流水（手动成就解锁也记一笔；撤销不回冲，保持流水单调）
                xpRepo.record("ach", XpRules.ACHIEVEMENT, "解锁成就：${ach.title}")
                notifyUnlocked(ach)
            }
        }
    }

    /** 删除手动成就 */    fun deleteManual(ach: AchievementEntity) {
        if (ach.type != "manual") return
        viewModelScope.launch { repo.delete(ach.id) }
    }
}
