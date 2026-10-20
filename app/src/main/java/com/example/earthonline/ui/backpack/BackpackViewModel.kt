package com.example.earthonline.ui.backpack

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.BagCategoryEntity
import com.example.earthonline.data.local.entity.CollectionEntity
import com.example.earthonline.data.local.entity.ItemEntity
import com.example.earthonline.data.local.entity.SCOPE_COLLECTION
import com.example.earthonline.data.local.entity.SCOPE_ITEM
import com.example.earthonline.data.model.CollectionFileMeta
import com.example.earthonline.data.repository.ActivityRepository
import com.example.earthonline.data.repository.BagCategoryRepository
import com.example.earthonline.data.repository.CollectionRepository
import com.example.earthonline.data.repository.ItemRepository
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.todayStr
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

/**
 * 背包 ViewModel：物品 CRUD + 收藏 CRUD（含文件附件）。
 * 文件附件处理（对应 HTML 仅会话内 Blob、刷新丢失）：
 *   - 复制源 Uri 到应用私有目录 files/collections/ 持久化（离线可用）
 *   - 元信息写入 CollectionEntity.fileMetaJson，相对路径写入 fileUri
 *   - 打开时通过 FileProvider 生成 content:// 共享给其它 App
 */
@HiltViewModel
class BackpackViewModel @Inject constructor(
    private val itemRepo: ItemRepository,
    private val collectionRepo: CollectionRepository,
    private val activityRepo: ActivityRepository,
    private val categoryRepo: BagCategoryRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val items = itemRepo.observeAll().stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList()
    )
    val collections = collectionRepo.observeAll().stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList()
    )

    /** 物品分类 / 收藏分类（两套独立） */
    val itemCategories = categoryRepo.observeByScope(SCOPE_ITEM).stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList()
    )
    val collectionCategories = categoryRepo.observeByScope(SCOPE_COLLECTION).stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList()
    )

    /** 分类操作的错误提示（重名等），展示后由 UI 调用 [clearCategoryError] 清掉 */
    private val _categoryError = MutableStateFlow<String?>(null)
    val categoryError: StateFlow<String?> = _categoryError.asStateFlow()

    fun clearCategoryError() { _categoryError.value = null }

    fun addItem(name: String, type: String, desc: String?, category: String?) {
        val n = name.trim()
        if (n.isBlank()) return
        viewModelScope.launch {
            itemRepo.insert(
                ItemEntity(
                    id = uid("item"),
                    name = n,
                    type = type,
                    description = desc?.takeIf { it.isNotBlank() },
                    category = category?.takeIf { it.isNotBlank() },
                    createdAt = todayStr()
                )
            )
            activityRepo.add(ActivityEntity(id = uid("act"), time = nowIso(), kind = "item", title = "拾取了物品：$n"))
        }
    }

    fun deleteItem(id: String) = viewModelScope.launch { itemRepo.delete(id) }

    /**
     * v1.0.4：编辑已有物品。此前条目保存后只能删除重建（点击卡片无响应），
     * 现在卡片可点开编辑对话框，仅覆盖名称 / 类型 / 故事 / 分类四个字段，
     * createdAt 与 id 保持不变（WebDAV 按主键合并的语义不受影响）。
     */
    fun updateItem(target: ItemEntity, name: String, type: String, desc: String?, category: String?) {
        val n = name.trim()
        if (n.isBlank()) return
        viewModelScope.launch {
            itemRepo.update(
                target.copy(
                    name = n,
                    type = type,
                    description = desc?.takeIf { it.isNotBlank() },
                    category = category?.takeIf { it.isNotBlank() }
                )
            )
            activityRepo.add(ActivityEntity(id = uid("act"), time = nowIso(), kind = "item", title = "整理了物品：$n"))
        }
    }

    /**
     * v1.0.4：编辑已有收藏。标题 / 备注 / 分类可改；文件附件保持原样
     *（替换附件涉及旧文件清理与元信息重写，本期不做，避免半删状态）。
     */
    fun updateCollection(target: CollectionEntity, title: String, note: String?, category: String?) {
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            collectionRepo.update(
                target.copy(
                    title = t,
                    note = note?.takeIf { it.isNotBlank() },
                    category = category?.takeIf { it.isNotBlank() }
                )
            )
            activityRepo.add(ActivityEntity(id = uid("act"), time = nowIso(), kind = "item", title = "整理了收藏：$t"))
        }
    }

    /**
     * 编辑收藏时「替换文件附件」：复制新文件到私有目录、删除旧文件、重写 fileUri/fileMetaJson；
     * sourceUri 为 null 表示不换文件，沿用原附件。标题/备注/分类照常更新。
     */
    fun replaceCollectionFile(
        target: CollectionEntity,
        title: String,
        note: String?,
        category: String?,
        sourceUri: Uri?
    ) {
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            val (rel, metaJson) = if (sourceUri != null) copyUriToPrivate(sourceUri, target.id)
            else (target.fileUri to target.fileMetaJson)
            // 替换了文件才删旧的，避免误删仍在用的附件
            if (sourceUri != null && target.fileUri != null && target.fileUri != rel) {
                File(appContext.filesDir, target.fileUri).delete()
            }
            collectionRepo.update(
                target.copy(
                    title = t,
                    note = note?.takeIf { it.isNotBlank() },
                    category = category?.takeIf { it.isNotBlank() },
                    fileUri = rel,
                    fileMetaJson = metaJson
                )
            )
            activityRepo.add(ActivityEntity(id = uid("act"), time = nowIso(), kind = "item", title = "替换了收藏文件：$t"))
        }
    }

    fun addCollection(title: String, note: String?, category: String?, sourceUri: Uri?) {
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            val id = uid("col")
            var fileUri: String? = null
            var fileMetaJson: String? = null
            if (sourceUri != null) {
                val (rel, metaJson) = copyUriToPrivate(sourceUri, id)
                fileUri = rel
                fileMetaJson = metaJson
            }
            collectionRepo.insert(
                CollectionEntity(
                    id = id,
                    category = category?.takeIf { it.isNotBlank() },
                    title = t,
                    note = note?.takeIf { it.isNotBlank() },
                    fileUri = fileUri,
                    fileMetaJson = fileMetaJson,
                    createdAt = todayStr()
                )
            )
            activityRepo.add(ActivityEntity(id = uid("act"), time = nowIso(), kind = "item", title = "收藏了：$t"))
        }
    }

    fun deleteCollection(id: String) {
        viewModelScope.launch {
            collections.value.firstOrNull { it.id == id }?.fileUri?.let { rel ->
                File(appContext.filesDir, rel).delete()
            }
            collectionRepo.delete(id)
        }
    }

    // ---------- 自定义分类 ----------

    fun addCategory(scope: String, name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        viewModelScope.launch {
            if (categoryRepo.countSameName(scope, n) > 0) {
                _categoryError.value = "已存在同名分类"
                return@launch
            }
            val next = (categoryRepo.maxOrder(scope) ?: -1) + 1
            categoryRepo.insert(
                BagCategoryEntity(id = uid("cat"), name = n, scope = scope, sortOrder = next)
            )
            _categoryError.value = null
        }
    }

    /**
     * 重命名分类：除了改分类表，还要同步条目上存的分类名。
     * 条目存的是分类名字符串而非 id，不同步的话改名后条目会「失联」——
     * 表现为条目上还挂着旧名字，但筛选栏里已经没有这个分类可选。
     */
    fun renameCategory(c: BagCategoryEntity, name: String) {
        val n = name.trim()
        if (n.isBlank() || n == c.name) return
        viewModelScope.launch {
            if (categoryRepo.countSameName(c.scope, n, c.id) > 0) {
                _categoryError.value = "已存在同名分类"
                return@launch
            }
            categoryRepo.update(c.copy(name = n))
            if (c.scope == SCOPE_ITEM) itemRepo.renameCategory(c.name, n)
            else collectionRepo.renameCategory(c.name, n)
            _categoryError.value = null
        }
    }

    /** 删除分类：先把引用它的条目回落「未分类」，再删分类行（顺序不能反） */
    fun deleteCategory(c: BagCategoryEntity) {
        viewModelScope.launch {
            if (c.scope == SCOPE_ITEM) itemRepo.clearCategory(c.name)
            else collectionRepo.clearCategory(c.name)
            categoryRepo.delete(c.id)
        }
    }

    /** 生成用于分享给其它 App 打开收藏文件的 Intent（FileProvider 暴露私有文件） */
    fun getOpenIntent(entity: CollectionEntity): Intent? {
        val rel = entity.fileUri ?: return null
        val file = File(appContext.filesDir, rel)
        if (!file.exists()) return null
        val meta = runCatching {
            entity.fileMetaJson?.let { Json.decodeFromString(CollectionFileMeta.serializer(), it) }
        }.getOrNull()
        val mime = meta?.mime?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 复制源 Uri 到应用私有目录，返回 (相对路径, 元信息 JSON) */
    private fun copyUriToPrivate(uri: Uri, id: String): Pair<String, String> {
        val cr = appContext.contentResolver
        val rawName = runCatching {
            cr.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
        }.getOrNull() ?: "file"
        val safeName = rawName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val dir = File(appContext.filesDir, "collections").apply { mkdirs() }
        val dest = File(dir, "${id}_$safeName")
        cr.openInputStream(uri)?.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        val meta = CollectionFileMeta(name = rawName, mime = cr.getType(uri) ?: "", size = dest.length())
        return "collections/${id}_$safeName" to Json.encodeToString(CollectionFileMeta.serializer(), meta)
    }
}