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
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.util.millisToDayStr
import com.example.earthonline.util.todayStr
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.ConfettiBurst
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.components.IconChip
import com.example.earthonline.ui.components.ModuleTints
import com.example.earthonline.ui.components.SectionHeader
import com.example.earthonline.ui.components.TagChip
import com.example.earthonline.ui.components.TrackScrollBlur
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

@OptIn(ExperimentalMaterial3Api::class)
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
    // v1.0.6：速览「展开 / 收起」状态上提到主页层级 —— 只有用户主动点「收起」或触发
    //         特定页面跳转才折叠；点击其它区域 / 其它操作不会导致展开态被重组复位收起。
    var overviewExpanded by remember { mutableStateOf(false) }
    // v1.0.19：AI 快捷中枢（右下角悬浮 + 按钮 → 液态玻璃底部抽屉）
    var showAiHub by remember { mutableStateOf(false) }
    // v1.0.6 封板：搜索框是否聚焦 —— 聚焦时隐藏 AI 悬浮按钮（键盘顶起后它会压在速览瓦片上）
    var searchFocused by remember { mutableStateOf(false) }

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
    val homeListState = rememberLazyListState()
    TrackScrollBlur(homeListState)
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
                onFocusChange = { searchFocused = it }
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

        // v1.0.3：今日一签 —— 每天从过去随机展示一条记录
        state.dailyDraw?.let { draw ->
            item(key = "dailydraw", contentType = "card") {
                DailyDrawCard(item = draw)
            }
        }

        item(key = "overview", contentType = "overview") {
            OverviewGrid(
                state = state,
                expanded = overviewExpanded,
                onExpandedChange = { overviewExpanded = it },
                onNavigate = onNavigate,
                onAccounting = moreActions.onAccounting,
                onEditEntries = { showQuickEdit = true }
            )
        }

        item(key = "acts", contentType = "card") {
            ActsCard(
                allActs = state.allActs,
                feedKinds = state.feedKinds,
                feedLimit = state.feedLimit,
                onFeedKindsChange = onFeedKindsChange,
                onFeedLimitChange = onFeedLimitChange,
                onAddCustom = onAddCustomActivity,
                onOpenAll = { onNavigate("all_activities") }
            )
        }

        item(key = "timeline", contentType = "card") {
            TimelineCard(
                items = state.timeline,
                customIds = state.customTimeline.map { it.key }.toSet(),
                streakStage = state.streakStage,
                streakDays = state.streakDays,
                onAdd = { showAddTimeline = true },
                onDelete = onDeleteTimelineEvent
            )
        }

        item(key = "lifecard", contentType = "card") {
            LifeCard(
                name = state.displayName,
                level = state.life.age,
                title = state.customTitle,
                unlockedAch = state.unlockedAch,
                locationCount = state.locationCount,
                doneRatio = state.doneRatio
            )
        }
    }
        } // v1.0.18：Column（实心顶块 + 内容列表）闭合

        // v1.0.19（核心功能）：右下角悬浮「+」按钮 —— AI 快捷中枢入口。
        // 悬浮在底部导航胶囊上方（58dp 胶囊 + 6dp 底距 + 间距 ≈ 96dp）；
        // v1.0.20：键盘让位由 MainScaffold 统一处理，这里不再自带 imePadding。
        AnimatedVisibility(
            visible = !searchFocused,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 96.dp),
            enter = fadeIn(),
            exit = fadeOut()
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
            ModalBottomSheet(onDismissRequest = { showQuickEdit = false }) {
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

@Composable
private fun OverviewGrid(
    state: HomeUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onAccounting: () -> Unit,
    onEditEntries: () -> Unit = {}
) {
    val allEntries = listOf(
        HomeEntry("tasks", Icons.Rounded.Checklist, "任务", "完成率 ${state.doneRatio}%", "tasks", ModuleTints.Tasks),
        HomeEntry("backpack", Icons.Rounded.Backpack, "背包", "${state.itemCount} 件物品", "backpack", ModuleTints.Backpack),
        HomeEntry("notes", Icons.Rounded.EditNote, "笔记", "${state.noteCount} 篇笔记", "notes", ModuleTints.Notes),
        HomeEntry("album", Icons.Rounded.PhotoLibrary, "相册", "${state.albumCount} 张照片", "album", ModuleTints.Album),
        HomeEntry("map", Icons.Rounded.Map, "地图", "${state.locationCount} 处足迹", "map", ModuleTints.Map),
        HomeEntry("accounting", Icons.Rounded.BarChart, "记账", "账本与导出", null, ModuleTints.Accounting),
        HomeEntry("data", Icons.Rounded.QueryStats, "数据", "统计看板", "data", ModuleTints.Data),
        HomeEntry("achievements", Icons.Rounded.EmojiEvents, "成就", "${state.unlockedAch}/${state.totalAch}", "achievements", ModuleTints.Achievements),
        HomeEntry("ai", Icons.Rounded.SmartToy, "系统", "AI 助手", "ai", ModuleTints.System)
    )
    // 用户自定义显隐（DataStore）；异常空集时回落全部，防止入口全灭
    val visible = allEntries.filter { it.id in state.quickEntries }.ifEmpty { allEntries }

    // v1.0.7：速览改为「3x2 网格 + 右上角展开」——
    //   收起态固定 6 格（任务/背包/笔记/相册/地图/记账），两行三列，不受历史自定义影响
    //   （旧版本存过的显隐集合曾把网格挤成 4 格/2 列，违反 3x2 规格）；
    //   其余入口（数据/成就/系统）由右上角「展开」按钮平滑放出/收回。
    val DEFAULT_IDS = setOf("tasks", "backpack", "notes", "album", "map", "accounting")
    val defaultEntries = allEntries.filter { it.id in DEFAULT_IDS }
    val moreEntries = visible.filter { it.id !in DEFAULT_IDS }

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
                        if (entry.route != null) onNavigate(entry.route) else onAccounting()
                    }
                }
                // 末行不足一整行时用空白占位，保持左右对齐
                repeat(cols - rowEntries.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // 展开区：更多入口 + 自定义入口，垂直展开 + 淡入淡出（spring 弹性，与容器 animateContentSize 协同）
        AnimatedVisibility(
            visible = expanded && moreEntries.isNotEmpty(),
            enter = expandVertically(animationSpec = GlassSpringIntSize) +
                fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = GlassSpringIntSize) +
                fadeOut(tween(180))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                moreEntries.chunked(cols).forEach { rowEntries ->
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
                                if (entry.route != null) onNavigate(entry.route) else onAccounting()
                            }
                        }
                        repeat(cols - rowEntries.size) { Spacer(Modifier.weight(1f)) }
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
        QuickEntryDef("tasks", Icons.Rounded.Checklist, "任务", ModuleTints.Tasks),
        QuickEntryDef("backpack", Icons.Rounded.Backpack, "背包", ModuleTints.Backpack),
        QuickEntryDef("notes", Icons.Rounded.EditNote, "笔记", ModuleTints.Notes),
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

/**
 * 最近动态：
 * - 顶部单选分类标签（全部 / 任务 / 成就 / 物品 / 记录），默认「全部」；
 *   「全部」按用户自定义的启用分类展示完整动态流（自定义动态恒显示）；
 * - 右上角「自定义」按钮 → BottomSheet 配置面板：勾选展示分类 / 设置显示条数 / 添加自定义动态；
 * - 底部「查看全部 N 条 ›」跳转完整动态列表页（AllActivitiesRoute）。
 * 启用分类（feedKinds）与条数（feedLimit）由 HomeViewModel 经 DataStore 持久化；
 * 标签选中态为轻量 UI 状态（默认「全部」，不持久化）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActsCard(
    allActs: List<ActivityEntity>,
    feedKinds: Set<String>,
    feedLimit: Int,
    onFeedKindsChange: (Set<String>) -> Unit,
    onFeedLimitChange: (Int) -> Unit,
    onAddCustom: (String) -> Unit,
    onOpenAll: () -> Unit = {}
) {
    val tabKinds = listOf(
        "all" to "全部",
        "task" to "任务",
        "ach" to "成就",
        "item" to "物品",
        "memo" to "记录"
    )
    val sheetKinds = listOf(
        "task" to "任务",
        "ach" to "成就",
        "item" to "物品",
        "memo" to "记录"
    )

    var tab by remember { mutableStateOf("all") }
    var showConfig by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    // v1.0.3：左右滑动切换分类（手势累计位移，超过阈值即切一格）
    var dragAcc by remember { mutableStateOf(0f) }

    // 筛选逻辑：具体标签 = 单独该类型（标签是显式意图，覆盖启用分类配置）；
    // 「全部」= 按启用分类展示，自定义动态恒显示
    val filtered = if (tab == "all") {
        allActs.filter { it.kind in feedKinds || it.kind == "custom" }
    } else {
        allActs.filter { it.kind == tab }
    }
    val shown = if (feedLimit > 0) filtered.take(feedLimit) else filtered

    // v1.0.7：全透明毛玻璃卡片 + 5% 黑遮罩（GlassCard）替代实心 Card，壁纸可透出
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(UiDimens.CardPad)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAcc = 0f },
                        onHorizontalDrag = { change, amount ->
                            dragAcc += amount
                            change.consume()
                        },
                        onDragEnd = {
                            val idx = tabKinds.indexOfFirst { it.first == tab }
                            when {
                                dragAcc < -90f && idx < tabKinds.size - 1 -> tab = tabKinds[idx + 1].first
                                dragAcc > 90f && idx > 0 -> tab = tabKinds[idx - 1].first
                            }
                        }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // weight 占满剩余宽度：大字号下标题换行也不会把右侧入口挤出屏幕
                SectionHeader("最近动态", Modifier.weight(1f), icon = Icons.Rounded.Bolt, chipColor = ModuleTints.Feed)
                // 可见性修复（第二轮）：纯图标 32dp 方块在琥珀底上仍不够醒目，
                // 改为「图标 + 文字」实心按钮，前景用 OnAmber（深棕），
                // 浅深主题对琥珀底都有 ≥4.5:1 对比度，且文字入口更易发现、易点中。
                Surface(
                    onClick = { showConfig = true },
                    color = AmberPrimary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = OnAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "自定义",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 单选分类标签（5 枚，横向可滚动防溢出）
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                tabKinds.forEach { (kind, label) ->
                    FilterChip(
                        selected = tab == kind,
                        onClick = { tab = kind },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            if (shown.isEmpty()) {
                EmptyHint(
                    if (tab == "all") "暂无动态，去完成任务或解锁成就试试"
                    else "该分类下暂无动态"
                )
            } else {
                shown.forEach { act ->
                    // v1.0.17：动态种类图标 emoji → 圆角矢量
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
                        Text(act.time.take(10), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    TextButton(onClick = onOpenAll, modifier = Modifier.fillMaxWidth()) {
                        Text("查看全部 ${filtered.size} 条动态 ›", color = AmberPrimary)
                    }
                }
            }
        }
    }

    // 自定义配置面板（BottomSheet）：启用分类 / 显示条数 / 添加自定义动态 / 全部动态入口
    if (showConfig) {
        ModalBottomSheet(onDismissRequest = { showConfig = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("自定义最近动态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // 1) 启用分类（多选；自定义动态恒显示）
                Text("要展示的分类", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sheetKinds.forEach { (kind, label) ->
                        val selected = kind in feedKinds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) feedKinds - kind else feedKinds + kind
                                // 至少保留一个分类，避免出现「全部隐藏」的死局面
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

                // 2) 显示条数
                Text("最多显示条数", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5 to "5 条", 10 to "10 条", 20 to "20 条", 0 to "全部").forEach { (n, label) ->
                        FilterChip(
                            selected = feedLimit == n,
                            onClick = { onFeedLimitChange(n) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // 3) 添加自定义动态 + 完整列表入口
                // 对比度修复：OutlinedButton 容器透明、与面板同色导致无法辨认。
                // 主操作用 AmberPrimary 实心填充 + 白字；次操作用琥珀描边 + 琥珀字，与背景拉开层次。
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showConfig = false; showAdd = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加自定义动态", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showConfig = false; onOpenAll() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AmberPrimary
                        ),
                        border = BorderStroke(1.dp, AmberPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("查看全部动态 ›", maxLines = 1)
                    }
                }
            }
        }
    }

    // 添加自定义动态对话框
    if (showAdd) {
        AddCustomActivityDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title ->
                onAddCustom(title)
                showAdd = false
            }
        )
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
    onDelete: (String) -> Unit
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
                IconButton(onClick = onAdd, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加里程碑", tint = AmberPrimary)
                }
            }
            if (items.isEmpty()) {
                EmptyHint("还没有重要事件，点右上角 + 记录一个里程碑吧")
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
                        if (isCustom) {
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
