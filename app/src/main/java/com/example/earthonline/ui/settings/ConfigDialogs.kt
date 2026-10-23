package com.example.earthonline.ui.settings

import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.LocalModelConfig
import com.example.earthonline.data.network.WebDavConfig
import com.example.earthonline.data.sync.CloudSyncManager
import com.example.earthonline.ui.ai.LocalLlmRuntime
import com.example.earthonline.ui.ai.LocalLlmSupport
import com.example.earthonline.ui.ai.LocalModelManager
import com.example.earthonline.ui.ai.MockLlmEngine
import com.example.earthonline.ui.ai.ModelDownloader
import com.example.earthonline.ui.ai.ModelVariant
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.GlassTextField
import kotlinx.coroutines.launch
import java.io.File

/** 本地模型下载的 UI 状态（进度条直接消费） */
private sealed interface ModelDownloadUi {
    data object Idle : ModelDownloadUi
    data class Running(val file: String, val percent: Int) : ModelDownloadUi
    data class Done(val message: String) : ModelDownloadUi
    data class Failed(val message: String) : ModelDownloadUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigDialog(
    initial: AiConfig,
    localModel: LocalModelConfig = LocalModelConfig(),
    onDismiss: () -> Unit,
    onSave: (AiConfig, LocalModelConfig) -> Unit
) {
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var runMode by remember { mutableStateOf(initial.runMode.ifBlank { "cloud" }) }

    var local by remember { mutableStateOf(localModel) }
    var portText by remember { mutableStateOf(local.port.toString()) }

    val context = LocalContext.current
    var discovered by remember { mutableStateOf(LocalModelManager.scan(context)) }
    // 说明入口：默认关闭；本地模式下才由用户主动点开，不占据主版面
    var showGuide by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = LocalModelManager.importFromUri(context, uri, "plugin_${System.currentTimeMillis()}")
            ?: return@rememberLauncherForActivityResult
        discovered = discovered + name
        local = local.copy(
            pluginName = name,
            pluginPath = LocalModelManager.pluginsDir(context).resolve("$name.zip").absolutePath,
            discovered = discovered
        )
    }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    AiConfig(baseUrl.trim(), apiKey.trim(), model.trim(), runMode),
                    local.copy(port = portText.toIntOrNull() ?: LocalModelManager.DEFAULT_PORT)
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = {
            /* 「使用说明」收进标题栏右侧的小图标 —— 不再作为正文里的独立按钮出现，
               既不打断表单节奏，又保证随时能找到。 */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI 对话配置", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showGuide = true }) {
                    Icon(Icons.Rounded.Info, contentDescription = "使用说明")
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 三态开关：关闭 / 云端模型 / 本地模型（DataStore 持久化 runMode）
                Text("模型运行位置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                /* v1.0.6.9：**不再均分 width**。之前三枚 chip 各占 1/3，文字稍长就被
                   Ellipsis 吃成「云端…」「本地…」。现在按内容自适应宽度 + 居中铺开，
                   四个字必然完整显示；实在极窄的屏幕才会用 Arrangement 压缩间距。 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeChip("关闭", runMode == "off") { runMode = "off" }
                    ModeChip("云端模型", runMode == "cloud") { runMode = "cloud" }
                    ModeChip("本地模型", runMode == "local") { runMode = "local" }
                }
                HintLine(modeSummary(runMode), MaterialTheme.colorScheme.onSurfaceVariant)

                when (runMode) {
                    "off" -> Unit
                    "cloud" -> {
                        GlassTextField(baseUrl, { baseUrl = it }, label = "Base URL",
                            placeholder = "https://.../v1", singleLine = true, modifier = Modifier.fillMaxWidth())
                        GlassTextField(apiKey, { apiKey = it }, label = "API Key",
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        GlassTextField(model, { model = it }, label = "模型名（必填）",
                            placeholder = "如 deepseek-chat / qwen-plus", singleLine = true, modifier = Modifier.fillMaxWidth())
                        HintLine("兼容 OpenAI / 类 OpenAI 接口；模型名照抄服务商文档。", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        LocalModelPanel(
                            local = local,
                            portText = portText,
                            onPortChange = { portText = it },
                            discovered = discovered,
                            onDiscoveredChange = { discovered = it },
                            onLocalChange = { local = it },
                            onImport = { importLauncher.launch("*/*") },
                            onOpenGuide = { showGuide = true }
                        )
                    }
                }
            }
        }
    )

    if (showGuide) LocalModelGuide(onDismiss = { showGuide = false })
}

/**
 * 通用 chip：单行 + 不换行。**截断只在真的放不下时发生** —— 三态开关那行按内容
 * 自适应宽度，四个字必然完整；模型规格那行用 weight 平分，长文案才需要 Ellipsis。
 */
@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium
            )
        }
    )
}

/** 当前运行模式的一句话说明（单行截断） */
private fun modeSummary(mode: String): String = when (mode) {
    "off" -> "已关闭：不发起任何请求，提交内容按原文存为笔记"
    "local" -> "本地模型：完全离线推理，数据不出本机"
    else -> "云端模型：走你自己的 OpenAI 兼容接口"
}

@Composable
private fun LocalModelPanel(
    local: LocalModelConfig,
    portText: String,
    onPortChange: (String) -> Unit,
    discovered: List<String>,
    onDiscoveredChange: (List<String>) -> Unit,
    onLocalChange: (LocalModelConfig) -> Unit,
    onImport: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadUi by remember { mutableStateOf<ModelDownloadUi>(ModelDownloadUi.Idle) }
    var loadMsg by remember { mutableStateOf<String?>(null) }
    /* 模型规格：默认按本机总内存选 —— ≥6GB 才给 1.5B，其余一律 0.5B。
       1GB 权重的模型在 4GB 机上加载后极易 OOM / 被系统杀进程，必须让用户显式选择。 */
    var variant by remember {
        mutableStateOf(if (LocalLlmRuntime.memory(context).second >= 6L * 1024 * 1024 * 1024) ModelVariant.BASE else ModelVariant.SMALL)
    }

    val modelImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "model.zip"
        scope.launch {
            loadMsg = "正在导入并解包模型…"
            val dir = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                LocalModelManager.importModelPackage(context, uri, name)
            }
            if (dir == null) {
                loadMsg = "模型导入失败，请重试"
            } else {
                val (ok, msg) = LocalModelManager.validateModelDir(dir)
                if (ok) {
                    val size = dir.listFiles()?.sumOf { it.length() } ?: 0L
                    onLocalChange(
                        local.copy(
                            downloaded = true,
                            modelDir = dir.absolutePath,
                            sizeBytes = size,
                            modelName = dir.name
                        )
                    )
                    loadMsg = msg
                } else {
                    loadMsg = msg
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        /* v1.0.6.9：正文里不再出现「使用说明」文字按钮 —— 说明入口只保留弹窗标题栏
           右上角那一个 i 图标，避免同一功能出现两处、把版面顶长。 */
        Text(
            "离线模型 · llama.cpp",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        /* —— 设备体检：本机能不能跑本地推理，先说清楚（内存统一 GB/MB 口径） —— */
        val support = remember { LocalLlmRuntime.diagnose() }
        val (avail, total) = LocalLlmRuntime.memory(context)
        val memText = remember(avail, total) {
            if (total > 0) "可用 ${LocalLlmRuntime.formatBytes(avail)} / 共 ${LocalLlmRuntime.formatBytes(total)}"
            else "内存信息不可用"
        }
        AssistChip(
            onClick = {},
            label = {
                Text(
                    "${abbrSupport(support)} · $memText",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        )

        /* —— 状态卡片：当前模型 / 引擎状态，单行截断 —— */
        val statusText = when {
            // 只有真的 ready 才敢说「已加载」。历史上这里对 Mock 也显示过「已加载」，
            // 结果用户看到「引擎已加载」但一问就崩 —— 状态必须和事实一致。
            LocalLlmRuntime.engine.ready -> "引擎已加载：${LocalLlmRuntime.engine.name}"
            support is LocalLlmSupport.Blocked -> "模拟模式：本机不能跑真实推理"
            local.downloaded -> "模型文件已就绪（未加载），点「加载模型」"
            else -> "尚未准备模型：请先下载或导入 GGUF"
        }
        AssistChip(
            onClick = {},
            label = { Text(statusText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        )

        /* —— 模型规格：体积直接决定会不会 OOM —— */
        Text("模型规格", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("轻量 0.5B · 360MB", variant == ModelVariant.SMALL, Modifier.weight(1f)) { variant = ModelVariant.SMALL }
            ModeChip("标准 1.5B · 1GB", variant == ModelVariant.BASE, Modifier.weight(1f)) { variant = ModelVariant.BASE }
        }
        HintLine("内存 ≤ 6GB 的机型请用轻量版，标准版加载后容易被系统回收。", MaterialTheme.colorScheme.onSurfaceVariant)

        /* —— 在线下载：魔搭 Qwen 官方 GGUF 直链 + 断点续传 + 进度条 —— */
        when (val st = downloadUi) {
            is ModelDownloadUi.Running -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(progress = { st.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "下载中 ${st.percent}% · ${st.file}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            is ModelDownloadUi.Done -> HintLine(st.message, MaterialTheme.colorScheme.primary)
            is ModelDownloadUi.Failed -> HintLine(st.message, MaterialTheme.colorScheme.error)
            ModelDownloadUi.Idle -> Unit
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (downloadUi is ModelDownloadUi.Running) return@Button
                    downloadUi = ModelDownloadUi.Running("", 0)
                    scope.launch {
                        ModelDownloader.downloadAll(context, variant) { p ->
                            downloadUi = ModelDownloadUi.Running(p.file, p.percent)
                        }.onSuccess { dir ->
                            val (ok, msg) = LocalModelManager.validateModelDir(dir)
                            if (ok) {
                                val size = dir.listFiles()?.sumOf { it.length() } ?: 0L
                                onLocalChange(
                                    local.copy(
                                        downloaded = true,
                                        modelDir = dir.absolutePath,
                                        sizeBytes = size,
                                        modelName = variant.modelName
                                    )
                                )
                                downloadUi = ModelDownloadUi.Done(msg)
                            } else {
                                downloadUi = ModelDownloadUi.Failed(msg)
                            }
                        }.onFailure { t ->
                            downloadUi = ModelDownloadUi.Failed("下载失败：${t.message ?: "未知错误"}（可重试续传）")
                        }
                    }
                },
                enabled = downloadUi !is ModelDownloadUi.Running,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (local.downloaded) "重下 / 校验" else "下载模型", maxLines = 1)
            }
            OutlinedButton(
                onClick = { modelImportLauncher.launch("*/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text("导入模型", maxLines = 1)
            }
        }

        /* —— 引擎加载：加载前体检 + IO 协程 + 单例引擎（绝不重复 new 上下文） —— */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val dir = File(local.modelDir.ifBlank {
                            LocalModelManager.modelDir(context, variant.modelName).absolutePath
                        })
                        loadMsg = "正在检查设备与模型文件…"

                        // ① 模型文件存在性 + 完整性（半截文件是最常见的「加载失败」原因）
                        val gguf = dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
                            ?.maxByOrNull { it.length() }
                        if (gguf == null) {
                            loadMsg = "加载失败：目录里没有 .gguf 文件，请先下载或导入模型"
                            return@launch
                        }
                        if (gguf.length() < 32L * 1024 * 1024) {
                            loadMsg = "加载失败：模型文件只有 ${LocalLlmRuntime.formatBytes(gguf.length())}，" +
                                "明显不完整（下载中断或被截断），请点「重下 / 校验」重新下载"
                            return@launch
                        }

                        // ② 设备体检：不过关就**零 JNI 调用**（native 崩溃 Java 层拦不住）
                        when (val diag = LocalLlmRuntime.diagnose(dir)) {
                            is LocalLlmSupport.Blocked -> {
                                loadMsg = "本机无法运行本地模型：${diag.short}"
                                Log.w("EarthOnline-LLM", "load blocked: ${diag.detail}")
                                return@launch
                            }
                            else -> Unit
                        }

                        // ③ 真实加载（IO 协程，内部已 catch Throwable + OOM）
                        loadMsg = "正在加载 ${gguf.name}（${LocalLlmRuntime.formatBytes(gguf.length())}）…"
                        val engine = LocalLlmRuntime.engineFor(dir)
                        if (engine === MockLlmEngine) {
                            loadMsg = "本机只支持模拟模式：真实推理需要 arm64 真机（无 arm64 原生库或运行在模拟器）"
                            return@launch
                        }
                        val ok = engine.load(dir.absolutePath) { step -> loadMsg = step }
                        if (ok) {
                            LocalLlmRuntime.activate(engine)
                            loadMsg = "已加载 ${engine.name}，离线推理就绪"
                        } else {
                            // load() 内部已把具体原因（OOM / 文件损坏 / 原生库缺失）写到 loadMsg
                            loadMsg = loadMsg?.takeIf { it.contains("失败") || it.contains("不足") || it.contains("不可用") }
                                ?: "加载失败：引擎未能初始化（可尝试更小的量化模型，或切换云端）"
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("加载模型", maxLines = 1) }
            OutlinedButton(
                onClick = {
                    LocalLlmRuntime.engine.release()
                    LocalLlmRuntime.activate(MockLlmEngine)
                    loadMsg = "已卸载本地模型，释放内存"
                },
                modifier = Modifier.weight(1f)
            ) { Text("卸载释放", maxLines = 1) }
        }
        loadMsg?.let { HintLine(it, MaterialTheme.colorScheme.onSurfaceVariant) }

        /* —— 旧能力（默认收起）：本机自建 OpenAI 兼容服务端口 / .zip 插件 —— */
        var showAdvanced by remember { mutableStateOf(false) }
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "收起高级选项" else "高级选项", maxLines = 1)
        }
        if (showAdvanced) {
            GlassTextField(
                value = portText,
                onValueChange = { onPortChange(it.filter { c -> c.isDigit() }.take(5)) },
                label = "本地服务端口",
                placeholder = "${LocalModelManager.DEFAULT_PORT}",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("导入插件（.zip）", maxLines = 1)
            }
            if (discovered.isNotEmpty()) {
                Text("已发现插件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                discovered.forEach { name ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = local.pluginName == name,
                            onClick = {
                                onLocalChange(
                                    local.copy(
                                        pluginName = name,
                                        pluginPath = LocalModelManager.pluginsDir(context).resolve("$name.zip").absolutePath
                                    )
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** 体检结论的极短摘要（用于 chip，完整文案在说明页里） */
private fun abbrSupport(support: LocalLlmSupport): String = when (support) {
    LocalLlmSupport.Ready -> "支持本地推理"
    is LocalLlmSupport.Blocked -> "不支持本地推理"
}

/**
 * 本地模型「使用说明」二级页（v1.0.6.8 重做）。
 *
 * 排版规范：
 *  - 分段卡片式结构（准备 → 步骤 → 性能 → 排障），每段一个标题 + 要点列表；
 *  - 统一 [GuideSection] / [GuideBullet]，行距、字号、颜色全部走主题，不再有裸 Text 堆砌；
 *  - 标题区 + 灰阶正文，AlertDialog 圆角 / 半透明底板与全局液态玻璃观感一致；
 *  - 顶部实时显示本机体检结果，用户一眼知道自己能不能用。
 */
@Composable
private fun LocalModelGuide(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val support = remember { LocalLlmRuntime.diagnose() }
    val (avail, total) = LocalLlmRuntime.memory(context)
    val abi = remember { android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown" }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        title = {
            Text(
                "本地模型使用说明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "本地模式在你的手机上直接跑 GGUF 大模型，全程离线，提问内容不出本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                GuideSection("一、先确认本机能不能跑") {
                    GuideBullet("处理器架构", abi)
                    GuideBullet(
                        "内存",
                        if (total > 0) "可用 ${LocalLlmRuntime.formatBytes(avail)} / 共 ${LocalLlmRuntime.formatBytes(total)}" else "读取失败"
                    )
                    GuideBullet("结论", supportSummaryText(support))
                }
                GuideNote(
                    "本地推理要求 arm64-v8a 架构、总内存 ≥ 4GB、可用内存 ≥ 2GB。" +
                        "不满足时 App 会在加载前自动切换云端模式，不会闪退。"
                )

                GuideSection("二、准备模型文件") {
                    GuideBullet("格式", "GGUF（不支持 MNN，已废弃）")
                    GuideBullet("推荐", "轻量 0.5B（约 360MB）优先；内存 ≥6GB 可选 1.5B（约 1GB）")
                    GuideBullet("来源", "在线下载，或电脑下载后传 .gguf / .zip 导入")
                }

                GuideSection("三、三步跑起来") {
                    GuideStep(1, "点「下载模型」或「导入模型」，等进度条走完")
                    GuideStep(2, "点「加载模型」，首次加载约需十几秒")
                    GuideStep(3, "状态条显示「引擎已加载」后即可离线对话")
                }

                GuideSection("四、速度与安全") {
                    GuideBullet("首次推理", "较慢（权重页首次读入内存），之后明显变快")
                    GuideBullet("内存回收", "不用时点「卸载释放」，立刻归还几百 MB ~ 1GB")
                    GuideBullet("崩溃兜底", "内存不足时自动降级云端，原文一条不丢")
                }

                GuideSection("五、出问题怎么办") {
                    GuideBullet("提示内存不足", "先卸载释放，关掉其它 App 再加载；仍不行请改用云端模式")
                    GuideBullet("加载失败", "多半是文件不完整，点「重下 / 校验」重新拉取")
                    GuideBullet("本机不支持", "直接在上方选「云端模型」，填写 Base URL 与 API Key 即可")
                }
            }
        }
    )
}

@Composable
private fun GuideSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier.padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { content() }
    }
}

@Composable
private fun GuideBullet(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GuideStep(index: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$index.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(16.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GuideNote(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private fun supportSummaryText(support: LocalLlmSupport): String = when (support) {
    LocalLlmSupport.Ready -> "可以运行本地推理"
    is LocalLlmSupport.Blocked -> support.short
}

/** 单行截断的说明行：统一承担「文案再长也不撑爆版面」的职责 */
@Composable
private fun HintLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavConfigDialog(
    initial: WebDavConfig,
    onDismiss: () -> Unit,
    onSave: (WebDavConfig) -> Unit
) {
    var url by remember { mutableStateOf(initial.url) }
    var user by remember { mutableStateOf(initial.user) }
    var pass by remember { mutableStateOf(initial.pass) }
    var path by remember { mutableStateOf(initial.path) }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(WebDavConfig(url.trim(), user.trim(), pass, path.trim())) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("WebDAV 配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(
                    url, { url = it },
                    label = "服务器地址",
                    placeholder = "https://dav.example.com/",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    user, { user = it },
                    label = "用户名",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    pass, { pass = it },
                    label = "密码",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    path, { path = it },
                    label = "存储路径（可留空）",
                    placeholder = CloudSyncManager.DEFAULT_REMOTE_PATH,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "留空则使用 ${CloudSyncManager.DEFAULT_REMOTE_PATH}，与网页端默认路径一致，两端即可互相同步。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
