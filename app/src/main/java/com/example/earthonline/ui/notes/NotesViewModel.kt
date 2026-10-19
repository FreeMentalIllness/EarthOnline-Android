package com.example.earthonline.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * 笔记系统 ViewModel（v1.0.6）。
 * 笔记即原「世界日志 / 灵感」的升级形态：memos 表新增 markdown 列承载富文本，
 * [text] 保留纯文本兜底（备份 / WebDAV 旧客户端可读）。
 * 过滤、搜索、增改删均复用既有 MemoRepository（软删走回收站）。
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val memoRepo: MemoRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** all / note / important / idea */
    private val _type = MutableStateFlow("all")
    val type: StateFlow<String> = _type.asStateFlow()

    val notes: StateFlow<List<MemoEntity>> = combine(memoRepo.observeAll(), _query, _type) { list, q, t ->
        list.filter { m ->
            (t == "all" || m.type == t) &&
                (q.isBlank() || m.text.contains(q, true) || (m.markdown ?: "").contains(q, true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setType(t: String) { _type.value = t }

    /**
     * 保存笔记（新建或更新）。id 为空则新建；保留原 createdAt（编辑不改创建时间）。
     * markdown 为空时写 null，回退 [MemoEntity.text] 纯文本。
     * @return 笔记 id
     */
    suspend fun saveNote(id: String?, type: String, text: String, markdown: String): String {
        val eid = id ?: uid("memo")
        val existing = if (id != null) memoRepo.observeAll().first().firstOrNull { it.id == id } else null
        val created = existing?.createdAt ?: nowIso()
        memoRepo.insert(
            MemoEntity(
                id = eid,
                text = text,
                markdown = markdown.ifBlank { null },
                type = type,
                createdAt = created,
                deletedAt = null
            )
        )
        return eid
    }

    suspend fun getNote(id: String): MemoEntity? =
        memoRepo.observeAll().first().firstOrNull { it.id == id }

    /** 删除走回收站软删（与全局统一） */
    suspend fun deleteNote(id: String) = memoRepo.delete(id)
}
