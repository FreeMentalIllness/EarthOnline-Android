package com.example.earthonline.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.earthonline.data.model.CustomField
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.CropShape
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.ImageCropperDialog
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.GlassCard
import com.example.earthonline.ui.components.UserAvatar
import com.example.earthonline.util.IMAGE_TYPES_HINT
import com.example.earthonline.util.ImageStore
import com.example.earthonline.util.ShareCardRenderer
import com.example.earthonline.util.ageFromBirthDate
import com.example.earthonline.util.millisToDayStr
import com.example.earthonline.util.rememberImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* v1.0.6.6（死命令 #2）：彻底清除默认 Emoji 头像预设。
   更换头像弹窗只保留「从相册上传图片」与「重置为默认头像（极简 User 轮廓矢量图标）」两项，
   不再提供任何 Emoji 预设选项。 */

private val PROVINCES = listOf(
    "北京", "上海", "广东", "江苏", "浙江", "四川", "湖北", "湖南", "山东", "河南",
    "福建", "陕西", "辽宁", "天津", "重庆", "河北", "安徽", "江西", "云南", "广西",
    "山西", "黑龙江", "吉林", "贵州", "甘肃", "内蒙古", "新疆", "海南", "宁夏", "青海", "西藏"
)


@Composable
fun ProfileRoute(moreActions: MoreMenuActions, vm: ProfileViewModel = hiltViewModel()) =
    ProfileScreen(vm = vm, moreActions = moreActions)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: ProfileViewModel, moreActions: MoreMenuActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val profile by vm.profile.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(profile?.customFieldsJson) {
        if (profile != null) vm.load(profile!!)
    }

    var editing by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    /* v1.2.4：头像导入改为「先选图 → 裁剪框缩放/移动 → 确认」。
       裁剪输出为无损 PNG（见 ui/components/ImageCropper.kt），不再整图塞进圆形。 */
    var showCrop by remember { mutableStateOf(false) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }

    /* v1.0.0：分享卡片状态。
       卡片位图只在生成过程中短暂存在：画完立刻写文件，然后回收位图，
       预览用 Coil 读文件（避免一张 1080×1440 的 ARGB 位图常驻内存 ≈ 6MB）。 */
    var cardGenerating by remember { mutableStateOf(false) }
    var cardFile by remember { mutableStateOf<java.io.File?>(null) }
    // v1.0.3：分享卡片模板选择（1 暖米 / 2 深夜 / 3 樱粉）
    var showTemplatePicker by remember { mutableStateOf(false) }

    fun generateCard(template: Int) {
        if (cardGenerating) return
        cardGenerating = true
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                val bmp = ShareCardRenderer.render(vm.buildCardData(template), vm.avatarFile())
                try {
                    withContext(Dispatchers.IO) { ShareCardRenderer.saveToCache(context, bmp) }
                } finally {
                    bmp.recycle()
                }
            }
            cardGenerating = false
            if (file == null) snackbar.showSnackbar("生成卡片失败，请重试")
            else cardFile = file
        }
    }

    /* v1.0.0：头像导入 —— **原图完整保存**。
       走统一选择入口（Photo Picker 优先 + 持久化读权限），把原图字节原封不动复制进
       filesDir/avatar（不解码、不缩放、不重编码）：
         · 不解码 -> 4K 图也不会有全尺寸位图的内存尖峰，不会 OOM；
         · 不重编码 -> 画质 100% 保留，不会像上一版那样被压糊。
       显示端交给 Coil 按控件尺寸采样（见 ui/components/Avatar.kt），内存与画质两头都要。
       复制全程在 IO 线程，不占主线程。 */
    val pickAvatar = rememberImagePicker(onPicked = { uri ->
        cropUri = uri
        showCrop = true
    })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("个人资料") },
                actions = {
                    if (editing) {
                        TextButton(onClick = { vm.save(); editing = false }) { Text("保存") }
                    } else {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        GlassCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            shape = RoundedCornerShape(UiDimens.CardRadius)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 头像
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UserAvatar(vm.avatarPath, vm.avatarData, vm.avatarKey, 80.dp)
                Column {
                    Text(
                        vm.name.takeIf { it.isNotBlank() } ?: "未命名角色",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val age = ageFromBirthDate(vm.birthDate)
                    if (age != null) {
                        Text("Lv.$age · ${vm.genderLabel()} · ${vm.regionLabel()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (editing) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showAvatarPicker = true }) { Text("更换头像") }
                    }
                }
            }

            HorizontalDivider()

            if (editing) {
                GlassTextField(vm.name, { vm.name = it.take(30) }, label = "角色名",
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                GenderDropdown(vm.gender) { vm.gender = it }
                GlassTextField(vm.country, { vm.country = it.take(30) }, label = "区服 · 国家/地区",
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (vm.country == "中国") {
                    ProvinceDropdown(vm.province) { vm.province = it }
                } else {
                    GlassTextField(vm.province, { vm.province = it.take(30) }, label = "省份/州",
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                GlassTextField(vm.birthDate, {}, readOnly = true, label = "出生日期",
                    trailingIcon = { IconButton({ showDatePicker = true }) { Icon(Icons.Rounded.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth())
                GlassTextField(vm.signature, { vm.signature = it }, label = "个性签名",
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                GlassTextField(vm.customTitle, { vm.customTitle = it.take(12) },
                    label = "称号（可选，如「星尘旅人」）",
                    placeholder = "留空显示默认「旅行者」",
                    singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("自定义字段", style = MaterialTheme.typography.titleMedium)
                vm.customFields.forEachIndexed { idx, cf ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTextField(cf.label, { vm.updateCustomField(idx, it, cf.value) }, label = "字段名",
                            singleLine = true, modifier = Modifier.weight(1f))
                        GlassTextField(cf.value, { vm.updateCustomField(idx, cf.label, it) }, label = "字段值",
                            singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeCustomField(idx) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除")
                        }
                    }
                }
                if (vm.customFields.size < 20) {
                    OutlinedButton(onClick = vm::addCustomField, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("添加自定义字段")
                    }
                }
            } else {
                ProfileInfoRow("角色名", vm.name.takeIf { it.isNotBlank() } ?: "—")
                ProfileInfoRow("性别", vm.genderLabel())
                ProfileInfoRow("区服", vm.regionLabel())
                ProfileInfoRow("出生日期", vm.birthDate.takeIf { it.isNotBlank() } ?: "—")
                ProfileInfoRow("个性签名", vm.signature.takeIf { it.isNotBlank() } ?: "—")
                ProfileInfoRow("称号", com.example.earthonline.util.XpRules.titleFor(vm.customTitle))
                if (vm.customFields.any { it.label.isNotBlank() && it.value.isNotBlank() }) {
                    Text("自定义字段", style = MaterialTheme.typography.titleMedium)
                    vm.customFields.forEach { cf ->
                        if (cf.label.isNotBlank() && cf.value.isNotBlank()) {
                            ProfileInfoRow(cf.label, cf.value)
                        }
                    }
                }
                HorizontalDivider()
                /* v1.0.0：人生分享卡片。
                   把等级 / 成就 / 任务 / 签名 / 头像画成一张 1080×1440 的图，
                   走系统分享（微信、QQ、保存图片…都能接）。 */
                Button(
                    onClick = { showTemplatePicker = true },
                    enabled = !cardGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (cardGenerating) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("生成分享卡片")
        }
                }
                Text(
                    "把当前等级、成就数量、个性签名和头像渲染成一张图，可以分享给朋友或存进相册。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
    }

    if (showAvatarPicker) {
        AnimatedAlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            title = { Text("选择头像") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    OutlinedButton(onClick = { showAvatarPicker = false; pickAvatar() },
                        modifier = Modifier.fillMaxWidth()) { Text("从相册上传图片") }
                    Text(
                        "支持 $IMAGE_TYPES_HINT，原图完整保存不压缩；显示时按头像大小自动采样。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // v1.0.0：导入失败 / 想换回地球图标时的退路，别让用户卡在一张坏图上。
                    // resetAvatar 会把**原图文件路径也清掉**（只清预设 key 的话，上传的头像还留着）。
                    OutlinedButton(
                        onClick = {
                            vm.resetAvatar()
                            showAvatarPicker = false
                            scope.launch { snackbar.showSnackbar("已重置为默认头像（保存后生效）") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重置为默认头像") }
                }
            },
            confirmButton = { TextButton(onClick = { showAvatarPicker = false }) { Text("取消") } }
        )
    }

    // v1.0.0：分享卡片预览 —— 分享 / 保存到相册 / 关闭
    cardFile?.let { file ->
        AnimatedAlertDialog(
            onDismissRequest = { cardFile = null },
            title = { Text("人生分享卡片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    coil.compose.AsyncImage(
                        model = file,
                        contentDescription = "分享卡片预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    Text(
                        "分享出去的只有这张图，不会带任何其它数据。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = ShareCardRenderer.shareIntent(context, file)
                    if (intent != null) {
                        runCatching { context.startActivity(intent) }
                            .onFailure { scope.launch { snackbar.showSnackbar("没有可用的分享应用") } }
                    } else {
                        scope.launch { snackbar.showSnackbar("分享失败：无法读取卡片文件") }
                    }
                }) { Text("分享") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val ok = runCatching {
                            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            val saved = ShareCardRenderer.saveToGallery(
                                context, bmp, "earth_card_${System.currentTimeMillis()}.png"
                            )
                            bmp.recycle()
                            saved
                        }.getOrDefault(false)
                        scope.launch {
                            snackbar.showSnackbar(if (ok) "已保存到相册（Pictures/地球Online）" else "保存失败，可能缺少存储权限")
                        }
                    }) { Text("存相册") }
                    TextButton(onClick = { cardFile = null }) { Text("关闭") }
                }
            }
        )
    }

    // v1.0.3：分享卡片模板选择（生成前先挑一套配色）
    if (showTemplatePicker) {
        AnimatedAlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            title = { Text("选择卡片模板") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(1, "暖米 · 治愈系（默认）", 0xFFD4A373),
                        Triple(2, "深夜 · 暗色限定", 0xFFE0B589),
                        Triple(3, "樱粉 · 春日限定", 0xFFD98BA4)
                    ).forEach { (tpl, label, accent) ->
                        Surface(
                            onClick = {
                                showTemplatePicker = false
                                generateCard(tpl)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(accent))
                                )
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTemplatePicker = false }) { Text("取消") } }
        )
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

    if (showCrop && cropUri != null) {
        ImageCropperDialog(
            uri = cropUri!!,
            shape = CropShape.Circle,
            outputDir = ImageStore.avatarDir(context),
            prefix = "avatar",
            onConfirm = { file ->
                vm.setUploadedAvatarPath(file.absolutePath)
                ImageStore.clearDirExcept(ImageStore.avatarDir(context), file)
                showCrop = false
                cropUri = null
                scope.launch {
                    snackbar.showSnackbar(
                        "头像已更新（裁剪无损 · ${ImageStore.prettySize(file.length())}，保存后生效）"
                    )
                }
            },
            onDismiss = { showCrop = false; cropUri = null }
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderDropdown(selected: String, onSelect: (String) -> Unit) {
    // v1.2.0：沃尔玛购物袋（彩蛋）+ 两个更离谱的答案（对应彩蛋「性别是流动的」）
    val options = listOf(
        "" to "保密", "male" to "男", "female" to "女",
        "walmart" to "沃尔玛购物袋", "helicopter" to "直升机", "potato" to "土豆"
    )
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        GlassTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "保密",
            onValueChange = {}, readOnly = true, label = "性别",
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvinceDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        GlassTextField(
            value = selected, onValueChange = {}, readOnly = true, label = "省份",
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PROVINCES.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = { onSelect(p); expanded = false })
            }
        }
    }
}

private fun ProfileViewModel.genderLabel(): String = when (gender) {
    "male" -> "男"
    "female" -> "女"
    "walmart" -> "沃尔玛购物袋"
    "helicopter" -> "直升机"
    "potato" -> "土豆"
    else -> "保密"
}

private fun ProfileViewModel.regionLabel(): String =
    if (country.isBlank()) "—" else if (province.isBlank()) country else "$country · $province"
