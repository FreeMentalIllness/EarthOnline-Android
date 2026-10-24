package com.example.earthonline.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.util.millisToDayStr
import com.example.earthonline.util.launchAccounting
import com.example.earthonline.util.todayStr
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.bouncyPress
import com.example.earthonline.ui.components.ConfettiBurst
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.GlassDialog
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.components.IconChip
import com.example.earthonline.ui.components.ModuleTints
import com.example.earthonline.ui.components.SectionHeader
import com.example.earthonline.ui.components.TagChip
import com.example.earthonline.ui.theme.HazeScrollAware
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.UserAvatar
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.GlassCard
import com.example.earthonline.ui.theme.GlassSurface
import com.example.earthonline.ui.theme.GlassSpring
import com.example.earthonline.ui.theme.GlassSpringIntSize
import com.example.earthonline.ui.theme.pressScale
import com.example.earthonline.ui.theme.OnAmber
import kotlinx.coroutines.launch

/** 主页 LazyColumn 中「人生时间轴」卡片的位置（0 基），供卡片内的「添加里程碑」定位 */

/** 首页入口定义（对应 HTML renderHome 顶部功能入口）。
 *  仅保留：任务 / 背包 / 成就 / 地图 / 记账 / 系统，去除与下方卡片重复的「新建任务」「记心情」等入口。
 *  [id] 与 HomeViewModel.DEFAULT_QUICK_ENTRIES 对应，供「自定义速览入口」按 DataStore 开关显隐。
 *  v1.0.19：[tint] 模块语义色 —— 速览瓦片图标升级为彩色圆角芯片（对标设置页图标语言）。 */
private data class HomeEntry(
    val id: String,
    /** v1.0.17：emoji → Icons.Rounded 圆角矢量（全局图标现代化） */
    val icon: ImageVector,
    val label: String,
    val sub: String,
    /** route 为空表示「记账」：不走页面导航，改为弹出下载渠道弹窗 */
    val route: String?,
    /** v1.0.19：模块语义色（ModuleTints） */
    val tint: Color = AmberPrimary
)

@Composable
fun HomeRoute(
    onNavigate: (String) -> Unit,
    moreActions: MoreMenuActions,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onNavigate = onNavigate,
        moreActions = moreActions,
        onAddMemo = vm::addQuickMemo,
        onDeleteMemo = vm::deleteMemo,
        onAddTimelineEvent = vm::addCustomTimelineEvent,
        onDeleteTimelineEvent = vm::deleteCustomTimelineEvent,
        onFeedKindsChange = vm::setFeedKinds,
        onFeedLimitChange = vm::setFeedLimit,
        onQuickEntriesChange = vm::setQuickEntries,
        onStackCardsChange = vm::setStackCards,
        onStackScaleChange = vm::setStackScale,
        onReorderEntries = vm::setEntryOrder,
        onAddCustomActivity = vm::addCustomActivity,
        onThrowbackSeen = vm::onThrowbackSeen,
        query = query,
        results = results,
        onQueryChange = vm::setQuery,
        onPickResult = { hit ->
            vm.clearQuery()
            onNavigate(hit.route)
        },
        onLevelCelebrated = vm::markLevelCelebrated
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    moreActions: MoreMenuActions,
    onAddMemo: (String, String) -> Unit,
    onDeleteMemo: (String) -> Unit,
    onAddTimelineEvent: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteTimelineEvent: (String) -> Unit = {},
    onFeedKindsChange: (Set<String>) -> Unit = {},
    /** 最近动态：首页显示条数（0 = 不限）与自定义动态添加 */
    onFeedLimitChange: (Int) -> Unit = {},
    /** 主页速览自定义入口（DataStore 持久化，至少保留一个） */
    onQuickEntriesChange: (Set<String>) -> Unit = {},
    /** v1.0.6.7：主页卡片堆叠区自定义（哪些次要卡片入堆叠，DataStore 持久化） */
    onStackCardsChange: (Set<String>) -> Unit = {},
    /** vc64：主页卡片堆叠平滑缩放（0.6~1.6，滑块任意值，DataStore 持久化） */
    onStackScaleChange: (Float) -> Unit = {},
    /** v2.0.0：主页速览入口拖拽排序结果（id 顺序，DataStore 持久化） */
    onReorderEntries: (List<String>) -> Unit = {},
    onAddCustomActivity: (String) -> Unit = {},
    /** v1.0.3：翻开「历年今日」卡（彩蛋计数） */
    onThrowbackSeen: () -> Unit = {},
    /** v1.2.1 全局搜索：当前词 / 命中项 / 改词 / 点中结果 */
    query: String = "",
    results: List<SearchHit> = emptyList(),
    onQueryChange: (String) -> Unit = {},
    onPickResult: (SearchHit) -> Unit = {},
    /** v1.0.4：升级庆祝播完回调（落档已庆祝等级，防重复弹） */
    onLevelCelebrated: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    // v1.0.6 封板：AI 抽屉关闭后清焦点 / 收键盘用（底层输入框绝不重新请求焦点）
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var showAddTimeline by remember { mutableStateOf(false) }
    // 主页速览「自定义入口」配置面板（BottomSheet）
    var showQuickEdit by remember { mutableStateOf(false) }
    // v1.0.6.7：卡片堆叠区「自定义」（选择哪些次要卡片入堆叠）
    var showStackEdit by remember { mutableStateOf(false) }
    // v1.0.6.8：最近动态配置（分类 / 条数 / 添加自定义动态）从 ActsCard 上提到主页层
    var showFeedConfig by remember { mutableStateOf(false) }
    var showAddCustomAct by remember { mutableStateOf(false) }
    // v1.0.6：速览「展开 / 收起」状态上提到主页层级 —— 只有用户主动点「收起」才折叠。
    // vc62：remember → rememberSaveable —— 旧版跳转二级页再返回时组合销毁、展开态被静默
    // 复位成收起（正是「没点收起却自动收起」的根因）；现在跨导航持久保留。
    var overviewExpanded by rememberSaveable { mutableStateOf(false) }
    // v1.0.19：AI 快捷中枢（右下角悬浮 + 按钮 → 液态玻璃底部抽屉）
    var showAiHub by remember { mutableStateOf(false) }
    // vc62（死命令 #1）：AI 悬浮入口**永久常驻** —— 取消「滑动时隐藏」与「搜索聚焦隐藏」
    // 的全部降显逻辑（灵敏度太高、来回跳变像卡顿）。搜索框聚焦/滚动时它也稳稳待在右下角。
    val homeListState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        // v1.0.18（死命令 3）：顶部整块全幅出血 —— Column 包裹「实心顶块 + 内容列表」。
        Column(Modifier.fillMaxSize()) {
        // 状态栏 + 「地球Online」标题行合成一个不透明实心块：
        // fillMaxWidth + statusBarsPadding 让背景色延伸到屏幕顶部与左右边缘；
        // 标题内容自己的水平 padding 移到块内部，不再被 LazyColumn 的 contentPadding 内缩 —— 两侧绝不留缝。
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            Box(Modifier.padding(horizontal = UiDimens.ScreenPad, vertical = 8.dp)) {
                HomeTitleBar(
                    actions = moreActions,
                    greeting = state.greeting
                )
            }
        }
    // homeListState 声明在 HomeScreen 外层作用域（FAB 滚动监听共用），这里只挂滑动模糊追踪
    HazeScrollAware(homeListState) {
    LazyColumn(
        state = homeListState,
        // v1.0.20：键盘让位已上收到 MainScaffold（bottomBar 高度与 IME 取大者）——
        // 这里不再叠 imePadding，消除「键盘上方巨大空白断层」。
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPad,
            end = UiDimens.ScreenPad,
            top = 8.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(UiDimens.Gap)
    ) {
        item(key = "search", contentType = "search") {
            HomeSearchBox(
                query = query,
                results = results,
                onQueryChange = onQueryChange,
                onPickResult = onPickResult,
                onFocusChange = { }
            )
        }

        item(key = "hero", contentType = "hero") {
            HeroBanner(
                state = state,
                onAvatarClick = { onNavigate("profile") }
            )
        }

        // v1.0.3：历年今日 —— 去年今天留下的记录，有内容才出现
        if (state.throwback.isNotEmpty()) {
            item(key = "throwback", contentType = "card") {
                ThrowbackCard(
                    items = state.throwback,
                    comeback = state.comeback,
                    onSeen = onThrowbackSeen
                )
            }
        }

        item(key = "overview", contentType = "overview") {
            OverviewGrid(
                state = state,
                expanded = overviewExpanded,
                onExpandedChange = { overviewExpanded = it },
                onNavigate = onNavigate,
                onAccounting = moreActions.onAccounting,
                onEditEntries = { showQuickEdit = true },
                onReorderEntries = onReorderEntries
            )
        }

        // v1.0.6.9（死命令）：卡片堆叠移动到「速览」正下方 —— 今日一签 / 最近动态 /
        // 日程 / 时间轴 / 人生卡片收进堆叠区，左右滑动翻页（相邻卡片边缘露出），纯展示。
        item(key = "stack", contentType = "card") {
            HomeStack(
                stackCards = state.stackCards,
                stackScale = state.stackScale,
                onStackScaleChange = onStackScaleChange,
                dailyDraw = state.dailyDraw,
                acts = state.allActs,
                feedKinds = state.feedKinds,
                feedLimit = state.feedLimit,
                calendarTasks = state.calendarTasks,
                timeline = {
                    TimelineCard(
                        items = state.timeline,
                        customIds = state.customTimeline.map { it.key }.toSet(),
                        streakStage = state.streakStage,
                        streakDays = state.streakDays,
                        onAdd = { showAddTimeline = true },
                        onDelete = onDeleteTimelineEvent,
                        pureDisplay = true
                    )
                },
                life = {
                    LifeCard(
                        name = state.displayName,
                        level = state.life.age,
                        title = state.customTitle,
                        unlockedAch = state.unlockedAch,
                        locationCount = state.locationCount,
                        doneRatio = state.doneRatio
                    )
                },
                onOpenDraw = { draw ->
                    // 一签来源路由：memo→笔记 / ach→成就 / task→任务 / loc→地图
                    onNavigate(
                        when (draw.kind) {
                            "memo" -> "notes"
                            "ach" -> "achievements"
                            "task" -> "tasks"
                            "loc" -> "map"
                            else -> "notes"
                        }
                    )
                },
                onOpenActs = { onNavigate("all_activities") },
                onOpenCalendar = { onNavigate("tasks") },
                onCustomize = { showStackEdit = true }
            )
        }
    }
    }  // vc64：滚动降级作用域闭合
        } // v1.0.18：Column（实心顶块 + 内容列表）闭合

        // v1.0.19（核心功能）：右下角悬浮「+」按钮 —— AI 快捷中枢入口。
        // vc62（死命令 #1）：**永久常驻**，不再随滚动/聚焦隐藏。
        // 悬浮在底部导航胶囊上方（58dp 胶囊 + 6dp 底距 + 间距 ≈ 96dp）；
        // v1.0.20：键盘让位由 MainScaffold 统一处理，这里不再自带 imePadding。
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 96.dp)
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(AmberPrimary, AmberPrimary.copy(alpha = 0.86f)))
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showAiHub = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "AI 快捷中枢",
                    tint = OnAmber,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // v1.0.19：AI 快捷中枢底部抽屉（语音默认 + 意图自动分发）
        AiQuickHubSheet(
            visible = showAiHub,
            // 抽屉内部已统一收口（清焦点 → 收键盘 → 关闭），这里只收状态
            onDismiss = { showAiHub = false },
            // 未配置 AI 接口时可直接跳设置页（跳转前同样清焦点收键盘）
            onConfigure = {
                showAiHub = false
                runCatching { focusManager.clearFocus() }
                runCatching { keyboardController?.hide() }
                onNavigate("settings")
            }
        )

        // 时间轴：添加自定义里程碑
        if (showAddTimeline) {
            AddTimelineDialog(
                onDismiss = { showAddTimeline = false },
                onConfirm = { title, day, note ->
                    onAddTimelineEvent(title, day, note)
                    showAddTimeline = false
                }
            )
        }

        // 主页速览：自定义显示哪些入口（DataStore 持久化，至少保留一个）
        if (showQuickEdit) {
            GlassDialog(onDismissRequest = { showQuickEdit = false }) {
                QuickEntriesSheet(
                    enabled = state.quickEntries,
                    onToggle = { id, checked ->
                        val next = if (checked) state.quickEntries + id else state.quickEntries - id
                        // 空集请求会被 ViewModel 忽略（至少保留一个），这里同步约束勾选态
                        if (next.isNotEmpty()) onQuickEntriesChange(next)
                    }
                )
            }
        }

        // v1.0.6.8（死命令 #3）：堆叠与动态设置 —— 上段勾选入堆叠卡片，下段最近动态配置
        // （原 ActsCard 内「自定义」面板上提到此，堆叠内动态卡已纯展示化）。
        if (showStackEdit) {
            GlassDialog(onDismissRequest = { showStackEdit = false }) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("卡片堆叠", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "勾选要放入堆叠区的卡片，左右滑动切换（至少保留一个）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        "dailydraw" to "今日一签",
                        "acts" to "最近动态",
                        "calendar" to "日程",
                        "timeline" to "时间轴",
                        "life" to "人生卡片"
                    ).forEach { (id, label) ->
                        val checked = id in state.stackCards
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    val next = if (on) state.stackCards + id else state.stackCards - id
                                    if (next.isNotEmpty()) onStackCardsChange(next)
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // —— 最近动态配置（原 ActsCard 自定义面板） ——
                    Text("自定义最近动态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Text("要展示的分类", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    // v1.0.6.8（死命令 #3 排版修复）：FlowRow 换行 —— 旧版定宽 Row 把
                    // 「记录」等标签挤压成竖排文字，现按内容宽度自动换行，绝不再挤压。
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("task" to "任务", "ach" to "成就", "item" to "物品", "memo" to "记录")
                            .forEach { (kind, label) ->
                                val selected = kind in state.feedKinds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val next = if (selected) state.feedKinds - kind else state.feedKinds + kind
                                        // 至少保留一个分类，避免「全部隐藏」死局
                                        if (next.isNotEmpty()) onFeedKindsChange(next)
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                    }
                    Text(
                        "「全部」标签只显示勾选的分类；自定义动态（星标）始终显示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("最多显示条数", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5 to "5 条", 10 to "10 条", 20 to "20 条", 0 to "全部").forEach { (n, label) ->
                            FilterChip(
                                selected = state.feedLimit == n,
                                onClick = { onFeedLimitChange(n) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // 3) 添加自定义动态 + 完整列表入口（v1.0.6.8：按钮文案补全为「+ 添加自定义」，
                    //    主按钮权重加大 + 不截断，根除旧版「+ 添加自」文字截断问题）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showStackEdit = false; showAddCustomAct = true },
                            modifier = Modifier.weight(1.25f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberPrimary,
                                contentColor = OnAmber
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("+ 添加自定义", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showStackEdit = false; onNavigate("all_activities") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AmberPrimary
                            ),
                            border = BorderStroke(1.5.dp, AmberPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("查看全部", maxLines = 1)
                        }
                    }

                    TextButton(onClick = { showStackEdit = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("完成")
                    }
                }
            }
        }

        // v1.0.6.8：添加自定义动态对话框（从堆叠设置面板进入）
        if (showAddCustomAct) {
            AddCustomActivityDialog(
                onDismiss = { showAddCustomAct = false },
                onConfirm = { title ->
                    onAddCustomActivity(title)
                    showAddCustomAct = false
                }
            )
        }

        // v1.0.4：升级庆祝浮层 —— 等级（周岁）跨档时弹一次，播完落档
        state.levelUp?.let { newLevel ->
            LevelUpOverlay(
                level = newLevel,
                playerName = state.displayName,
                onCelebrated = onLevelCelebrated
            )
        }
    }
}

/**
 * v1.0.4：升级庆祝浮层（纯 UI，不动 DB）。
 * 全屏半透明遮罩 + 共享撒花 + Lv 徽章卡；点击任意处或 4 秒后自动收起。
 */
@Composable
private fun LevelUpOverlay(
    level: Int,
    playerName: String,
    onCelebrated: (Int) -> Unit
) {
    val dismissed = remember(level) { mutableStateOf(false) }
    // 4 秒后自动收起；点击遮罩立即收起 —— 两者都走 onCelebrated 落档
    LaunchedEffect(level) {
        kotlinx.coroutines.delay(4000)
        if (!dismissed.value) {
            dismissed.value = true
            onCelebrated(level)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!dismissed.value) {
                    dismissed.value = true
                    onCelebrated(level)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        ConfettiBurst(burstKey = "levelup-$level")
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Celebration, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(64.dp))
                Text(
                    "Level Up!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Lv.$level",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$playerName 在地球Online又长大了一岁",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 启动一次语音识别，结果追加到输入框，错误以 Toast 呈现 */
private fun startVoice(
    controller: SpeechInputController,
    context: Context,
    onText: (String) -> Unit
) {
    controller.start(
        onFinal = { text -> onText(text) },
        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    )
}

/**
 * 无可用语音识别服务时的手动输入弹窗（国内机型的降级方案）
 */
@Composable
private fun VoiceFallbackDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动输入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "当前设备没有可用的语音识别服务，可直接输入内容（也可使用输入法的语音按钮）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    label = "内容",
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()); },
                enabled = text.isNotBlank()
            ) { Text("填入") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

/**
 * v1.2.1：主页顶部全局搜索（任务 / 物品 / 收藏）。
 *
 * 放在标题栏正下方，而不是每个列表页各放一个 —— 用户想找东西时第一反应是回主页找，
 * 而且三个数据源（任务 / 背包 / 收藏）本来就是跨页的，只有在主页做才是「全局」。
 * 有词才展开结果区，空词时整块只有一根搜索条，不抢首屏内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSearchBox(
    query: String,
    results: List<SearchHit>,
    onQueryChange: (String) -> Unit,
    onPickResult: (SearchHit) -> Unit,
    /** v1.0.6 封板：搜索框聚焦回调 —— 键盘弹起时主页右下的 AI 悬浮按钮要让位，别压在速览瓦片上 */
    onFocusChange: (Boolean) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索任务 / 物品 / 收藏…",
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "搜索") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清空")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) }
        )
        if (query.isNotBlank()) {
            // v1.0.7：全透明毛玻璃卡片 + 5% 黑遮罩（GlassCard）替代实心 Card，壁纸可透出
    GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(UiDimens.CardPad)) {
                    if (results.isEmpty()) {
                        EmptyHint("没有匹配「$query」的内容", icon = Icons.Rounded.Search)
                    } else {
                        Text(
                            "找到 ${results.size} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        results.forEach { hit ->
                            Surface(
                                onClick = { onPickResult(hit) },
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // v1.0.6 封板：搜索命中项图标改为彩色圆角矢量芯片（与速览 / 设置页同源）
                                IconChip(
                                    hit.icon,
                                    chipColor = hitTint(hit.kind),
                                    chipSize = 32.dp,
                                    iconSize = 18.dp
                                )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            hit.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (hit.sub.isNotBlank()) {
                                            Text(
                                                hit.sub,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        when (hit.kind) { "task" -> "任务"; "item" -> "背包"; else -> "收藏" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (hit !== results.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTitleBar(actions: MoreMenuActions, greeting: String) {
    // v1.0.3：季节限定徽章（春樱/夏夜/秋叶/冬雪，按节气近似切分）
    val season = remember { com.example.earthonline.util.SeasonTheme.current() }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // v1.0.17：Logo 由 🌍 emoji 换为圆角矢量地球（Public），色贴主题琥珀
        Icon(
            Icons.Rounded.Public,
            contentDescription = null,
            tint = AmberPrimary,
            modifier = Modifier.size(26.dp)
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("地球Online", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                // 当季限定徽章
                Surface(
                    color = season.accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(season.icon, contentDescription = null, tint = season.accent, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(season.label, style = MaterialTheme.typography.labelSmall, color = season.accent)
                    }
                }
            }
            // v1.0.3：动态问候语（时间段 + 最近心情关怀 + 连续天数认可）
            Text(
                greeting.ifBlank { "人生记录 · 开放世界" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        SettingsIconButton(actions = actions)
    }
}

@Composable
private fun HeroBanner(state: HomeUiState, onAvatarClick: () -> Unit) {
    // v1.0.7：全透明毛玻璃卡片 + 5% 黑遮罩（GlassCard）替代实心 Card，壁纸可透出
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AvatarWithBadge(state.avatarPath, state.avatarData, state.avatarKey, onAvatarClick)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // v1.0.7：自适应 —— 昵称再长也只占剩余空间并省略，绝不把称号徽章挤出屏幕或与之重叠
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // v1.0.3：自定义称号徽章
                    val titleText = com.example.earthonline.util.XpRules.titleFor(state.customTitle)
                    Surface(
                        color = AmberPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            titleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                // v1.0.3：连续记录成长徽标（≥3 天即出现，界面随投入生长）
                if (state.streakDays >= 3) {
                    Text(
                        "连续记录 ${state.streakDays} 天 · ${com.example.earthonline.util.GrowthStreak.stageLabel(state.streakStage)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberPrimary
                    )
                }
                Text(
                    if (state.birthDate.isBlank()) "未设置生日" else "生日 ${state.birthDate}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.signature.isNotBlank()) {
                    Text(
                        "“${state.signature}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.fieldTags.isNotEmpty() || state.hiddenFieldCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.fieldTags.take(2).forEach { tag ->
                            FieldTag(tag)
                        }
                        if (state.hiddenFieldCount > 0) FieldTag("+${state.hiddenFieldCount}")
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Lv.${state.life.age}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary
                )
                Text(
                    if (state.life.hasBirth) "距下一级还有 ${state.life.daysToNext} 天" else "设置生日后开启等级",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                ExpBar(
                    progress = if (state.life.hasBirth) state.life.progress else 0f,
                    modifier = Modifier.width(88.dp)
                )
            }
        }
    }
}

/** 个人自定义字段标签 —— 统一复用 TagChip */
@Composable
private fun FieldTag(text: String) {
    TagChip(text = text)
}

@Composable
private fun ExpBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(AmberPrimary)
        )
    }
}

// v1.0.0：预设头像 emoji 统一取自 util/AvatarPresets.kt（资料页、主页、桌面小组件共用一份）

/** 头像：点击直达个人资料页（对应 HTML hero-avatar 的 goto-profile） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarWithBadge(
    avatarPath: String?,
    avatarData: ByteArray?,
    avatarKey: String,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.size(72.dp)) {
        Surface(
            onClick = onClick,
            color = AmberPrimary.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // v1.0.0：统一走 UserAvatar —— 原图文件 + 按 72dp 控件尺寸采样
                UserAvatar(avatarPath, avatarData, avatarKey, 72.dp)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/**
 * v1.0.6.8（死命令 #3 重构）：主页「卡片堆叠」—— 置顶 + 纯展示化。
 * 今日一签 / 最近动态 / 日程 / 时间轴 / 人生卡片收进同一堆叠区，HorizontalPager
 * 左右滑动切换 + 页码指示点 + 右上角设置（堆叠卡片勾选 + 最近动态配置）。
 * **堆叠内所有卡片仅做纯展示**：剥离一切编辑与按钮操作，点击整卡跳转到对应详情页。
 * 用户可自定义把哪些卡片放入堆叠区（DataStore 持久化）。
 */
@Composable
private fun HomeStack(
    stackCards: Set<String>,
    /** vc64：平滑缩放（0.6~1.6，1.0 = 基准 356dp；滑块可停任意值，DataStore 持久化） */
    stackScale: Float,
    onStackScaleChange: (Float) -> Unit,
    dailyDraw: ThrowbackItem?,
    acts: List<ActivityEntity>,
    feedKinds: Set<String>,
    feedLimit: Int,
    calendarTasks: List<TaskEntity>,
    timeline: @Composable () -> Unit,
    life: @Composable () -> Unit,
    onOpenDraw: (ThrowbackItem) -> Unit,
    onOpenActs: () -> Unit,
    onOpenCalendar: () -> Unit,
    onCustomize: () -> Unit
) {
    // 固定展示顺序：一签 → 动态 → 日程 → 时间轴 → 人生卡片
    val pages = remember(stackCards) {
        listOf("dailydraw", "acts", "calendar", "timeline", "life").filter { it in stackCards }
    }
    if (pages.isEmpty()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(UiDimens.CardPad)) {
                Text("堆叠区是空的", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "把想常看的卡片放进堆叠，左右滑动切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onCustomize) { Text("选择卡片") }
            }
        }
        return
    }
    // vc62（死命令 #5）：v1.0.6.11 的「前后层叠」没有任何手势处理（只有 clickable），
    // 滑动完全无响应、点下层卡片翻面又与父级 LazyColumn 滚动冲突 —— 正是「堆叠划不动」的根因。
    // 现更换为最稳定的 HorizontalPager + 缩放/位移动画模拟堆叠：当前页居中满幅，
    // 前后页按页偏移收缩、下移、变淡，天然支持滑动 / 惯性 / 回弹，手势零冲突。
    // 默认最前卡片仍为「日程」。
    val defaultFront = remember(pages) {
        val i = pages.indexOf("calendar")
        if (i < 0) 0 else i
    }
    val pagerState = rememberPagerState(initialPage = defaultFront, pageCount = { pages.size })
    val pagerScope = rememberCoroutineScope()
    /* vc64：尺寸改为**平滑缩放滑块**（0.6x ~ 1.6x），取代 vc63 的大/中/小三档预设 ——
       滑块可停在任意值，卡片只整体按高度缩放（内部内容按比例让位），绝不变形。
       同时**根除中间那条灰色横向断层**：旧实现是每张卡各自 AsyncImage 采样壁纸（240px 缩略图）
       + RenderEffect 模糊 + 半透明白蒙版，卡片尺寸一大，缩放采样与模糊半径就会在卡片中段
       压出一条与上下不同色的横向色带（真机截图里的「灰杠」）。现在全站共用 Haze 的
       一张共享背景层按屏幕位置取景，卡片上只剩一层玻璃材质，断层无从产生。 */
    val scale = stackScale.coerceIn(0.6f, 1.6f)
    val stackHeight = 356.dp * scale
    val cardHeight = stackHeight - 26.dp
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("卡片堆叠", Modifier.weight(1f), icon = Icons.Rounded.Layers)
            // vc64：平滑尺寸滑块（紧凑，只占 96dp 宽，拖动即时生效）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "高",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = scale,
                    onValueChange = onStackScaleChange,
                    valueRange = 0.6f..1.6f,
                    modifier = Modifier
                        .width(96.dp)
                        .height(22.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(pages.size) { idx ->
                    val active = idx == pagerState.currentPage
                    Box(
                        Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) AmberPrimary else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
            IconButton(onClick = onCustomize, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.Tune, contentDescription = "堆叠与动态设置", modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1
        ) { page ->
            // 页偏移（0 = 当前页，1 = 相邻页）：驱动缩放 / 下移 / 透明度的堆叠景深
            val pageOffset = kotlin.math.abs(
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            )
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .graphicsLayer {
                        val f = pageOffset.coerceIn(0f, 1f)
                        val s = 1f - f * 0.07f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - f * 0.35f
                        translationY = f * 18.dp.toPx()
                    }
                    .clickable {
                        if (page == pagerState.currentPage) {
                            // 当前页：点击进入对应详情（一签/动态/日程；时间轴/人生卡纯展示）
                            when (pages[page]) {
                                "dailydraw" -> dailyDraw?.let { onOpenDraw(it) }
                                "acts" -> onOpenActs()
                                "calendar" -> onOpenCalendar()
                            }
                        } else {
                            // 相邻页：点击把它翻到最前（带弹簧动画）
                            pagerScope.launch { pagerState.animateScrollToPage(page) }
                        }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (pages[page]) {
                        "dailydraw" -> {
                            val draw = dailyDraw
                            if (draw != null) DailyDrawCard(item = draw)
                            else EmptyHint("今天没有抽到记录，明天再来")
                        }
                        "acts" -> ActsFeedPure(allActs = acts, feedKinds = feedKinds, feedLimit = feedLimit)
                        "calendar" -> CalendarCardPure(tasks = calendarTasks)
                        "timeline" -> timeline()
                        "life" -> life()
                    }
                }
            }
        }
        Text(
            "左右滑动翻页 · 点击当前卡片查看详情 · 右上角自定义",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * v1.0.6.8（死命令 #3）：堆叠区「最近动态」纯展示版 —— 无 Tab、无按钮、无手势，
 * 只列动态流；点击整卡由堆叠容器统一跳转「全部动态」页。
 */
@Composable
private fun ActsFeedPure(
    allActs: List<ActivityEntity>,
    feedKinds: Set<String>,
    feedLimit: Int
) {
    val filtered = allActs.filter { it.kind in feedKinds || it.kind == "custom" }
    val shown = if (feedLimit > 0) filtered.take(feedLimit) else filtered.take(10)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(UiDimens.CardPad),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader("最近动态", icon = Icons.Rounded.Bolt, chipColor = ModuleTints.Feed)
            if (shown.isEmpty()) {
                EmptyHint("暂无动态，去完成任务或解锁成就试试")
            } else {
                shown.forEach { act ->
                    val actIcon = when (act.kind) {
                        "ach" -> Icons.Rounded.EmojiEvents
                        "task" -> Icons.Rounded.Checklist
                        "item" -> Icons.Rounded.Backpack
                        "custom" -> Icons.Rounded.Star
                        else -> Icons.Rounded.ChatBubbleOutline
                    }
                    val verb = when (act.kind) {
                        "ach" -> "解锁成就"
                        "task" -> "完成任务"
                        "item" -> "获得物品"
                        "custom" -> "自定义事件"
                        else -> "记录"
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            act.time.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            actIcon,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "$verb「${act.title}」",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (filtered.size > shown.size) {
                    Text(
                        "还有 ${filtered.size - shown.size} 条动态…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * v2.0.0：堆叠区「日程」卡 —— 换用 kizitonwose/Calendar，彻底根治「日期不可点」。
 *
 * 旧 Bug 根因：v1.0.6.8 起这张卡是「纯展示版」—— 手绘 7×N 网格里每个日期 Box
 * 只配了 background，压根没有 clickable，整张卡由堆叠容器统一跳任务页，
 * 于是用户点任何一天都毫无反应（看起来像日历坏了）。
 *
 * 现在：① 每个日期独立可点（选中态 / 今天态 / 有安排的日子打琥珀圆点标记）
 *       ② 左右箭头平滑切换月份（animateScrollToMonth），也可左右滑动翻月
 *       ③ 选中日期下方实时列出当天安排（复用同一份 tasks 数据源）。
 */
@Composable
private fun CalendarCardPure(tasks: List<TaskEntity>) {
    val today = remember { java.time.LocalDate.now() }
    var selected by remember { mutableStateOf<java.time.LocalDate?>(today) }
    val busyDays = remember(tasks) {
        tasks.mapNotNull { it.dueDate?.take(10)?.takeIf { d -> d.isNotBlank() } }.toSet()
    }
    val calState = rememberCalendarState(
        startMonth = java.time.YearMonth.now().minusMonths(36),
        endMonth = java.time.YearMonth.now().plusMonths(36),
        firstVisibleMonth = java.time.YearMonth.now(),
        // 固定周一起排（中文习惯），同时让表头「一…日」永远与列对齐
        firstDayOfWeek = java.time.DayOfWeek.MONDAY
    )
    val scope = rememberCoroutineScope()
    val visibleMonth = calState.firstVisibleMonth.yearMonth
    val dayTasks = remember(selected, tasks) {
        val k = selected?.toString().orEmpty()
        if (k.isBlank()) emptyList() else tasks.filter { it.dueDate?.take(10) == k }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(UiDimens.CardPad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    text = "\u65e5\u7a0b",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.CalendarMonth,
                    chipColor = ModuleTints.Tasks
                )
                IconButton(
                    onClick = { scope.launch { calState.animateScrollToMonth(visibleMonth.minusMonths(1)) } },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "\u4e0a\u4e2a\u6708", modifier = Modifier.size(18.dp))
                }
                Text(
                    "${visibleMonth.year}\u5e74${visibleMonth.monthValue}\u6708",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { scope.launch { calState.animateScrollToMonth(visibleMonth.plusMonths(1)) } },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "\u4e0b\u4e2a\u6708", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            HorizontalCalendar(
                state = calState,
                calendarScrollPaged = true,
                modifier = Modifier.fillMaxWidth(),
                monthHeader = { _ ->
                    Row(Modifier.fillMaxWidth()) {
                        listOf("\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d", "\u65e5").forEach { w ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    w,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                dayContent = { day ->
                    if (day.position != DayPosition.MonthDate) {
                        Box(Modifier.size(34.dp))
                    } else {
                        val key = day.date.toString()
                        val isToday = day.date == today
                        val busy = key in busyDays
                        val isSel = day.date == selected
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSel -> AmberPrimary
                                        isToday -> AmberPrimary.copy(alpha = 0.20f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { selected = day.date },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSel -> Color.White
                                    busy -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                }
                            )
                            if (busy && !isSel) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 3.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(AmberPrimary)
                                )
                            }
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (selected == today) "\u4eca\u5929\uff08${visibleMonth.monthValue}\u6708${today.dayOfMonth}\u65e5\uff09"
                else "${selected?.monthValue ?: today.monthValue}\u6708${selected?.dayOfMonth ?: today.dayOfMonth}\u65e5",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            if (dayTasks.isEmpty()) {
                Text(
                    "\u8fd9\u4e00\u5929\u6ca1\u6709\u5b89\u6392\uff0c\u5b89\u5fc3\u8fc7\u4e00\u5929\u3002",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                dayTasks.take(3).forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewGrid(
    state: HomeUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onAccounting: () -> Unit,
    onEditEntries: () -> Unit = {},
    /** v2.0.0：拖拽排序结果回调（速览展开区长按瓦片拖动排序） */
    onReorderEntries: (List<String>) -> Unit = {}
) {
    val context = LocalContext.current
    // v1.0.6.11（死命令 #1）：记账未安装目标 App 时，弹出来源选择弹窗（网盘 + GitHub）
    var showAccDialog by remember { mutableStateOf(false) }
    // v1.0.6.7（死命令 #2 入口拆分）：原「任务」拆为「日常」+「Todo」两项，原「背包」拆为
    // 「物品」+「收藏」两项。日常/Todo 同走任务页（内部按状态筛选），物品走背包页、收藏走收藏页。
    // vc62（跳转修复）：Todo 的 route 直挂 "new_todo"（任务页 Todo Tab + 自动弹新建对话框）——
    // 旧版 route 写死 "tasks"，收起态走特殊分支没错、展开区走通用 route 却跳成普通任务页，
    // 正是「点 Todo 跳转错误」的根因；现在收起/展开两条路径同一个语义。
    // vc62：新增「游戏库」入口（Steam 游戏库独立页）。
    val allEntries = listOf(
        HomeEntry("daily", Icons.Rounded.Today, "日常", "完成率 ${state.doneRatio}%", "tasks", ModuleTints.Tasks),
        HomeEntry("todo", Icons.Rounded.PlaylistAddCheck, "Todo", "${state.totalTasks - state.doneTasks} 待完成", "new_todo", ModuleTints.Tasks),
        HomeEntry("items", Icons.Rounded.Backpack, "物品", "${state.itemCount} 件物品", "backpack", ModuleTints.Backpack),
        HomeEntry("collections", Icons.Rounded.Favorite, "收藏", "${state.collectionCount} 个收藏", "collections", ModuleTints.Backpack),
        HomeEntry("notes", Icons.Rounded.EditNote, "笔记", "${state.noteCount} 篇笔记", "notes", ModuleTints.Notes),
        HomeEntry("games", Icons.Rounded.SportsEsports, "游戏库", "Steam 游戏收藏", "steam_library", ModuleTints.Data),
        HomeEntry("album", Icons.Rounded.PhotoLibrary, "相册", "${state.albumCount} 张照片", "album", ModuleTints.Album),
        HomeEntry("map", Icons.Rounded.Map, "地图", "${state.locationCount} 处足迹", "map", ModuleTints.Map),
        HomeEntry("accounting", Icons.Rounded.BarChart, "记账", "账本与导出", null, ModuleTints.Accounting),
        HomeEntry("data", Icons.Rounded.QueryStats, "数据", "统计看板", "data", ModuleTints.Data),
        HomeEntry("achievements", Icons.Rounded.EmojiEvents, "成就", "${state.unlockedAch}/${state.totalAch}", "achievements", ModuleTints.Achievements),
        HomeEntry("ai", Icons.Rounded.SmartToy, "系统", "AI 助手", "ai", ModuleTints.System)
    )
    // 用户自定义显隐（DataStore）；异常空集时回落全部，防止入口全灭
    val visible = allEntries.filter { it.id in state.quickEntries }.ifEmpty { allEntries }

    // 死命令 #5（v1.0.6.x）：速览默认收起 —— 收起态只固定 6 个核心入口
    // （游戏库/相册/地图/记账/数据/成就，3 列 × 2 行满排），不受历史自定义影响；
    // vc63（真机反馈）：**「系统」等不常用入口默认收起**，并修复 7 格默认集导致的
    // 「孤行大留白」—— 收起态恒为完美 3×2。
    val DEFAULT_IDS = setOf("album", "map", "accounting", "data", "achievements", "games")
    val defaultEntries = allEntries.filter { it.id in DEFAULT_IDS }
    val moreEntries = visible.filter { it.id !in DEFAULT_IDS }

    /* v2.0.0：拖拽排序（Reorderable）—— 展开区瓦片可长按拖动改顺序，
       顺序以 id 列表持久化到 DataStore；空顺序 = 用默认排列。 */
    var sortMode by remember { mutableStateOf(false) }
    var orderedIds by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(moreEntries.size, state.entryOrder) {
        val saved = state.entryOrder.filter { id -> moreEntries.any { it.id == id } }
        val rest = moreEntries.map { it.id }.filter { it !in saved }
        orderedIds = saved + rest
    }


    Column(
        // v1.0.7：展开/收起用 spring（带阻尼回弹），不再生硬跳变
        Modifier.animateContentSize(animationSpec = GlassSpringIntSize),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // weight 让标题占据剩余宽度：大字号下标题换行也不会把右侧按钮挤出屏幕
            SectionHeader("速览", Modifier.weight(1f), icon = Icons.Rounded.Explore)            // v1.0.7：右上角「展开 / 收起」按钮，替换原「自定义」；自定义入口收进展开区
            // v2.0.0：排序开关（进入后瓦片变为长按可拖动）
            if (expanded) {
                TextButton(
                    onClick = {
                        sortMode = !sortMode
                        if (!sortMode) onReorderEntries(orderedIds)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        if (sortMode) "完成" else "排序",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sortMode) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            TextButton(
                onClick = { onExpandedChange(!expanded) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    if (expanded) "收起 ▲" else "展开 ▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberPrimary,
                    maxLines = 1
                )
            }
        }

        // v1.0.7：3 列网格（用户指定 3x2）；特大字号降为 2 列防挤压
        val cols = if (LocalDensity.current.fontScale >= 1.5f) 2 else 3
        defaultEntries.chunked(cols).forEach { rowEntries ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowEntries.forEach { entry ->
                    WaffleTile(
                        icon = entry.icon,
                        label = entry.label,
                        tint = entry.tint,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 死命令 #1（v1.0.6.x）：速览 = 先跳对应二级主页面，再自动弹出该功能的操作面板。
                        // 记账 → 唤起外部记账 App（其记账页自带键盘输入，即「记一笔」）；
                        //        目标 App 未安装时弹下载渠道弹窗（网盘 + GitHub）。
                        // Todo → route 已直挂 new_todo：任务页 Todo Tab + 自动弹新建任务框；
                        // 日常 → 任务页；笔记 → 直达新建笔记编辑页；其余按 route 跳转。
                        when (entry.id) {
                            "accounting" -> { if (!launchAccounting(context)) showAccDialog = true }
                            "daily" -> onNavigate("tasks")
                            "notes" -> onNavigate("note_editor?id=&folder=")
                            else -> if (entry.route != null) onNavigate(entry.route)
                            else { if (!launchAccounting(context)) showAccDialog = true }
                        }
                    }
                }
                // 末行不足一整行时用空白占位，保持左右对齐
                repeat(cols - rowEntries.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // 展开区：更多入口 + 自定义入口，垂直展开 + 淡入淡出（spring 弹性，与容器 animateContentSize 协同）
        // vc63（真机反馈「奇怪留空」根治）：改用 FlowRow + 固定瓦片宽 —— 旧版 chunked Row +
        // weight 占位会让末行出现整块空白列；FlowRow 逐项紧凑换行，绝无无意义留空。
        AnimatedVisibility(
            visible = expanded && moreEntries.isNotEmpty(),
            enter = expandVertically(animationSpec = GlassSpringIntSize) +
                fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = GlassSpringIntSize) +
                fadeOut(tween(180))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sortMode) {
                    SortableEntryGrid(
                        entries = orderedIds.mapNotNull { id -> moreEntries.firstOrNull { it.id == id } },
                        cols = cols,
                        onOrderChange = { orderedIds = it },
                        onCommit = { onReorderEntries(it) }
                    )
                    Text(
                        "长按瓦片拖动即可排序",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 10.dp
                    val tileW = (maxWidth - gap * (cols - 1)) / cols
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        moreEntries.forEach { entry ->
                            WaffleTile(
                                icon = entry.icon,
                                label = entry.label,
                                tint = entry.tint,
                                modifier = Modifier.width(tileW)
                            ) {
                                if (entry.route != null) onNavigate(entry.route)
                                else { if (!launchAccounting(context)) showAccDialog = true }
                            }
                        }
                    }
                }
                }
                // 自定义入口（原右上角按钮，收进展开区）
                TextButton(
                    onClick = onEditEntries,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text(
                        "自定义速览入口",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // 记账未安装目标 App 时弹出来源选择弹窗（网盘 + GitHub 两个入口）
        // 必须置于展开区之外、随速览整体常驻，否则收起态点「记账」不会弹窗。
        if (showAccDialog) {
            AccountingDownloadDialog(onDismiss = { showAccDialog = false })
        }
    }
}

/** 搜索命中项的模块语义色（与速览瓦片同一套 [ModuleTints]） */
private fun hitTint(kind: String): Color = when (kind) {
    "task" -> ModuleTints.Tasks
    "item" -> ModuleTints.Backpack
    "collection" -> ModuleTints.Album
    "memo" -> ModuleTints.Notes
    "achievement" -> ModuleTints.Achievements
    "location" -> ModuleTints.Map
    else -> AmberPrimary
}

/** 速览自定义入口的配置面板（BottomSheet 内容）：勾选显示 / 隐藏，至少保留一个 */
@Composable
private fun QuickEntriesSheet(enabled: Set<String>, onToggle: (String, Boolean) -> Unit) {
    // 与 OverviewGrid 的 allEntries 保持同一份清单（含隐藏项，才能重新勾回来）
    // v1.0.19：图标改用模块语义色，与速览瓦片芯片同源
    val quickAll = listOf(
        QuickEntryDef("daily", Icons.Rounded.Today, "日常", ModuleTints.Tasks),
        QuickEntryDef("todo", Icons.Rounded.PlaylistAddCheck, "Todo", ModuleTints.Tasks),
        QuickEntryDef("items", Icons.Rounded.Backpack, "物品", ModuleTints.Backpack),
        QuickEntryDef("collections", Icons.Rounded.Favorite, "收藏", ModuleTints.Backpack),
        QuickEntryDef("notes", Icons.Rounded.EditNote, "笔记", ModuleTints.Notes),
        QuickEntryDef("games", Icons.Rounded.SportsEsports, "游戏库", ModuleTints.Data),
        QuickEntryDef("album", Icons.Rounded.PhotoLibrary, "相册", ModuleTints.Album),
        QuickEntryDef("map", Icons.Rounded.Map, "地图", ModuleTints.Map),
        QuickEntryDef("accounting", Icons.Rounded.BarChart, "记账", ModuleTints.Accounting),
        QuickEntryDef("data", Icons.Rounded.QueryStats, "数据", ModuleTints.Data),
        QuickEntryDef("achievements", Icons.Rounded.EmojiEvents, "成就", ModuleTints.Achievements),
        QuickEntryDef("ai", Icons.Rounded.SmartToy, "系统", ModuleTints.System)
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("自定义速览入口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "勾选要显示在主页顶部的入口（至少保留一个），设置立即生效并自动保存。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        quickAll.forEach { def ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = def.id in enabled,
                    onCheckedChange = { onToggle(def.id, it) }
                )
                Icon(
                    def.icon,
                    contentDescription = null,
                    tint = def.tint,
                    modifier = Modifier.size(18.dp)
                )
                Text(def.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** v1.0.19：速览自定义面板条目（图标 + 模块语义色） */
private data class QuickEntryDef(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val tint: Color
)

/**
 * v1.0.12：华夫菜单单格 —— 材质统一为 [GlassSurface]。
 * 之前这里是一个裸 Box + 1dp outlineVariant 描边，跟任务/笔记/背包的液态玻璃卡
 * 完全是两套材质（无毛玻璃、无底部暗化、无高光边），是「速览卡片与其它卡片不一致」的根源。
 * 现在：同一套玻璃底 + 同一套色散高光描边 + 同一 16dp 圆角，只保留触点聚光灯与按压缩放。
 */
@Composable
private fun WaffleTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    /** v1.0.19：模块语义色（图标芯片），默认品牌琥珀 */
    tint: Color = AmberPrimary,
    onClick: () -> Unit
) {
    var spot by remember { mutableStateOf<Offset?>(null) }
    val shape = RoundedCornerShape(UiDimens.CardRadius)
    GlassSurface(
        shape = shape,
        showBorder = true,
        modifier = modifier
            .aspectRatio(1f)
            // v1.0.7：按压缩放（94% -> 100% spring 弹回），连续按压反馈
            .pressScale(pressed = spot != null, pressedScale = 0.94f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        spot = offset
                        tryAwaitRelease()
                        spot = null
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        // 触点聚光灯（只叠在最上层，不参与测量）
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    val s = spot
                    if (s != null) {
                        val r = size.maxDimension * 0.8f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(AmberPrimary.copy(alpha = 0.32f), Color.Transparent),
                                center = s,
                                radius = r
                            ),
                            radius = r,
                            center = s
                        )
                    }
                }
        )
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // v1.0.19：图标升级为彩色圆角芯片（模块语义色），与设置页设计语言统一
            IconChip(
                icon,
                chipColor = tint,
                chipSize = 40.dp,
                iconSize = 22.dp,
                contentDescription = label
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 添加一条自定义动态（kind=custom，随动态流与时间轴展示） */
@Composable
private fun AddCustomActivityDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义动态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(
                    title, { title = it.take(60) },
                    label = "事件内容",
                    placeholder = "例如：开始一段新旅程",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "自定义动态会显示在主页「最近动态」中（星标条目），不受分类筛选影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

/**
 * v1.0.3：历年今日 —— 去年今天留下的日志 / 完成任务 / 足迹。
 * 有内容才渲染；首次展开会触发彩蛋计数（时光回声）。
 * 断更鼓励语也放这里：回归的用户先看到「你的星球一直在」。
 */
@Composable
private fun ThrowbackCard(
    items: List<ThrowbackItem>,
    comeback: String?,
    onSeen: () -> Unit
) {
    LaunchedEffect(Unit) { onSeen() }
    // v1.0.7：全透明毛玻璃卡片 + 5% 黑遮罩（GlassCard）替代实心 Card，壁纸可透出
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text("历年今日", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            comeback?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items.forEach { entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (entry.kind) {
                            "task" -> Icons.Rounded.Checklist
                            "loc" -> Icons.Rounded.Map
                            else -> Icons.Rounded.ChatBubbleOutline
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${entry.day.take(4)}年的今天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * v1.0.3：今日一签 —— 每天从过去的日志与成就里确定性抽出一条。
 * 同一天刷新结果一致，明天自动换新，让老内容每天都被重新看见。
 */
@Composable
private fun DailyDrawCard(item: ThrowbackItem) {
    // v1.0.7：全透明毛玻璃卡片 + 5% 黑遮罩（GlassCard）替代实心 Card，壁纸可透出
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = AmberPrimary.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text("今日一签", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "来自 ${item.day}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 人生时间轴：在自动事件之外，允许用户添加自定义里程碑（「+」按钮），并可删除自定义项。
 * 自定义事件由 HomeViewModel 经 DataStore 持久化（不改动 Room 表结构）。
 * v1.0.3：连续记录生长机制 —— 阶段徽标随投入升级（萌芽/抽枝/繁茂/参天）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineCard(
    items: List<TimelineItem>,
    customIds: Set<String>,
    streakStage: Int,
    streakDays: Int,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    /** v1.0.6.8（死命令 #3）：堆叠区内纯展示 —— 隐藏「+」与删除按钮，点击整卡跳详情 */
    pureDisplay: Boolean = false
) {
    // 与主页其他卡片（LifeCard / ActsCard / 世界日志等）完全同构：
    // 默认 Card + UiDimens.CardRadius 圆角 + 默认容器色（surfaceContainerLow），
    // 不再单独覆盖 containerColor，保证浅深主题下颜色、圆角与邻卡零偏差。
    // v1.0.7：全透明毛玻璃卡片 + 5% 黑遮罩（GlassCard）替代实心 Card，壁纸可透出
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = AmberPrimary.copy(alpha = 0.16f),
                        shape = CircleShape,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text("人生时间轴", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    // v1.0.3：生长阶段徽标（≥3 天出现，随天数升级）
                    if (streakStage > 0) {
                        Text(
                            com.example.earthonline.util.GrowthStreak.stageLabel(streakStage),
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberPrimary
                        )
                    }
                }
                if (!pureDisplay) {
                    IconButton(onClick = onAdd, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加里程碑", tint = AmberPrimary)
                    }
                }
            }
            if (items.isEmpty()) {
                EmptyHint(
                    if (pureDisplay) "还没有重要事件" else "还没有重要事件，点右上角 + 记录一个里程碑吧"
                )
            } else {
                items.forEach { item ->
                    val isCustom = item.key in customIds
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(timelineColor(item.kind))
                        )
                        Text(item.day, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            when (item.kind) {
                                "ach" -> Icons.Rounded.EmojiEvents
                                "loc" -> Icons.Rounded.Map
                                "custom" -> Icons.Rounded.Star
                                else -> Icons.Rounded.Checklist
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCustom && !pureDisplay) {
                            IconButton(onClick = { onDelete(item.key) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimelineDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, day: String, note: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(todayStr()) }
    var note by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加里程碑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(title, { title = it.take(40) }, label = "标题",
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                GlassTextField(day, {}, readOnly = true, label = "日期",
                    trailingIcon = { IconButton({ showDate = true }) { Icon(Icons.Rounded.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth())
                GlassTextField(note, { note = it.take(200) }, label = "备注（可选）",
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), day, note.trim()) },
                enabled = title.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { day = millisToDayStr(it) }
                showDate = false
            }) { Text("确定") } },
            dismissButton = { TextButton({ showDate = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifeCard(
    name: String,
    level: Int,
    title: String,
    unlockedAch: Int,
    locationCount: Int,
    doneRatio: Int
) {
    // v1.0.7：全透明毛玻璃卡片 + 5% 黑遮罩（GlassCard）替代实心 Card，壁纸可透出
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(UiDimens.CardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("地球Online · 人生卡片", icon = Icons.Rounded.Public)
            // 昵称加前缀，避免纯数字昵称（如「1」）看起来像渲染残留
            Text(
                if (name.isBlank()) "未命名旅行者" else "昵称：$name",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LifeStat("Lv.$level", "等级")
                LifeStat("$unlockedAch", "成就")
                LifeStat("$locationCount", "足迹")
                LifeStat("$doneRatio%", "完成率")
            }
            // v1.0.7：徽章墙已按需求删除（佩戴/更换入口、成就徽章区、管理对话框全量下线）
        }
    }
}

@Composable
private fun LifeStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AmberPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun timelineColor(kind: String) = when (kind) {
    "ach" -> AmberPrimary
    "loc" -> MaterialTheme.colorScheme.tertiary
    "custom" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

private val MEMO_TYPES = listOf(
    // v1.0.17：类别图标 emoji → 圆角矢量（随笔/灵感/重要/心情）
    Triple("note", Icons.Rounded.ChatBubbleOutline, "随笔"),
    Triple("idea", Icons.Rounded.Lightbulb, "灵感"),
    Triple("important", Icons.Rounded.PushPin, "重要"),
    Triple("mood", Icons.Rounded.Mood, "心情")
)

@Composable
private fun MemoTypeRow(selected: String, onSelect: (String) -> Unit) {
    // v1.0.4：类型选择行改为单行横向滚动 —— 任何系统字号下四个 chip 都保持在
    // 同一水平线上，放得下时一屏铺开，放不下时可滑动，绝不换行也不截断文字。
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MEMO_TYPES.forEach { (value, icon, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                leadingIcon = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoRow(text: String, type: String, time: String, onDelete: () -> Unit) {
    // v1.0.17：备忘行首图标 emoji → 圆角矢量
    val dot = when (type) {
        "important" -> Icons.Rounded.PushPin
        "idea" -> Icons.Rounded.Lightbulb
        "mood" -> Icons.Rounded.Mood
        else -> Icons.Rounded.ChatBubbleOutline
    }
    // v1.0.3：右滑删除（M3 SwipeToDismissBox，松手后未过阈值自动回弹）
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(UiDimens.ItemRadius))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    "删除",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    ) {
        // 不透明底：防止红色删除背景从内容缝隙里透出来
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(dot, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(14.dp))
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * v2.0.0：速览入口的**可拖拽排序网格**（Reorderable）。
 *
 * 为什么必须换掉 FlowRow 才能拖拽：FlowRow 没有 Lazy 布局的 item 索引信息，
 * 拿不到「拖到第几个」；Reorderable 依赖 Lazy* 的 itemInfo 做位移计算。
 * 这里用固定列数 + 固定高度（行数 × 瓦片高）的 LazyVerticalGrid 承载，
 * userScrollEnabled = false，避免与外层主页 LazyColumn 抢滚动。
 */
@Composable
private fun SortableEntryGrid(
    entries: List<HomeEntry>,
    cols: Int,
    onOrderChange: (List<String>) -> Unit,
    onCommit: (List<String>) -> Unit
) {
    var ids by remember { mutableStateOf(entries.map { it.id }) }
    LaunchedEffect(entries.map { it.id }.joinToString()) {
        val cur = ids.filter { id -> entries.any { it.id == id } }
        val rest = entries.map { it.id }.filter { it !in cur }
        ids = cur + rest
    }
    val shown = ids.mapNotNull { id -> entries.firstOrNull { it.id == id } }
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val l = ids.toMutableList()
        if (from.index in l.indices && to.index in l.indices) {
            l.add(to.index, l.removeAt(from.index))
            ids = l
            onOrderChange(l)
        }
    }
    val rows = ((shown.size + cols - 1) / cols).coerceAtLeast(1)
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = gridState,
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .height((rows * 84).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(shown, key = { it.id }) { entry ->
            ReorderableItem(
                state = reorderState,
                key = entry.id
            ) { isDragging ->
                WaffleTile(
                    icon = entry.icon,
                    label = entry.label,
                    tint = entry.tint,
                    modifier = Modifier
                        .longPressDraggableHandle(
                            onDragStopped = { onCommit(ids) }
                        )
                        .graphicsLayer {
                            scaleX = if (isDragging) 1.06f else 1f
                            scaleY = if (isDragging) 1.06f else 1f
                            alpha = if (isDragging) 0.92f else 1f
                        }
                ) { /* 排序态只排不跳，避免手滑误入二级页 */ }
            }
        }
    }
}
