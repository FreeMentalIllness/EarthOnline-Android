package com.example.earthonline.ui.report

import com.example.earthonline.ui.theme.GlassCard
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.AnimatedBarChart
import com.example.earthonline.ui.components.AnimatedLineChart
import com.example.earthonline.ui.components.MetricTile
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SectionHeader
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.util.XpRules
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon

@Composable
fun ReportRoute(
    moreActions: MoreMenuActions,
    startKind: ReportKind = ReportKind.DAY,
    vm: ReportViewModel = hiltViewModel()
) {
    // 数据页三个入口分别带 kind=day/week/year 进来，首屏就落在对应周期
    LaunchedEffect(startKind) { vm.selectKind(startKind) }
    ReportScreen(vm = vm, moreActions = moreActions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: ReportViewModel, moreActions: MoreMenuActions) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("周期报告") },
                actions = { }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(UiDimens.ScreenPad),
            verticalArrangement = Arrangement.spacedBy(UiDimens.Gap)
        ) {
            item { KindSwitcher(current = state.kind, onSelect = vm::selectKind) }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(UiDimens.CardRadius)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(UiDimens.CardPad),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(
                                state.kind.label,
                                icon = state.kind.icon,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                state.rangeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                value = state.tasksDone,
                                label = "完成任务",
                                hint = "+${XpRules.TASK_DONE}/个",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                value = state.memos,
                                label = "新增灵感",
                                hint = "+${XpRules.MEMO}/条",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                value = state.achievements,
                                label = "解锁成就",
                                hint = "+${XpRules.ACHIEVEMENT}/个",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                value = state.locations,
                                label = "新增足迹",
                                hint = "+${XpRules.LOCATION}/处",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("获得经验值", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "+${state.xp} XP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberPrimary
                            )
                        }
                    }
                }
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(UiDimens.CardRadius)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(UiDimens.CardPad),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeader(state.chartTitle)
                        /* 切周期时整块图表换掉：AnimatedContent 让旧图淡出、新图从右侧滑入，
                           比直接替换少一次「空白一帧」。图内部的渐入动画由各自 LaunchedEffect 触发。 */
                        AnimatedContent(
                            targetState = state,
                            transitionSpec = {
                                (fadeIn() + slideInHorizontally { it / 6 }).togetherWith(
                                    fadeOut() + slideOutHorizontally { -it / 6 }
                                )
                            },
                            label = "reportChart"
                        ) { s ->
                            if (s.chartMode == ChartMode.LINE) {
                                AnimatedLineChart(
                                    points = s.linePoints,
                                    labels = s.lineLabels,
                                    color = AmberPrimary,
                                    dotRing = MaterialTheme.colorScheme.surface
                                )
                            } else {
                                AnimatedBarChart(
                                    items = s.bars,
                                    color = AmberPrimary,
                                    topColor = AmberPrimary.copy(alpha = 0.55f),
                                    maxHeight = 150.dp
                                )
                            }
                        }
                    }
                }
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(UiDimens.CardRadius)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(UiDimens.CardPad),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionHeader("📝 一句话总结")
                        Text(
                            state.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (state.highlights.isNotEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(UiDimens.CardRadius)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(UiDimens.CardPad),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SectionHeader("✨ 本周期亮点")
                            state.highlights.forEach { h ->
                                Text(
                                    h,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 日报 / 周报 / 年报 三档切换 */
@Composable
private fun KindSwitcher(current: ReportKind, onSelect: (ReportKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ReportKind.values().forEach { kind ->
            FilterChip(
                selected = kind == current,
                onClick = { onSelect(kind) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(kind.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(kind.label)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberPrimary.copy(alpha = 0.22f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
