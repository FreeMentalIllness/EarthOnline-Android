package com.example.earthonline.ui.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.earthonline.data.network.LocalModelConfig
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 本地模型（离线推理）管理器（v1.0.6.6）。
 *
 * 模型格式支持 **GGUF（llama.cpp / Inferkt）** 与 **MNN（libMNN.so，移植自 messageAIHelper）**：
 *  - 在线下载：魔搭 Qwen 官方 GGUF 仓库直链（[ModelDownloader]，断点续传 + 进度条）；
 *  - 本地导入：.gguf 单文件，或 .zip 压缩包（自动解包，兼容含 GGUF / MNN 权重的任意结构，
 *    逐条目上报解压进度）；MNN 格式（llm.mnn + llm.mnn.weight）由 MnnEngine 直接加载；
 *  - 落盘位置：App 沙盒 filesDir/mnn_models/<safeName>/，不进备份 JSON。
 */
object LocalModelManager {

    /**
     * 模型导入结果（死命令 #2）。
     * - [Ok]：成功，带模型目录；
     * - [Fail]：失败，带**具体原因**（格式不支持 / 解压失败 / 结构不完整 / MNN 不支持），
     *   UI 据此精确提示，绝不静默失败。
     */
    sealed interface ModelImportResult {
        val reason: String? get() = null
        data class Ok(val modelDir: File) : ModelImportResult
        data class Fail(override val reason: String) : ModelImportResult
    }

    const val DEFAULT_PORT = 8090
    private const val DIR = "local_models"

    /** 旧插件（.zip）存放目录：App 沙盒 filesDir/local_models */
    fun pluginsDir(ctx: Context): File = ctx.filesDir.resolve(DIR).apply { if (!exists()) mkdirs() }

    /** 模型目录：filesDir/mnn_models/<safeName>/ */
    fun modelDir(ctx: Context, modelName: String): File =
        ctx.filesDir.resolve("mnn_models")
            .resolve(modelName.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifBlank { "model" })
            .apply { mkdirs() }

    /** 自动搜寻已导入的本地插件（返回不含 .zip 后缀的名字，按字母序） */
    fun scan(ctx: Context): List<String> =
        pluginsDir(ctx).listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            .orEmpty()

    /**
     * 从外部 uri 导入旧插件压缩包（.zip），落盘为 <name>.zip。
     * v1.0.6.11：整段搬到 IO 协程，避免大文件在主线程复制导致 ANR / 系统杀进程
     * （导入失败的主因之一）；并顺带 [takePersistable] 申请长期读权限，规避部分 ROM
     * 上 SAF uri 时效过期后 `openInputStream` 抛 SecurityException 的导入失败。
     */
    suspend fun importFromUri(ctx: Context, uri: Uri, name: String): String? = runCatching {
        withContext(Dispatchers.IO) {
            takePersistable(ctx, uri)
            val safe = name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifBlank { "model" }
            val out = pluginsDir(ctx).resolve("$safe.zip")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            safe
        }
    }.getOrNull()

    /** 申请 SAF uri 的长期读权限（失败忽略：GetContent 返回的 uri 在当次回调内通常已可读） */
    private fun takePersistable(ctx: Context, uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 从外部 uri 导入模型（死命令 #2 强化）。支持三类：
     *  ① `.gguf` 单文件 —— 直接落盘；
     *  ② `.zip` —— 自动解包（防 zip-slip），递归寻找核心权重文件；
     *  ③ 其它扩展名 —— 明确拒绝「格式不支持」。
     * 解包后定位权重：
     *  - 找到 `.gguf` → [Ok]（llama.cpp 引擎可直接加载）；
     *  - 找到 `.mnn`(+`.mnn.weight`) → [Ok]（MnnEngine / libMNN.so 可直接加载，arm64 真机）；
     *  - 什么都没有 → [Fail]「模型结构不完整」。
     * 整个复制 / 解包在 IO 协程执行，并申请 SAF 长期读权限；任何异常都转成可读 [Fail] 原因。
     */
    suspend fun importModelPackage(
        ctx: Context,
        uri: Uri,
        displayName: String,
        onProgress: (entryName: String, written: Long, total: Long) -> Unit = { _, _, _ -> }
    ): ModelImportResult = runCatching {
        withContext(Dispatchers.IO) {
            takePersistable(ctx, uri)
            val safeName = displayName.substringBeforeLast('.')
                .replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
                .ifBlank { "model_${System.currentTimeMillis()}" }
            val dir = modelDir(ctx, safeName)
            val resolver = ctx.contentResolver
            val lower = displayName.lowercase()

            if (lower.endsWith(".gguf")) {
                // ① 单文件 GGUF：原样落盘
                val outName = displayName.substringAfterLast('/').let { if (it.endsWith(".gguf", true)) it else "$it.gguf" }
                val copied = resolver.openInputStream(uri)?.use { input ->
                    File(dir, outName).outputStream().use { input.copyTo(it) }
                    true
                } ?: false
                if (!copied) {
                    return@withContext ModelImportResult.Fail("文件读取失败：无法打开所选文件（无读取权限，或文件已被其它应用移动/删除）")
                }
                val g = dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }.orEmpty().maxByOrNull { it.length() }
                if (g == null || g.length() < 100L * 1024 * 1024) {
                    return@withContext ModelImportResult.Fail("GGUF 模型文件过小或写入失败（小于 100 MB，疑似损坏或未下载完整），请重新选择完整文件")
                }
                return@withContext ModelImportResult.Ok(dir)
            }

            if (lower.endsWith(".zip")) {
                // ② zip：解包（防 zip-slip），逐条目上报「正在解压：<条目名> (xx%)」，
                // 避免 800MB+ 大包解压时用户以为卡死（移植自 messageAIHelper 的 PluginManager 进度回调）。
                val zipSizeBytes = runCatching {
                    resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
                }.getOrDefault(-1L)
                var written = 0L
                val extracted = runCatching {
                    resolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var entry = zip.nextEntry
                            var count = 0
                            while (entry != null) {
                                val target = File(dir, entry.name)
                                if (!target.canonicalPath.startsWith(dir.canonicalPath)) {
                                    entry = zip.nextEntry
                                    continue
                                }
                                if (entry.isDirectory) target.mkdirs()
                                else {
                                    target.parentFile?.mkdirs()
                                    target.outputStream().use { zip.copyTo(it) }
                                    count++
                                    written += target.length().coerceAtLeast(0L)
                                    // 解压进度 UI：当前条目名 + 已写字节 / 总字节（总字节未知时仅显示条目名）
                                    onProgress(entry.name, written, zipSizeBytes)
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                            count
                        }
                    } ?: 0
                }.getOrDefault(0)
                if (extracted <= 0) {
                    return@withContext ModelImportResult.Fail("解压失败：压缩包为空或已损坏（请重新下载模型）")
                }
                // 递归定位核心权重
                return@withContext when (val w = findCoreWeight(dir)) {
                    "gguf" -> ModelImportResult.Ok(dir)
                    // v1.0.6 MNN 移植：MNN 格式（llm.mnn + llm.mnn.weight）现在可运行，接受导入
                    "mnn" -> ModelImportResult.Ok(dir)
                    else -> ModelImportResult.Fail("压缩包内未找到 .gguf 或 .mnn 权重文件，模型结构不完整")
                }
            }

            // ③ 其它格式：明确拒绝
            ModelImportResult.Fail("不支持的文件格式（仅支持 .gguf 单文件，或含 .gguf/.mnn 的 .zip）")
        }
    }.getOrElse { ModelImportResult.Fail("导入失败：${it.message ?: it.javaClass.simpleName}") }

    /** 递归定位模型核心权重：找到 .gguf → \"gguf\"；找到 llm.mnn + llm.mnn.weight → \"mnn\"；都没有 → null。
     *  兼容权重在子目录（如 model/llm.mnn）的情况。 */
    private fun findCoreWeight(dir: File): String? {
        var gguf: File? = null
        dir.walkTopDown().forEach { f ->
            if (f.isFile && gguf == null && f.extension.equals("gguf", true)) gguf = f
        }
        if (gguf != null) return "gguf"
        val hasMnn = dir.walkTopDown().any { it.isFile && it.name == "llm.mnn" && it.length() > 0 }
        val hasWeight = dir.walkTopDown().any { it.isFile && it.name == "llm.mnn.weight" && it.length() > 0 }
        return if (hasMnn && hasWeight) "mnn" else null
    }

    /** 本地推理服务地址（OpenAI 兼容 chat/completions）：固定回环地址 + 用户端口 */
    fun localBaseUrl(port: Int): String = "http://127.0.0.1:${port.coerceIn(1, 65535)}"

    /** 模型目录内是否含 GGUF 文件（llama.cpp 引擎可加载） */
    fun hasGguf(dir: File): Boolean =
        dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }.orEmpty().isNotEmpty()

    /** 模型目录内是否为 MNN 插件包结构（llm.mnn + llm.mnn.weight，兼容嵌套在 model/ 子目录） */
    fun hasMnn(dir: File): Boolean {
        val hasMnn = dir.walkTopDown().any { it.isFile && it.name == "llm.mnn" && it.length() > 0 }
        val hasWeight = dir.walkTopDown().any { it.isFile && it.name == "llm.mnn.weight" && it.length() > 0 }
        return hasMnn && hasWeight
    }

    /**
     * 模型目录校验（GGUF 优先）。
     * 返回 (是否可用于当前引擎, 说明)。
     */
    fun validateModelDir(dir: File): Pair<Boolean, String> {
        if (!dir.exists() || !dir.isDirectory) return false to "模型目录不存在"
        if (hasGguf(dir)) {
            val gguf = dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }!!.maxBy { it.length() }
            if (gguf.length() < 100L * 1024 * 1024) {
                return false to "GGUF 文件过小（${gguf.length() / 1024 / 1024} MB），疑似下载不完整"
            }
            return true to "GGUF 校验通过：${gguf.name}（${gguf.length() / 1024 / 1024} MB），llama.cpp 引擎可加载"
        }
        if (hasMnn(dir)) {
            // v1.0.6 MNN 移植：llm.mnn + llm.mnn.weight 现由 MnnEngine（libMNN.so）直接加载
            val w = dir.walkTopDown().firstOrNull { it.isFile && it.name == "llm.mnn.weight" && it.length() > 0 }
            val mb = (w?.length() ?: 0L) / 1024 / 1024
            return true to "MNN 模型校验通过（权重 ${mb} MB），MNN 引擎可加载（arm64 真机）"
        }
        return false to "模型目录中未找到可用模型（需要 .gguf 文件）"
    }

    /** 把云端 / 本地两份配置收敛为一个「真实可用的请求配置」（runMode=off 时不收敛，原样返回） */
    fun resolve(cloud: com.example.earthonline.data.network.AiConfig, local: LocalModelConfig): com.example.earthonline.data.network.AiConfig =
        if (cloud.runMode == "local") {
            com.example.earthonline.data.network.AiConfig(
                baseUrl = localBaseUrl(local.port),
                apiKey = "local",
                model = local.pluginName.ifBlank { "local" },
                runMode = "local"
            )
        } else cloud

    /** 逻辑卸载标记文件名：写进模型目录即表示「待物理清理」 */
    const val UNLOADED_MARKER = ".unloaded_earthonline"

    /**
     * 真正卸载模型（v1.0.6.11 修复「卸载失败」）：
     *  ① 强制释放底层引擎 —— [LocalLlmRuntime.engine.release] 关闭 llama.cpp 的 native fd
     *     并置空 inference / loaded 引用，否则 mmap 的权重页仍被占用，[File.deleteRecursively] 删不动；
     *  ② 延迟 120ms 等 native 真正交还句柄（部分 ROM 的 mmap 释放有延迟）；
     *  ③ 物理删除模型目录；
     *  ④ 若仍删不掉（句柄延迟释放）→ 写 [UNLOADED_MARKER] 逻辑卸载标记，
     *     由 [cleanupUnloadedOnStartup] 在下次启动清理，避免文件永久残留占用空间。
     * 返回 true=物理删除成功；false=已标记逻辑卸载（文件残留，下次启动清）。
     */
    suspend fun unloadAndDelete(ctx: Context, dir: File): Boolean {
        // ① 强制释放引擎（releaseAwait 挂起等待：进行中的生成结束 + native fd 真正关闭后才返回，
        //    v1.0.6.13 与生成互斥，杜绝「边生成边释放」的 use-after-free 与删不掉）
        runCatching { LocalLlmRuntime.engine.releaseAwait() }
        // ② 等 native 真正交还文件句柄（mmap 映射的权重页可能延迟回收）
        delay(120)
        // ③ 物理删除
        val deleted = runCatching { dir.exists() && dir.deleteRecursively() }.getOrDefault(false)
        if (deleted) return true
        // ④ 删不掉 → 逻辑卸载：写标记，下次启动清理
        runCatching { File(dir, UNLOADED_MARKER).writeText(System.currentTimeMillis().toString()) }
        return false
    }

    /** 启动时清理被逻辑卸载标记的模型目录（此时 fd 必然已释放，可安全删除） */
    fun cleanupUnloadedOnStartup(ctx: Context) {
        runCatching {
            val root = ctx.filesDir.resolve("mnn_models")
            root.listFiles { f -> f.isDirectory }?.forEach { d ->
                if (File(d, UNLOADED_MARKER).exists()) d.deleteRecursively()
            }
        }
    }
}
