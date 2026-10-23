package com.example.earthonline.ui.ai

import android.content.Context
import android.net.Uri
import com.example.earthonline.data.network.LocalModelConfig
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 本地模型（离线推理）管理器（v1.0.6.6）。
 *
 * 模型格式按死命令切换为 **GGUF**（llama.cpp / Inferkt 引擎）：
 *  - 在线下载：魔搭 Qwen 官方 GGUF 仓库直链（[ModelDownloader]，断点续传 + 进度条）；
 *  - 本地导入：.gguf 单文件，或 .zip 压缩包（自动解包，兼容含 GGUF 的任意结构，
 *    也兼容 MNN 插件包结构仅作预存 —— MNN 引擎按死命令放弃，导入后提示改用 GGUF）；
 *  - 落盘位置：App 沙盒 filesDir/mnn_models/<safeName>/，不进备份 JSON。
 */
object LocalModelManager {

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

    /** 从外部 uri 导入一个插件压缩包，落盘为 <name>.zip，返回插件名（失败返回 null） */
    fun importFromUri(ctx: Context, uri: Uri, name: String): String? = runCatching {
        val safe = name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifBlank { "model" }
        val out = pluginsDir(ctx).resolve("$safe.zip")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        safe
    }.getOrNull()

    /**
     * 从外部 uri 导入模型压缩包并解包到模型目录（v1.0.6.6）。
     * 支持：.zip（自动解包，保留相对结构；防 zip-slip）；其余按单文件原样落盘（如 .gguf）。
     * 返回模型目录（失败 null）。
     */
    fun importModelPackage(ctx: Context, uri: Uri, displayName: String): File? = runCatching {
        val safeName = displayName.substringBeforeLast('.')
            .replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
            .ifBlank { "model_${System.currentTimeMillis()}" }
        val dir = modelDir(ctx, safeName)
        val resolver = ctx.contentResolver
        val lower = displayName.lowercase()
        if (lower.endsWith(".zip")) {
            resolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    var count = 0
                    while (entry != null) {
                        val target = File(dir, entry.name)
                        // 防 zip-slip：规范化后必须仍在模型目录内
                        if (!target.canonicalPath.startsWith(dir.canonicalPath)) {
                            entry = zip.nextEntry
                            continue
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            count++
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    if (count == 0) return null
                }
            } ?: return null
        } else {
            // 单文件（.gguf 等）：原样落盘
            resolver.openInputStream(uri)?.use { input ->
                File(dir, displayName.substringAfterLast('/')).outputStream().use { input.copyTo(it) }
            } ?: return null
        }
        dir
    }.getOrNull()

    /** 本地推理服务地址（OpenAI 兼容 chat/completions）：固定回环地址 + 用户端口 */
    fun localBaseUrl(port: Int): String = "http://127.0.0.1:${port.coerceIn(1, 65535)}"

    /** 模型目录内是否含 GGUF 文件（llama.cpp 引擎可加载） */
    fun hasGguf(dir: File): Boolean =
        dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }.orEmpty().isNotEmpty()

    /** 模型目录内是否为 MNN 插件包结构（llm.mnn + llm.mnn.weight） */
    fun hasMnn(dir: File): Boolean =
        File(dir, "llm.mnn").let { it.exists() && it.length() > 0 } &&
            File(dir, "llm.mnn.weight").let { it.exists() && it.length() > 0 }

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
            return false to "检测到 MNN 格式模型：MNN 官方未发布 Android 推理 AAR，本地引擎已切换为 llama.cpp（GGUF）。" +
                "请下载或导入 Qwen2.5-1.5B 的 GGUF 格式（已放弃 MNN 格式）。"
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
}
