package com.example.earthonline.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.earthonline.ui.theme.AmberPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 裁剪形状。
 * - [Circle]：头像，输出为正方形位图，显示端再按圆形裁剪（见 ui/components/Avatar.kt）；
 * - [Rect]：壁纸，输出为裁剪窗口比例的矩形，显示端用 ContentScale.Crop 铺满屏幕。
 */
enum class CropShape { Circle, Rect }

/**
 * 图片裁剪 + 缩放对话框。
 *
 * ## 设计要点（满足「导入大图 + 自定义裁剪缩放 + 不压缩画质」）
 * 1. **预览只解码缩略图**：用 `inSampleSize` 把原图降采样到 ≤1024px 供屏幕预览，
 *    4K 原图也不会有全尺寸位图进内存（避免 OOM / 掉帧）。
 * 2. **裁剪用区域解码**：确认时通过 `BitmapRegionDecoder` 只解码用户选中的那块区域，
 *    输出位图尺寸 = 选区在**原图**中的真实分辨率（极大图再按 ≤2048px 兜底防 OOM），
 *    因此裁剪框里每一像素都来自原图，不是预览放大——画质不丢。
 * 3. **无损保存**：结果以 **PNG**（100，无损）写入私有目录，绝不做 JPEG 重压缩
 *    （上一版把图压成 512px JPEG 才导致画质糊掉，这里彻底规避）。
 * 4. **手势**：双指捏合缩放（以捏合中心为锚点）+ 单指拖拽平移，约束为「cover」——
 *    缩放下限 = 让选区被图片完全盖住，上限 8×，且拖拽后图片始终盖住选区（不会露出空白）。
 *
 * 整个组件**不引入任何新依赖**，纯 Compose + Android 图形 API。
 *
 * @param uri       用户从系统选择器选中的原图 Uri
 * @param shape     裁剪形状（圆形=头像 / 矩形=壁纸）
 * @param outputDir 落盘目录（头像=avatarDir / 壁纸=wallpaperDir）
 * @param prefix    落盘文件名前缀
 * @param onConfirm 裁剪成功，返回落盘后的文件（绝对路径即持久化值）
 * @param onDismiss 取消
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropperDialog(
    uri: Uri,
    shape: CropShape,
    outputDir: File,
    prefix: String,
    onConfirm: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 加载：原图边界 + 预览位图（IO 线程，避免阻塞首帧）
    val loadState = produceState<Triple<Bitmap, Int, Int>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val srcW = bounds.outWidth
                val srcH = bounds.outHeight
                if (srcW <= 0 || srcH <= 0) return@runCatching null
                // v1.0.4：预览降采样目标 1024 -> 2048。裁剪视口是全屏（宽普遍 1080+ 物理像素），
                // 1024 的预览位图在初始 cover 状态就被上采样，放大到 2~4x 时肉眼可见地糊，
                // 用户会误以为「裁出来也是糊的」；2048 保证常用缩放范围内预览始终锐利，
                // 内存代价约 2048×2048×4 ≈ 16MB，仅在本对话框存活期间持有，可接受。
                val rawSample = max(1, (max(srcW, srcH) / 2048f).toInt())
                var sample = 1
                while (sample * 2 <= rawSample) sample *= 2
                val pOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, pOpts)
                }
                if (bmp == null) null else Triple(bmp, srcW, srcH)
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                // 顶部栏
                TopAppBar(
                    title = { Text(if (shape == CropShape.Circle) "裁剪头像" else "裁剪壁纸") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "取消裁剪")
                        }
                    }
                )
                // 裁剪区
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    val data = loadState.value
                    if (data == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "图片加载中或无法读取，请重试",
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        CropperCanvas(
                            bitmap = data.first,
                            srcW = data.second,
                            srcH = data.third,
                            shape = shape,
                            onConfirm = { rect ->
                                // 区域解码 + PNG 落盘（搬到 IO 线程）
                                val file = cropToPng(context, uri, rect, outputDir, prefix)
                                if (file != null) onConfirm(file) else onDismiss()
                            }
                        )
                    }
                }
                // 确认按钮在裁剪区底部中央（见 CropperCanvas），取消在顶部栏（✕）
            }
        }
    }
}

/**
 * 裁剪画布：负责预览绘制、手势变换、裁剪窗口蒙版，以及「确认」按钮。
 * 把确认逻辑收敛在这里，父级 Dialog 只负责外壳。
 */
@Composable
private fun CropperCanvas(
    bitmap: Bitmap,
    srcW: Int,
    srcH: Int,
    shape: CropShape,
    onConfirm: (Rect) -> Unit
) {
    val density = LocalDensity.current
    val pw = bitmap.width.toFloat()
    val ph = bitmap.height.toFloat()
    // 原图坐标 → 预览坐标的比例（预览已降采样，srcW/pw == inSampleSize）
    val srcPerPreview = srcW / pw

    // 视口尺寸（px），由 onSizeChanged 拿到
    var vpSize by remember { mutableStateOf(Size.Zero) }
    // 变换（左上角为原点，便于坐标反推）：scale 为预览位图的缩放，offset 为预览图左上角在视口中的位置
    val scale = remember { mutableStateOf(1f) }
    val offsetX = remember { mutableStateOf(0f) }
    val offsetY = remember { mutableStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }

    // 裁剪窗口（视口坐标，px）：圆形=居中正方形；矩形=整个视口
    val window: Size
    val winX: Float
    val winY: Float
    if (vpSize == Size.Zero) {
        window = Size.Zero; winX = 0f; winY = 0f
    } else {
        if (shape == CropShape.Circle) {
            val side = min(vpSize.width, vpSize.height) * 0.85f
            window = Size(side, side)
        } else {
            window = vpSize
        }
        winX = (vpSize.width - window.width) / 2f
        winY = (vpSize.height - window.height) / 2f
    }

    // 初始化为中心 cover
    fun resetTransform() {
        if (vpSize == Size.Zero || window == Size.Zero) return
        val minScale = max(window.width / pw, window.height / ph)
        scale.value = minScale
        offsetX.value = winX + (window.width - pw * minScale) / 2f
        offsetY.value = winY + (window.height - ph * minScale) / 2f
    }
    if (!initialized && vpSize != Size.Zero && window != Size.Zero) {
        resetTransform()
        initialized = true
    }

    fun clamp() {
        if (window == Size.Zero) return
        val dispW = pw * scale.value
        val dispH = ph * scale.value
        // 图片必须盖住窗口：offset ∈ [winRight - dispW, winLeft]
        val minX = winX + window.width - dispW
        val maxX = winX
        offsetX.value = offsetX.value.coerceIn(min(minX, maxX), max(minX, maxX))
        val minY = winY + window.height - dispH
        val maxY = winY
        offsetY.value = offsetY.value.coerceIn(min(minY, maxY), max(minY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { vpSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale.value * zoom).coerceIn(
                        max(window.width / pw, window.height / ph),
                        8f
                    )
                    val factor = newScale / scale.value
                    // 以捏合中心为锚点缩放
                    offsetX.value = centroid.x - (centroid.x - offsetX.value) * factor
                    offsetY.value = centroid.y - (centroid.y - offsetY.value) * factor
                    // 平移
                    offsetX.value += pan.x
                    offsetY.value += pan.y
                    scale.value = newScale
                    clamp()
                }
            }
    ) {
        // 预览画布
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawImage(
                bitmap.asImageBitmap(),
                dstOffset = IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()),
                dstSize = IntSize(
                    (pw * scale.value).roundToInt().coerceAtLeast(1),
                    (ph * scale.value).roundToInt().coerceAtLeast(1)
                )
            )
            // 窗口外蒙版
            val dim = Color.Black.copy(alpha = 0.55f)
            val w = window
            if (w != Size.Zero) {
                // 上
                drawRect(dim, Offset(0f, 0f), Size(size.width, winY))
                // 下
                drawRect(dim, Offset(0f, winY + w.height), Size(size.width, size.height - (winY + w.height)))
                // 左
                drawRect(dim, Offset(0f, winY), Size(winX, w.height))
                // 右
                drawRect(dim, Offset(winX + w.width, winY), Size(size.width - (winX + w.width), w.height))
                // 边框
                val stroke = Stroke(width = 2.dp.toPx())
                drawRect(Color.White, Offset(winX, winY), w, style = stroke)
                if (shape == CropShape.Circle) {
                    drawCircle(
                        color = Color.White,
                        radius = w.width / 2f,
                        center = Offset(winX + w.width / 2f, winY + w.height / 2f),
                        style = stroke
                    )
                }
            }
        }

        // 确认按钮（浮在裁剪区底部中央）
        Button(
            onClick = {
                if (window == Size.Zero) return@Button
                // 窗口（视口坐标）→ 原图坐标
                val localX = (winX - offsetX.value) / scale.value
                val localY = (winY - offsetY.value) / scale.value
                val srcX = (localX * srcPerPreview).coerceIn(0f, srcW.toFloat())
                val srcY = (localY * srcPerPreview).coerceIn(0f, srcH.toFloat())
                val srcWRect = ((window.width / scale.value) * srcPerPreview)
                    .coerceAtMost(srcW - srcX)
                val srcHRect = ((window.height / scale.value) * srcPerPreview)
                    .coerceAtMost(srcH - srcY)
                val rect = Rect(
                    srcX.roundToInt().coerceAtLeast(0),
                    srcY.roundToInt().coerceAtLeast(0),
                    (srcX + srcWRect).roundToInt().coerceAtMost(srcW),
                    (srcY + srcHRect).roundToInt().coerceAtMost(srcH)
                )
                if (rect.width() > 0 && rect.height() > 0) onConfirm(rect)
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Text("选择这片区域")
        }
    }
}

/**
 * 用区域解码裁出原图选区并保存为无损 PNG。
 * 仅在选区超过 2048px 时才按 2 的幂降采样，避免超大图解码 OOM；
 * 正常情况下输出即选区原分辨率，画质零损失。
 */
private fun cropToPng(
    context: Context,
    uri: Uri,
    rect: Rect,
    outputDir: File,
    prefix: String
): File? = runCatching {
    outputDir.mkdirs()
    val input = context.contentResolver.openInputStream(uri) ?: return null
    val decoder = input.use { BitmapRegionDecoder.newInstance(it, false) } ?: return null
    try {
        // 超大选区兜底降采样（2 的幂）。v1.0.4：上限 2048 -> 3072 —— 2048 会把
        // 2K/3K 屏的壁纸裁剪输出压到不足屏幕原生分辨率，铺满时被拉糊；
        // 3072 长边的区域解码内存约 20MB 量级，画质余量与 OOM 风险平衡点。
        val rawSample = max(1, (max(rect.width(), rect.height()) / 3072f).toInt())
        var sample = 1
        while (sample * 2 <= rawSample) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val crop = decoder.decodeRegion(rect, opts) ?: return null
        val ext = "png"
        val dst = File(outputDir, "${prefix}_${System.currentTimeMillis()}.$ext")
        FileOutputStream(dst).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle()
        dst
    } finally {
        runCatching { decoder.recycle() }
    }
}.getOrNull()
