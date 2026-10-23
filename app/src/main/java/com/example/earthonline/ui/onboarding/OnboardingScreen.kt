package com.example.earthonline.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.theme.GlassCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.local.entity.ProfileEntity
import com.example.earthonline.data.model.CustomField
import com.example.earthonline.data.repository.ProfileRepository
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.util.millisToDayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/* 选项常量提到文件级：写在 @Composable 体内会随每次重组重新分配 List */
/* v1.2.0：补充两个更离谱的答案（对应彩蛋成就「性别是流动的」） */
private val GENDER_OPTIONS = listOf(
    "" to "保密", "male" to "男", "female" to "女",
    "walmart" to "沃尔玛购物袋", "helicopter" to "直升机", "potato" to "土豆"
)

private val COUNTRY_OPTIONS = listOf(
    "中国", "中国香港", "中国澳门", "中国台湾",
    "美国", "日本", "英国", "加拿大", "澳大利亚", "新加坡", "德国", "法国", "其他"
)

private val PROVINCES = listOf(
    "北京", "上海", "广东", "江苏", "浙江", "四川", "湖北", "湖南", "山东", "河南",
    "福建", "陕西", "辽宁", "天津", "重庆", "河北", "安徽", "江西", "云南", "广西",
    "山西", "黑龙江", "吉林", "贵州", "甘肃", "内蒙古", "新疆", "海南", "宁夏", "青海", "西藏"
)

/** 首次引导页（对应 HTML account.js 的引导表单，仅一次性写入唯一主档） */
@Composable
fun OnboardingRoute(vm: OnboardingViewModel = hiltViewModel()) {
    OnboardingScreen(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(vm: OnboardingViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }   // 点过「开始人生」后才显示校验错误
    val scroll = rememberScrollState()

    val nameError = "请先给自己起一个角色名"
    val birthError = "请选择出生日期（等级 = 年龄）"

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- 品牌区 ----
            EnterGroup(0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(color = AmberPrimary.copy(alpha = 0.14f), shape = CircleShape) {
                        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Public, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(64.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "欢迎来到地球Online",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "先设定你的角色，1 分钟内开始记录人生。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // ---- 角色信息卡 ----
            EnterGroup(1) {
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FormSectionLabel("基础信息")

                    GlassTextField(
                        value = vm.name,
                        onValueChange = vm::onNameChange,
                        label = "角色名 *",
                        placeholder = "你的角色名",
                        singleLine = true,
                        isError = tried && vm.name.isBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LabeledDropdown(
                        label = "性别",
                        selected = vm.gender,
                        options = GENDER_OPTIONS,
                        onSelect = { vm.gender = it }
                    )

                    /* v1.0.6.15（体验巡检修复）：出生日期字段整体可点。
                       原 readOnly 字段点文本区会抢焦点弹出**无法输入**的键盘（占位 yyyy/mm/dd
                       只是展示文本），真实用户会以为坏了；只有右下角小日历图标才弹选择器。
                       现在整字段盖一层透明点击层 → 点任意位置直接弹日期选择器，键盘不再出现。 */
                    Box {
                        GlassTextField(
                            value = vm.birthDate,
                            onValueChange = {},
                            readOnly = true,
                            label = "出生日期 *",
                            placeholder = "yyyy/mm/dd",
                            isError = tried && vm.birthDate.isBlank(),
                            trailingIcon = {
                                IconButton({ showDatePicker = true }) {
                                    Icon(Icons.Rounded.DateRange, contentDescription = "选择日期")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showDatePicker = true }
                        )
                    }

                    LabeledDropdown(
                        label = "选择区服 · 国家",
                        selected = vm.country,
                        options = COUNTRY_OPTIONS.map { it to it },
                        onSelect = { vm.country = it }
                    )

                    if (vm.country == "中国") {
                        LabeledDropdown(
                            label = "选择区服 · 省份",
                            selected = vm.province,
                            options = listOf("" to "请选择省份") + PROVINCES.map { it to it },
                            onSelect = { vm.province = it }
                        )
                    } else {
                        GlassTextField(
                            value = vm.province,
                            onValueChange = { vm.province = it.take(30) },
                            label = "州 / 省",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()
                    FormSectionLabel("个性签名（选填）")
                    GlassTextField(
                        value = vm.signature,
                        onValueChange = { vm.signature = it.take(200) },
                        placeholder = "一句话介绍自己",
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormSectionLabel("自定义字段", Modifier.weight(1f))
                        Text(
                            "${vm.customFields.size}/20",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    vm.customFields.forEachIndexed { idx, cf ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GlassTextField(
                                value = cf.label,
                                onValueChange = { vm.updateCustomField(idx, it, cf.value) },
                                label = "字段名",
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            GlassTextField(
                                value = cf.value,
                                onValueChange = { vm.updateCustomField(idx, cf.label, it) },
                                label = "值",
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { vm.removeCustomField(idx) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "删除字段")
                            }
                        }
                    }
                    if (vm.customFields.size < 20) {
                        OutlinedButton(onClick = vm::addCustomField, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加自定义字段")
                        }
                    }
                    Text(
                        "例如：昵称、职业、城市。字段按添加顺序展示，名称 ≤30 字，值 ≤200 字。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }

            Spacer(Modifier.height(20.dp))

            // ---- 提交 ----
            EnterGroup(2) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = {
                    tried = true
                    if (vm.canSave) vm.save()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("开始人生", style = MaterialTheme.typography.titleMedium)
            }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "角色名与出生日期为必填项；所有数据只保存在本机。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // v1.0.6（b51）：右上角「跳过」——不想现在填资料的用户一键直达主页。
        // 不写任何资料，只标记 onboarding_done=true（Root 的分流数据流自动切换），
        // 资料以后可随时在「设置 → 个人资料」补填，AI 也会诚实地以「尚未填写资料」应对。
        TextButton(
            onClick = { vm.skip() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 4.dp, end = 8.dp)
        ) {
            Text(
                "跳过",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            selected = vm.birthDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { vm.birthDate = millisToDayStr(it) }
        )
    }
}

/**
 * 出生日期选择器（v1.0.6.8 性能专项）。
 *
 * 卡顿根因：Material3 的 [DatePicker] 默认年份区间是 **1900 ~ 2100（201 项）**，
 * 切到「选择年份」时会一次性布局 201 个条目；同时默认展示月是当前月，
 * 用户得一路翻到自己出生年份，翻页过程中整颗月历的 Compose 树跟着重算，
 * 中低端机上就是明显的掉帧。
 *
 * 优化三件事：
 *  ① [yearRange] 收紧到 1950 ~ 今年（默认 201 项 → 约 77 项），列表规模砍到 1/3；
 *  ② [initialDisplayedMonthMillis] 直接定位到「今年 - 25 年」，90% 用户一步到位，
 *     不必连续翻页（翻页才是掉帧的来源）；
 *  ③ [SelectableDates] 禁止选择未来日期，从状态层消除非法滚动区间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    selected: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val today = remember { java.util.Calendar.getInstance() }
    // 今天零点（用于高亮「今天」与限制未来日期）
    val todayStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val yearRange = remember { 1950..today.get(java.util.Calendar.YEAR) }
    /* v1.0.6.9：初始选中 = **今天**（用户明确要求），不再是「今年 -25 年」。
       日历打开即定位到当前年月，今天被高亮；用户再往上翻到自己的出生年份。
       PastOnlyDates 同时禁掉未来日期，翻不出非法区间。 */
    val initialSelected = remember(selected) {
        runCatching {
            val p = selected.split("/")
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, p[0].toInt())
                set(java.util.Calendar.MONTH, p[1].toInt() - 1)
                set(java.util.Calendar.DAY_OF_MONTH, p[2].toInt())
            }.timeInMillis
        }.getOrNull()
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialSelected ?: todayStart,
        initialDisplayedMonthMillis = initialSelected ?: todayStart,
        yearRange = yearRange,
        selectableDates = remember { PastOnlyDates(today.timeInMillis) }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onConfirm)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    ) { DatePicker(state = state) }
}

/** 只允许选择今天及以前的日期 */
@OptIn(ExperimentalMaterial3Api::class)
private class PastOnlyDates(private val todayUtcMillis: Long) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
}

/* v1.0.6.8：进场动效 —— 淡入 + 从底部轻微上滑。
   三个分组错峰 70ms 依次进场，观感像「整页抬起来」而不是一堆控件同时闪出来。
   动效只跑一次（remember 的 visible 一旦为 true 就不再变化），后续输入/选中不会重放。 */
@Composable
private fun EnterGroup(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val spec = tween<Float>(durationMillis = 380, delayMillis = index * 70)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spec) + slideInVertically(
            animationSpec = tween(durationMillis = 380, delayMillis = index * 70),
            initialOffsetY = { it / 5 }
        )
    ) { content() }
}

@Composable
private fun FormSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        GlassTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            label = label,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    var name by mutableStateOf("")
    var gender by mutableStateOf("")
    var birthDate by mutableStateOf("")
    var country by mutableStateOf("中国")
    var province by mutableStateOf("")
    var signature by mutableStateOf("")
    var customFields by mutableStateOf(listOf<CustomField>())

    val canSave: Boolean
        get() = name.isNotBlank() && birthDate.isNotBlank()

    fun onNameChange(v: String) { name = v.take(30) }

    fun addCustomField() {
        if (customFields.size < 20) customFields = customFields + CustomField(id = uid("cf"))
    }

    fun updateCustomField(idx: Int, label: String, value: String) {
        val list = customFields.toMutableList()
        if (idx in list.indices) list[idx] = list[idx].copy(
            label = label.take(30), value = value.take(200)
        )
        customFields = list
    }

    fun removeCustomField(idx: Int) {
        customFields = customFields.toMutableList().also { it.removeAt(idx) }
    }

    fun save() {
        viewModelScope.launch {
            val profile = ProfileEntity(
                name = name.trim(),
                gender = gender,
                country = country,
                province = province,
                signature = signature.take(200),
                birthDate = birthDate,
                customFieldsJson = Json.encodeToString(
                    ListSerializer(CustomField.serializer()),
                    customFields.filter { it.label.isNotBlank() || it.value.isNotBlank() }
                )
            )
            profileRepo.upsert(profile)
            settings.setOnboardingDone(true)
        }
    }

    /** v1.0.6：跳过引导 —— 不写任何资料直接进主页，资料以后可在设置里补 */
    fun skip() = viewModelScope.launch { settings.setOnboardingDone(true) }
}
