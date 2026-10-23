package com.example.earthonline.ui.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * 本地模型在线下载器（v1.0.6.6）。
 *
 * v1.0.6.6 修复「下载 llm.mnn 404」：魔搭直链不能瞎拼 —— 真实仓库是
 * Qwen 官方的 **Qwen/Qwen2.5-1.5B-Instruct-GGUF**（此前错把 org 写成 taobao-mnn，
 * 该仓库不存在，所有 resolve 都 404）。格式按死命令切换为 GGUF（llama.cpp 引擎吃）。
 *
 * 能力：
 *  - 断点续传：已下载部分通过 HTTP Range 从断点继续（服务端不支持 Range 时自动整段重下）；
 *  - 进度条：按「文件名 + 百分比」回调，UI 直接展示；
 *  - 下载完成由调用方调 [LocalModelManager.validateModelDir] 校验。
 */
/**
 * 可选模型规格（v1.0.6.8）。
 *
 * 崩溃治理关键：本地推理的 RSS ≈ 权重文件大小 + KV/计算缓冲。1.5B q4_k_m 约 1GB，
 * 在 4~6GB 的机器上加载后很容易被 LMK 杀掉或直接 native OOM；0.5B 只有约 360MB，
 * 是中低端机唯一稳得住的规格。所以这里默认给 [SMALL]，让用户显式选择更大的模型。
 */
enum class ModelVariant(
    val id: String,
    val modelName: String,
    val file: String,
    val sizeHint: String,
    /** 是否需要 ≥ 6GB 总内存才推荐（用于 UI 默认选中与提示） */
    val needsBigMemory: Boolean
) {
    SMALL(
        id = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
        modelName = "Qwen2.5-0.5B-Instruct-GGUF-q4_k_m",
        file = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        sizeHint = "约 360 MB",
        needsBigMemory = false
    ),
    BASE(
        id = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
        modelName = "Qwen2.5-1.5B-Instruct-GGUF-q4_k_m",
        file = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sizeHint = "约 1.0 GB",
        needsBigMemory = true
    )
}

object ModelDownloader {

    /** GGUF 模型（Qwen 官方 GGUF 仓库，q4_k_m 量化） */
    const val MODEL_ID = "Qwen/Qwen2.5-1.5B-Instruct-GGUF"
    const val MODEL_NAME = "Qwen2.5-1.5B-Instruct-GGUF-q4_k_m"
    const val MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val MODEL_SIZE_HINT = "约 1.0 GB"

    /** 下载清单（GGUF 单文件） */
    val REQUIRED_FILES = listOf(MODEL_FILE)

    private fun fileUrl(modelId: String, file: String): String =
        "https://modelscope.cn/models/$modelId/resolve/master/$file"

    /** 下载状态（UI 进度条直接消费） */
    data class Progress(val file: String, val received: Long, val total: Long) {
        val percent: Int get() = if (total <= 0) 0 else ((received * 100) / total).toInt().coerceIn(0, 100)
    }

    /**
     * 下载全部文件到 [LocalModelManager.modelDir]，逐文件回调进度。
     * 返回 Result<File>：成功时为模型目录（调用方再校验），失败时带可读异常。
     */
    suspend fun downloadAll(
        context: Context,
        onProgress: (Progress) -> Unit
    ): Result<File> = downloadAll(context, ModelVariant.BASE, onProgress)

    suspend fun downloadAll(
        context: Context,
        variant: ModelVariant,
        onProgress: (Progress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = LocalModelManager.modelDir(context, variant.modelName)
            val client = OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            downloadFile(client, dir, variant.id, variant.file, onProgress)
            dir
        }
    }

    /** 单文件下载（带断点续传）。协程取消即中断；已落盘部分保留，下次从断点继续。 */
    private suspend fun downloadFile(
        client: OkHttpClient,
        dir: File,
        modelId: String,
        fileName: String,
        onProgress: (Progress) -> Unit
    ) {
        val dest = File(dir, fileName)
        var start = if (dest.exists()) dest.length() else 0L

        val request = Request.Builder()
            .url(fileUrl(modelId, fileName))
            .get()
            .header("Range", "bytes=$start-")
            .build()
        val resp = client.newCall(request).execute()
        resp.use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("$fileName 下载失败（HTTP ${response.code}）")
            }
            val supportsRange = response.code == 206
            if (!supportsRange) {
                start = 0L
                dest.delete()
                dest.createNewFile()
            }
            val declared = response.body?.contentLength() ?: -1L
            // 206 时 contentLength 是「剩余字节」，总长 = 已有 + 剩余
            val total = if (declared >= 0) (if (supportsRange) start + declared else declared) else -1L

            val body = response.body ?: throw java.io.IOException("$fileName 响应体为空")
            val input = body.byteStream()
            java.io.FileOutputStream(dest, supportsRange).use { out ->
                val buf = ByteArray(64 * 1024)
                var received = start
                var lastEmit = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    received += n
                    // 每 512KB 回调一次进度，避免 UI 过度重组
                    if (total > 0 && received - lastEmit >= 512 * 1024) {
                        lastEmit = received
                        onProgress(Progress(fileName, received, total))
                    }
                }
                out.flush()
            }
            onProgress(Progress(fileName, dest.length(), dest.length()))
        }
    }
}
