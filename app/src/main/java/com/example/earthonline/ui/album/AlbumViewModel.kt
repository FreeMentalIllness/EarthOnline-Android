package com.example.earthonline.ui.album

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.photos.MemoryPhoto
import com.example.earthonline.data.photos.MemoryPhotoStore
import com.example.earthonline.data.photos.PhotoAlbum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 记忆相册（v1.0.3 起步，v1.0.6 大改）：
 * 批量导入老照片 → 按「自定义相册」分类管理（DataStore 扩展，零 Room 迁移），
 * 每张照片可补录「当年的话」、设为壁纸、唤起系统相册替换图像、归入/移出相册。
 * 浏览走「堆叠 + 3D 翻页」的卡片组（见 [AlbumRoute]）。
 *
 * 元数据存 DataStore JSON，图片原图完整复制（不重编码，画质红线）。
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val store: MemoryPhotoStore
) : ViewModel() {

    private val _all = MutableStateFlow<List<MemoryPhoto>>(emptyList())
    private val _albums = MutableStateFlow<List<PhotoAlbum>>(emptyList())
    /** 当前选中相册 id；null = 全部照片（取消 5 张数量限制） */
    private val _albumId = MutableStateFlow<String?>(null)

    val importing = MutableStateFlow(false)

    val selectedAlbumId: StateFlow<String?> = _albumId.asStateFlow()

    val albums: StateFlow<List<PhotoAlbum>> = _albums.asStateFlow()

    /** 全部照片（未过滤），用于计算各相册张数、首页缩略等 */
    val allPhotos: StateFlow<List<MemoryPhoto>> = _all.asStateFlow()

    /** 当前相册（或全部）下的照片，按日期倒序 —— 翻页组直接消费此列表 */
    val photos: StateFlow<List<MemoryPhoto>> = combine(_all, _albumId) { list, aid ->
        val filtered = if (aid.isNullOrBlank()) list else list.filter { it.albumId == aid }
        filtered.sortedByDescending { it.day }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Int> = _all.map { it.size }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0
    )

    /** 各相册封面：无显式封面时用该相册首张（仅前 3 张用于首页速览缩略） */
    fun albumCover(album: PhotoAlbum): MemoryPhoto? {
        val cover = if (album.coverPhotoId != null) _all.value.firstOrNull { it.id == album.coverPhotoId }
        else _all.value.firstOrNull { it.albumId == album.id }
        return cover
    }

    fun reload() {
        viewModelScope.launch {
            _all.value = store.load()
            _albums.value = store.loadAlbums()
        }
    }

    init { reload() }

    fun selectAlbum(id: String?) { _albumId.value = if (id.isNullOrBlank()) null else id }

    /** 批量导入（内容 URI）；v1.0.7：targetAlbumId 非空时直接归入该相册（新建相册后加图场景）。 */
    fun import(uris: List<*>, targetAlbumId: String? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            val real = uris.filterIsInstance<android.net.Uri>()
            store.importAll(real, targetAlbumId)
            reload()
            importing.value = false
        }
    }

    /** 补录一句「当年的话」 */
    fun setNote(id: String, note: String) {
        viewModelScope.launch { store.setNote(id, note); reload() }
    }

    /** 删除照片（原图文件一并删除，仅删这一张） */
    fun delete(id: String) {
        viewModelScope.launch { store.delete(id); reload() }
    }

    /** 设为主页壁纸（静态模式，关闭轮换） */
    fun useAsWallpaper(id: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = store.useAsWallpaper(id)
            onDone(ok)
        }
    }

    /** 用系统相册选来的新图替换该照片图像（保留元数据） */
    fun replacePhoto(id: String, uri: android.net.Uri) {
        viewModelScope.launch {
            store.replacePhoto(id, uri)
            reload()
        }
    }

    /**
     * 新建相册（同名去重由 store 处理）。v1.0.7：创建完成后通过 [onCreated] 回调返回相册，
     * 供 UI 立即选中该相册，实现「新建相册后点加号直接在该相册下加图」。
     */
    fun createAlbum(name: String, onCreated: (PhotoAlbum) -> Unit = {}) {
        viewModelScope.launch {
            val album = store.createAlbum(name)
            reload()
            onCreated(album)
        }
    }

    fun renameAlbum(id: String, name: String) {
        viewModelScope.launch { store.renameAlbum(id, name); reload() }
    }

    /** 删除相册：同步把引用它的照片 albumId 置空（照片不丢） */
    fun deleteAlbum(id: String) {
        viewModelScope.launch {
            store.deleteAlbum(id)
            if (_albumId.value == id) _albumId.value = null
            reload()
        }
    }

    /** 把照片归入/移出相册（albumId=null 即移出） */
    fun setPhotoAlbum(id: String, albumId: String?) {
        viewModelScope.launch { store.setPhotoAlbum(id, albumId); reload() }
    }
}
