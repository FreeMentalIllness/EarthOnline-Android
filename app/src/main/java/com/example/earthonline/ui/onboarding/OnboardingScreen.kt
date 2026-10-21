package com.example.earthonline.ui.onboarding

import com.example.earthonline.ui.theme.GlassCard
import androidx.compose.foundation.background
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
            Spacer(Modifier.height(24.dp))

            // ---- 角色信息卡 ----
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FormSectionLabel("基础信息")

                    OutlinedTextField(
                        value = vm.name,
                        onValueChange = vm::onNameChange,
                        label = { Text("角色名 *") },
                        placeholder = { Text("你的角色名") },
                        singleLine = true,
                        isError = tried && vm.name.isBlank(),
                        supportingText = if (tried && vm.name.isBlank()) {
                            { Text(nameError) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LabeledDropdown(
                        label = "性别",
                        selected = vm.gender,
                        options = GENDER_OPTIONS,
                        onSelect = { vm.gender = it }
                    )

                    OutlinedTextField(
                        value = vm.birthDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("出生日期 *") },
                        placeholder = { Text("yyyy/mm/dd") },
                        isError = tried && vm.birthDate.isBlank(),
                        supportingText = if (tried && vm.birthDate.isBlank()) {
                            { Text(birthError) }
                        } else null,
                        trailingIcon = {
                            IconButton({ showDatePicker = true }) {
                                Icon(Icons.Rounded.DateRange, contentDescription = "选择日期")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

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
                        OutlinedTextField(
                            value = vm.province,
                            onValueChange = { vm.province = it.take(30) },
                            label = { Text("州 / 省") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()
                    FormSectionLabel("个性签名（选填）")
                    OutlinedTextField(
                        value = vm.signature,
                        onValueChange = { vm.signature = it.take(200) },
                        placeholder = { Text("一句话介绍自己") },
                        minLines = 2,
                        maxLines = 4,
                        supportingText = { Text("${vm.signature.length}/200") },
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
                            OutlinedTextField(
                                value = cf.label,
                                onValueChange = { vm.updateCustomField(idx, it, cf.value) },
                                label = { Text("字段名") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cf.value,
                                onValueChange = { vm.updateCustomField(idx, cf.label, it) },
                                label = { Text("值") },
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

            Spacer(Modifier.height(20.dp))

            // ---- 提交 ----
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
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.birthDate = millisToDayStr(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
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
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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
}
