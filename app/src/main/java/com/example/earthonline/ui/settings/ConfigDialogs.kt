package com.example.earthonline.ui.settings

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
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
import com.example.earthonline.ui.ai.ModelImportProgress
import com.example.earthonline.ui.ai.ModelImportService
import kotlinx.coroutines.flow.first
import com.example.earthonline.ui.ai.ModelDownloader
import com.example.earthonline.ui.ai.ModelVariant
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.GlassTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

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
    /** v1.0.6（融合重构 · 死命令 #3）：本地模型加载态变化回调（加载成功 true / 卸载 false），供冷启动自动加载。
     *  注意：必须排在 [onSave] 之前 —— 两个调用点都用尾随 lambda 传 onSave，尾随 lambda 恒绑最后一个参数。 */
    onLoadedChange: (Boolean) -> Unit = {},
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
    val scope = rememberCoroutineScope()
    // v1.0.6.14（死命令 #3 主线程守护）：scan 是文件遍历 I/O，原写法在组合期主线程同步执行；
    // 改为空态初始化 + IO 协程回填，杜绝打开配置弹窗时的主线程磁盘扫描
    var discovered by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        discovered = withContext(Dispatchers.IO) { LocalModelManager.scan(context) }
    }
    // 说明入口：默认关闭；本地模式下才由用户主动点开，不占据主版面
    var showGuide by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // v1.0.6.11：整段搬到 IO 协程 —— 旧插件 zip 可能很大，主线程同步复制会 ANR / 导入失败
        scope.launch(Dispatchers.IO) {
            val name = LocalModelManager.importFromUri(context, uri, "plugin_${System.currentTimeMillis()}")
                ?: return@launch
            discovered = discovered + name
            local = local.copy(
                pluginName = name,
                pluginPath = LocalModelManager.pluginsDir(context).resolve("$name.zip").absolutePath,
                discovered = discovered
            )
        }
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
                // v1.0.6.14（死命令 #1）：x86 / 模拟器架构不支持 arm64-only 推理库 ——
                // 直接禁用「本地模型」选项，从源头杜绝误选后加载闪退
                val localSupported = remember {
                    LocalLlmRuntime.hasRequiredAbi() && !LocalLlmRuntime.isEmulator()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeChip("关闭", runMode == "off") { runMode = "off" }
                    ModeChip("云端模型", runMode == "cloud") { runMode = "cloud" }
                    ModeChip("本地模型", runMode == "local", enabled = localSupported) { runMode = "local" }
                }
                if (!localSupported) {
                    HintLine(
                        "当前设备架构不支持本地推理，已禁用本地模型选项",
                        MaterialTheme.colorScheme.error
                    )
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

                        /* —— v1.0.6.13（死命令 #3）：测试连接 ——
                           用当前输入框里的值（无需先保存）做轻量探测：GET /models 优先，
                           404/405 回退 1 token 极短对话；成功 Toast「连接成功！耗时 X ms」，
                           失败给精确错误码（401 鉴权失败 / 404 路径错误 / 超时 / DNS…）。 */
                        var testing by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = {
                                if (testing) return@OutlinedButton
                                testing = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        testCloudConnection(baseUrl.trim(), apiKey.trim(), model.trim())
                                    }
                                    testing = false
                                    runCatching {
                                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !testing && baseUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (testing) "测试中…" else "测试连接", maxLines = 1)
                        }
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
                            onLoadedChange = onLoadedChange,
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
private fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
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

/**
 * v1.0.6.13（死命令 #3）：云端接口连通性探测（IO 协程调用）。
 * 两段式：① GET {base}/models（OpenAI 兼容最轻量探针）；
 *        ② 遇 404/405（服务商不提供 /models）回退 POST {base}/chat/completions 极短对话（1 token）。
 * 成功返回「连接成功！耗时 X ms」；失败返回**精确原因**（401 鉴权失败 / 404 路径错误 /
 * 429 限流 / 5xx 服务端 / DNS / 超时 / TLS）。全程 runCatching，绝不向 UI 抛异常。
 */
private fun testCloudConnection(baseUrl: String, apiKey: String, model: String): String {
    if (baseUrl.isBlank()) return "请先填写 Base URL"
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    val base = baseUrl.trimEnd('/')
    val t0 = System.currentTimeMillis()
    fun elapsed(): String = "${System.currentTimeMillis() - t0} ms"

    fun ioError(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "域名无法解析：请检查 Base URL 拼写与网络连接"
        is java.net.ConnectException -> "连接失败：地址不可达、端口被拒或被防火墙/代理拦截"
        is java.net.SocketTimeoutException -> "连接超时：网络不通或服务无响应"
        is javax.net.ssl.SSLException -> "TLS/SSL 握手失败：证书无效或代理劫持"
        else -> "网络异常：${e.message ?: e.javaClass.simpleName}"
    }

    // ① GET /models
    runCatching {
        client.newCall(
            Request.Builder().url("$base/models")
                .header("Authorization", "Bearer $apiKey")
                .get().build()
        ).execute().use { resp ->
            val code = resp.code
            when {
                resp.isSuccessful -> return "连接成功！耗时 ${elapsed()}"
                code == 401 || code == 403 -> return "鉴权失败（$code）：API Key 无效或无权限"
                code == 429 -> return "请求过于频繁（429）：稍后再试"
                code in 500..599 -> return "服务端错误（$code）：服务商故障或额度用尽"
                // 404/405：该服务商可能不提供 /models → 落到 ② 极短对话探测
            }
        }
    }.getOrElse { e -> return ioError(e) }

    // ② POST /chat/completions（1 token 极短对话）
    runCatching {
        val probeModel = model.ifBlank { "probe" }
        val payload =
            "{\"model\":\"$probeModel\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":1}"
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        client.newCall(
            Request.Builder().url("$base/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body).build()
        ).execute().use { resp ->
            val code = resp.code
            return when {
                resp.isSuccessful -> "连接成功！耗时 ${elapsed()}"
                code == 401 || code == 403 -> "鉴权失败（$code）：API Key 无效或无权限"
                code == 404 -> "路径不存在（404）：请检查 Base URL 是否需要以 /v1 结尾"
                code == 429 -> "请求过于频繁（429）：Key 有效但被限流"
                code in 500..599 -> "服务端错误（$code）：服务商故障或模型名不存在"
                else -> "接口返回 $code：请核对 Base URL / Key / 模型名"
            }
        }
    }.getOrElse { e -> return ioError(e) }

    return "连接失败：未知错误"
}

@Composable
private fun LocalModelPanel(
    local: LocalModelConfig,
    portText: String,
    onPortChange: (String) -> Unit,
    discovered: List<String>,
    onDiscoveredChange: (List<String>) -> Unit,
    onLocalChange: (LocalModelConfig) -> Unit,
    onLoadedChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadUi by remember { mutableStateOf<ModelDownloadUi>(ModelDownloadUi.Idle) }
    var loadMsg by remember { mutableStateOf<String?>(null) }
    /* v1.0.6（融合重构 · 死命令 #2）：只保留「标准 1.5B」一档并默认选中 ——
       0.5B 小模型回复质量太差（用户实测「答非所问」），两档并存徒增选择负担；
       4GB 以下机型本来就会被加载前体检拦住并降级云端，无需用户自己避坑。 */
    var variant by remember { mutableStateOf(ModelVariant.BASE) }

    val modelImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "model.zip"
        scope.launch {
            loadMsg = "正在导入并解包模型…"
            /* 终极重构 · 工程四：800MB 级解压交给**前台服务**执行 ——
               提升进程优先级，切后台不会被系统杀，通知栏实时显示进度，
               界面退出再回来也能接着拿到结果，绝不会出现「进度条卡死像崩了」。
               UI 侧只观察 ModelImportProgress，不再自己跑解压协程。 */
            val progressJob = launch {
                ModelImportProgress.label.collect { entry ->
                    if (entry.isNotBlank()) {
                        loadMsg = "正在解压：$entry (${ModelImportProgress.percent.value}%)"
                    }
                }
            }
            ModelImportService.start(context, uri, name)
            val result = withContext(Dispatchers.IO) {
                ModelImportProgress.result.first { it != null }
            }!!
            progressJob.cancel()
            when (result) {
                is LocalModelManager.ModelImportResult.Ok -> {
                    val (ok, msg) = LocalModelManager.validateModelDir(result.modelDir)
                    if (ok) {
                        val size = result.modelDir.listFiles()?.sumOf { it.length() } ?: 0L
                        onLocalChange(
                            local.copy(
                                downloaded = true,
                                modelDir = result.modelDir.absolutePath,
                                sizeBytes = size,
                                modelName = result.modelDir.name
                            )
                        )
                        loadMsg = msg
                    } else {
                        loadMsg = msg
                    }
                }
                is LocalModelManager.ModelImportResult.Fail -> loadMsg = result.reason
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        /* v1.0.6.9：正文里不再出现「使用说明」文字按钮 —— 说明入口只保留弹窗标题栏
           右上角那一个 i 图标，避免同一功能出现两处、把版面顶长。 */
        Text(
            "离线模型 · MNN / llama.cpp",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        /* —— 设备体检：本机能不能跑本地推理，先说清楚（内存统一 GB/MB 口径） —— */
        val support = remember { LocalLlmRuntime.diagnose() }
        // v1.0.6.14：memory() 走 ActivityManager 系统调用，remember 一次防止每次重组都打系统服务
        val (avail, total) = remember { LocalLlmRuntime.memory(context) }
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

        /* —— 状态卡片：当前模型 / 引擎状态，单行截断 ——
           状态必须与实际文件 / 引擎事实一致，不能只信易失的 Compose 状态 local.downloaded
           （dialog 重开或没点「保存」时会丢，导致「已下载却显示尚未准备模型」的错位）。
           改为直接校验模型目录是否真有 GGUF——下载/解压后即时变为「已就绪」。 */
        val modelDirResolved = remember(local.modelDir, variant) {
            File(local.modelDir.ifBlank { LocalModelManager.modelDir(context, variant.modelName).absolutePath })
        }
        // v1.0.6.14（死命令 #3 主线程守护）：hasGguf 是文件系统遍历，原写法在组合期主线程同步执行；
        // 改为 produceState + IO 协程回填，目录变化时自动重查
        val fileReady by produceState(false, modelDirResolved) {
            // v1.0.6 MNN 移植：GGUF 或 MNN 权重任一存在即视为模型文件就绪
            value = withContext(Dispatchers.IO) {
                LocalModelManager.hasGguf(modelDirResolved) || LocalModelManager.hasMnn(modelDirResolved)
            }
        }
        val statusText = when {
            // 只有真的 ready 才敢说「已加载」
            LocalLlmRuntime.engine.ready -> "引擎已加载：${LocalLlmRuntime.engine.name}"
            fileReady -> "模型文件已就绪（未加载），点「加载模型」"
            support is LocalLlmSupport.Blocked -> "模拟模式：本机不能跑真实推理"
            else -> "尚未准备模型：请先下载或导入 GGUF"
        }
        AssistChip(
            onClick = {},
            label = { Text(statusText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        )

        /* —— 模型规格（v1.0.6 融合重构）：只保留「标准 1.5B」一档，默认选中 —— */
        Text("模型规格", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("标准 1.5B · 1GB", true, Modifier.weight(1f)) { }
        }
        HintLine("统一使用 1.5B 模型：回复质量与速度最佳平衡；内存不足时 App 会在加载前自动提示。", MaterialTheme.colorScheme.onSurfaceVariant)

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

                        /* ⓪ 目录存在性（v1.0.6.6 修复：导入路径被手改 / 清数据后目录消失，
                            此前会一路走到「没有 .gguf」的误导性结论） */
                        if (!dir.exists() || !dir.isDirectory) {
                            loadMsg = "加载失败：模型目录不存在（${dir.absolutePath}），请先导入模型"
                            return@launch
                        }

                        /* ① 先定引擎，再按各自格式校验完整性。
                           ⚠️ v1.0.6.6 修复「本地模型加载必失败」真机 Bug：
                           原流程无条件要求目录里有 .gguf，而 MNN 模型包里根本没有 gguf
                           （是 llm.mnn + llm.mnn.weight），于是 MNN 用户永远卡在
                           「目录里没有 .gguf 文件」—— 实为校验逻辑与引擎选型脱节。
                           现在与 LocalLlmRuntime.engineFor 的选型顺序保持一致：MNN 优先、GGUF 回落。 */
                        val useMnn = LocalModelManager.hasMnn(dir)
                        val gguf = dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
                            ?.maxByOrNull { it.length() }
                        val weightName: String
                        when {
                            useMnn -> {
                                val w = dir.walkTopDown().firstOrNull {
                                    it.isFile && it.name == "llm.mnn.weight" && it.length() > 0
                                }
                                if (w == null) {
                                    loadMsg = "加载失败：MNN 权重文件 llm.mnn.weight 缺失，模型包不完整，请重新导入"
                                    return@launch
                                }
                                if (w.length() < 32L * 1024 * 1024) {
                                    loadMsg = "加载失败：MNN 权重只有 ${LocalLlmRuntime.formatBytes(w.length())}，" +
                                        "明显不完整（解压中断或文件损坏），请重新导入模型"
                                    return@launch
                                }
                                weightName = "llm.mnn.weight（${LocalLlmRuntime.formatBytes(w.length())}）"
                            }
                            gguf != null -> {
                                if (gguf.length() < 32L * 1024 * 1024) {
                                    loadMsg = "加载失败：模型文件只有 ${LocalLlmRuntime.formatBytes(gguf.length())}，" +
                                        "明显不完整（下载中断或被截断），请点「重下 / 校验」重新下载"
                                    return@launch
                                }
                                weightName = "${gguf.name}（${LocalLlmRuntime.formatBytes(gguf.length())}）"
                            }
                            else -> {
                                loadMsg = "加载失败：模型目录里既没有 MNN 权重（llm.mnn + llm.mnn.weight），" +
                                    "也没有 .gguf 文件 —— 请先用「导入模型」导入完整模型包"
                                return@launch
                            }
                        }

                        // ② 设备体检：不过关就**零 JNI 调用**（native 崩溃 Java 层拦不住）
                        val engine = LocalLlmRuntime.engineFor(dir)
                        if (engine === MockLlmEngine) {
                            loadMsg = when {
                                !LocalLlmRuntime.hasRequiredAbi() ->
                                    "加载失败：设备架构不支持（当前 ABI=${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"}，" +
                                        "本地推理仅支持 arm64-v8a），请改用云端模型"
                                LocalLlmRuntime.isEmulator() ->
                                    "加载失败：检测到模拟器环境，本地推理仅支持 arm64 真机，请改用云端模型"
                                else ->
                                    "加载失败：模型格式未被识别（需 MNN 权重或 .gguf），请重新导入模型"
                            }
                            return@launch
                        }
                        when (val diag = LocalLlmRuntime.diagnose(dir)) {
                            is LocalLlmSupport.Blocked -> {
                                loadMsg = "加载失败：${diag.short}"
                                Log.w("EarthOnline-LLM", "load blocked: ${diag.detail}")
                                return@launch
                            }
                            else -> Unit
                        }

                        // ③ 真实加载（IO 协程，内部已 catch Throwable + OOM）
                        loadMsg = "正在加载 $weightName…"
                        val ok = engine.load(dir.absolutePath) { step -> loadMsg = step }
                        if (ok) {
                            LocalLlmRuntime.activate(engine)
                            // 死命令 #3：加载成功即持久化「已加载」标志 → 下次冷启动静默自动加载
                            onLoadedChange(true)
                            loadMsg = "已加载 ${engine.name}，离线推理就绪"
                        } else {
                            // load() 内部已把具体原因（OOM / 文件损坏 / 原生库缺失）写到 loadMsg
                            loadMsg = loadMsg?.takeIf { it.contains("失败") || it.contains("不足") || it.contains("不可用") }
                                ?: "加载失败：引擎未能初始化（内存不足或模型文件损坏），可尝试重新导入模型，或切换云端模型"
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("加载模型", maxLines = 1) }
            OutlinedButton(
                onClick = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        // ① 先强制释放引擎 fd + 置空引用（unloadAndDelete 内部已调 release），
                        //    延迟 120ms 等 native 交还句柄，再物理删除模型目录；删不掉则写逻辑卸载标记。
                        val dir = if (local.modelDir.isNotBlank()) java.io.File(local.modelDir)
                        else LocalModelManager.modelDir(context, com.example.earthonline.ui.ai.ModelDownloader.MODEL_NAME)
                        val ok = LocalModelManager.unloadAndDelete(context, dir)
                        // ② 引擎引用复位为 Mock（避免状态卡残留）
                        LocalLlmRuntime.activate(MockLlmEngine)
                        // ③ 清除「已下载」与「已加载」标记，UI 状态与事实一致（否则冷启动会白加载）
                        onLoadedChange(false)
                        onLocalChange(local.copy(downloaded = false, modelDir = "", sizeBytes = 0))
                        loadMsg = if (ok) "已卸载模型并释放空间" else "已标记卸载，重启应用后清理残留文件"
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("卸载释放", maxLines = 1) }
        }
        /* v1.0.6.6（死命令 #3 诚实反馈）：**失败一律红字**。
           此前所有提示都用次要灰，用户分不清「正在加载」和「加载失败」，
           以为卡住了。现在按语义判定：含失败 / 不足 / 不可用 / 不支持 / 损坏 → error 红字 + 警示图标。 */
        loadMsg?.let { msg ->
            val isFailure = msg.contains("失败") || msg.contains("不足") || msg.contains("不可用") ||
                msg.contains("不支持") || msg.contains("损坏") || msg.contains("无法") || msg.contains("不存在")
            ErrorHintLine(msg, if (isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }

        /* —— 旧能力（默认收起）：本机自建 OpenAI 兼容服务端口 / .zip 插件 ——
           改为与整体设置项风格一致的「列表项」：左侧标题、右侧箭头，点击展开/收起，
           不再用孤立的文本按钮挂在底部。 */
        var showAdvanced by remember { mutableStateOf(false) }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showAdvanced) "高级选项（点击收起）" else "高级选项",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            /* v1.0.6.6（死命令：删除重复入口）：原「导入插件（.zip）」按钮已移除 ——
               它与上方「导入模型」是同一件事（都是选文件导入），两处并存让用户分不清该点哪个。
               全页**只保留一个「导入模型」入口**。 */
            HintLine(
                "模型的导入 / 下载统一使用上方「导入模型」「下载模型」按钮（全页仅此一处入口）。",
                MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    GuideBullet("格式", "MNN（.mnn，推荐）或 GGUF（.gguf）")
                    GuideBullet("规格", "Qwen2.5-1.5B（MNN 约 800MB~1GB / GGUF 约 1GB）")
                    GuideBullet("来源", "电脑下载 qwen2.5-1.5b-mnn.zip 或 .gguf 后，从文件导入")
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

/**
 * v1.0.6.6：**可换行的错误提示行**（失败原因往往很长，一行截断等于没说）。
 * 失败色（error）时左侧带警示图标，让「出问题了」在扫视层面就能被看见。
 */
@Composable
private fun ErrorHintLine(text: String, color: androidx.compose.ui.graphics.Color) {
    val isError = color == MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isError) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal
        )
    }
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
