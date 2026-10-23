package com.example.earthonline.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.earthonline.ui.theme.AmberPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.io.File

/**
 * v1.0.0：统一的「用户头像」渲染（个人资料页 / 主页共用）。
 *
 * ## 关键点：显示端按需采样，而不是存储端压缩
 * 头像存的是**原图文件**（4K 也原样保存，见 util/ImageStore）。
 * 这里靠 Coil 的 `.size(px)` 把「我只需要这么多像素」告诉解码器：
 * Coil 会用 `inSampleSize` 按目标尺寸解码 —— 一张 4000×3000 的原图，
 * 最终只分配 80dp 控件实际需要的那点像素，全尺寸位图永远不会进内存。
 * 画质与内存两头都要，这才是正解；导入时一刀切压缩是拿画质换省事。
 *
 * 采样尺寸取「控件实际像素」而不是写死一个值：容器多大就解多大，
 * 以后头像改成 120dp 也不用改这里。
 *
 * @param avatarData 兼容字段：v1.0.0 之前头像直接把字节存在数据库里，仍要能显示
 */
@Composable
fun UserAvatar(
    avatarPath: String?,
    avatarData: ByteArray?,
    avatarKey: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // 控件实际像素：Coil 照这个尺寸采样（不写死，容器多大解多大）
    val px = (size.value * density.density).roundToInt()

    /* 文件是否还在：换设备 / 清数据 / 备份导入失败之后，路径可能指向一个不存在的文件。
       这种时候要干净地回落成预设 emoji，而不是给 Coil 一个空 model 让它报错。 */
    val file by produceState<File?>(initialValue = null, avatarPath) {
        value = withContext(Dispatchers.IO) {
            avatarPath?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.exists() && it.length() > 0L }
        }
    }

    val f = file
    if (f != null) {
        val request = remember(f, px) {
            ImageRequest.Builder(context)
                .data(f)
                // 只要这么大：Coil 据此算 inSampleSize，按目标尺寸解码
                .size(px)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = "头像",
            // Crop 才能填满圆形：默认 Fit 会把非正方形头像按比例塞进圆圈，
            // 上下（或左右）留出两条透明带，看起来像「头像没填满」。
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else if (avatarData != null && avatarData!!.isNotEmpty()) {
        // v1.0.4：旧版数据库字节数据同样按控件尺寸采样。此前裸传 ByteArray 会让 Coil
        // 按原始尺寸解码（几百 KB 的 JPEG 解出 4000px 位图），既费内存，也没有跟文件
        // 路径一致的清晰度表现；.size(px) + INEXACT 与上方文件分支完全同参。
        val bytesRequest = remember(avatarData, px) {
            ImageRequest.Builder(context)
                .data(avatarData)
                .size(px)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = bytesRequest,
            contentDescription = "头像",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else {
        /* v1.0.6.6（真机反馈批）：**取消默认 Emoji 头像**。
           真机反馈：系统按 avatarKey 派发的 emoji 在部分机型上字体缺字形（豆腐块）、
           且风格与全站矢量图标语言割裂。未上传头像时改为极简矢量占位 ——
           琥珀底 + Person 轮廓线图标，安静、不抢戏，也不会出现方块字。 */
        Surface(
            color = AmberPrimary.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PersonOutline,
                    contentDescription = "未设置头像",
                    tint = AmberPrimary,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}
