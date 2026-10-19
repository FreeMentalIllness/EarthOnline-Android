package com.example.earthonline.data.photos

import android.content.Context
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.util.ImageStore
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「记忆相册」照片条目（v1.0.3，v1.0.6 扩展 albumId）。
 * 元数据存 DataStore JSON，图片文件完整复制在 filesDir/photos（不重编码）。
 * [day] = 拍摄/归属日期 YYYY-MM-DD，用于按月份归档成相册墙与「历年今日」。
 * [albumId] = 所属自定义相册 id（v1.0.6，可空 = 未分类）。
 */
@Serializable
data class MemoryPhoto(
    val id: String,
    /** 文件绝对路径 */
    val path: String,
    /** YYYY-MM-DD */
    val day: String,
    /** 用户补录的「当年的话」（可空） */
    val note: String = "",
    /** v1.0.6：所属相册 id（可空 = 未分类） */
    val albumId: String? = null
)

/**
 * 记忆相册自定义分类（v1.0.6）。
 * 零 Room 迁移：存 DataStore JSON，规避数据损坏风险。
 * [coverPhotoId] 可选封面（为空时由 UI 取该相册首张）。
 */
@Serializable
data class PhotoAlbum(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: String = "",
    val coverPhotoId: String? = null
)

/**
 * 记忆相册存储（v1.0.3）。
 * 批量导入老照片：优先读 EXIF 拍摄时间，其次 MediaStore 日期，再退回文件时间，最后今天。
 * 「按月份自动归档」由 UI 层按 [MemoryPhoto.day] 前七位分组即可，这里只管存取。
 */
@Singleton
class MemoryPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val json: Json
) {

    fun photosDir(): File = File(context.filesDir, "photos").apply { mkdirs() }

    suspend fun load(): List<MemoryPhoto> = parse(settings.memoryPhotosJson.first())

    private fun parse(txt: String): List<MemoryPhoto> =
        if (txt.isBlank()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(MemoryPhoto.serializer()), txt)
        }.getOrDefault(emptyList())

    private suspend fun save(list: List<MemoryPhoto>) {
        settings.setMemoryPhotosJson(
            json.encodeToString(ListSerializer(MemoryPhoto.serializer()), list)
        )
    }

    /**
     * 批量导入：完整复制原图（画质红线），并推断拍摄日期。
     * 返回本次成功导入的数量。
     */
    suspend fun importAll(uris: List<android.net.Uri>): Int = withContext(Dispatchers.IO) {
        var ok = 0
        val current = load().toMutableList()
        for (uri in uris) {
            val result = ImageStore.importOriginal(context, uri, photosDir(), "memory")
            val file = result.getOrNull() ?: continue
            current += MemoryPhoto(
                id = uid("photo"),
                path = file.absolutePath,
                day = guessDay(uri, file),
                note = ""
            )
            ok++
        }
        if (ok > 0) save(current)
        ok
    }

    /** 补录一句「当年的话」 */
    suspend fun setNote(id: String, note: String) {
        val list = load().map { if (it.id == id) it.copy(note = note.trim().take(120)) else it }
        save(list)
    }

    /* ---------------- v1.0.6：自定义相册分类（零 Room 迁移） ---------------- */

    suspend fun loadAlbums(): List<PhotoAlbum> {
        val txt = settings.albumsJson.first()
        return if (txt.isBlank()) emptyList() else runCatching {
            json.decodeFromString(ListSerializer(PhotoAlbum.serializer()), txt)
        }.getOrDefault(emptyList())
    }

    private suspend fun saveAlbums(list: List<PhotoAlbum>) {
        settings.setAlbumsJson(json.encodeToString(ListSerializer(PhotoAlbum.serializer()), list))
    }

    /** 新建相册（同名去重：已存在则直接返回旧的） */
    suspend fun createAlbum(name: String): PhotoAlbum {
        val clean = name.trim().take(24)
        val albums = loadAlbums()
        albums.firstOrNull { it.name == clean }?.let { return it }
        val album = PhotoAlbum(
            id = uid("album"),
            name = clean,
            sortOrder = albums.size,
            createdAt = todayStr()
        )
        saveAlbums(albums + album)
        return album
    }

    suspend fun renameAlbum(id: String, name: String) {
        val clean = name.trim().take(24)
        saveAlbums(loadAlbums().map { if (it.id == id) it.copy(name = clean) else it })
    }

    /** 删除相册：同步把引用它的照片 albumId 置空（照片不丢） */
    suspend fun deleteAlbum(id: String) {
        saveAlbums(loadAlbums().filter { it.id != id })
        val list = load()
        if (list.any { it.albumId == id }) {
            save(list.map { if (it.albumId == id) it.copy(albumId = null) else it })
        }
    }

    /** 设置相册封面（点错可传 null 清除） */
    suspend fun setAlbumCover(albumId: String, photoId: String?) {
        saveAlbums(loadAlbums().map { if (it.id == albumId) it.copy(coverPhotoId = photoId) else it })
    }

    /** 把某张照片归入/移出相册（albumId=null 即移出） */
    suspend fun setPhotoAlbum(photoId: String, albumId: String?) {
        val list = load().map { if (it.id == photoId) it.copy(albumId = albumId) else it }
        save(list)
    }

    /** 取某相册下的照片（按日期倒序）；albumId 空串 = 全部照片（取消 5 张限制） */
    suspend fun photosOfAlbum(albumId: String?): List<MemoryPhoto> {
        val all = load()
        return if (albumId.isNullOrBlank()) all else all.filter { it.albumId == albumId }
    }

    /** 把某张照片设为主页壁纸（静态；轮换模式关闭） */
    suspend fun useAsWallpaper(id: String): Boolean {
        val p = load().firstOrNull { it.id == id } ?: return false
        settings.setWallpaper(p.path)
        settings.setWallpaperRotate(false)
        return true
    }

    suspend fun delete(id: String) {
        val list = load()
        val target = list.firstOrNull { it.id == id } ?: return
        ImageStore.deleteFile(target.path)
        save(list.filter { it.id != id })
    }

    /**
     * v1.0.5 清空数据：删除记忆相册全部原图文件。
     * 元数据键（MEMORY_PHOTOS）由 SettingsDataStore.clearUserDataKeys 统一清，
     * 这里只负责把 filesDir/photos 下的图片文件删干净，避免留孤儿文件占空间。
     * @return 删除的文件数
     */
    suspend fun clearAllFiles(): Int = withContext(Dispatchers.IO) {
        val dir = photosDir()
        var n = 0
        dir.listFiles()?.forEach { f -> if (f.isFile && runCatching { f.delete() }.getOrDefault(false)) n++ }
        n
    }

    /** 推断拍摄日期：EXIF 拍摄时间 > MediaStore DATE_TAKEN > 文件最后修改 > 今天 */
    private fun guessDay(uri: android.net.Uri, file: File): String {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val exif = ExifInterface(ins)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (!raw.isNullOrBlank()) {
                    val parsed = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)
                    if (parsed != null) return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
                }
            }
        }
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null
            )?.use { c ->
                val idx = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                if (c.moveToFirst() && idx >= 0) {
                    val ms = c.getLong(idx)
                    if (ms > 0) return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(ms))
                }
            }
        }
        if (file.lastModified() > 0) {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(file.lastModified()))
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }
}
