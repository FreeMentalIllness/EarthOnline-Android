package com.example.earthonline.ui.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.local.entity.ProfileEntity
import com.example.earthonline.data.model.CustomField
import com.example.earthonline.data.repository.AchievementRepository
import com.example.earthonline.data.repository.LocationRepository
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.data.repository.ProfileRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.util.GrowthStreak
import com.example.earthonline.util.ImageStore
import com.example.earthonline.util.ShareCardData
import com.example.earthonline.util.XpRules
import com.example.earthonline.util.lifeStatsOf
import com.example.earthonline.util.localDayOf
import com.example.earthonline.util.todayStr
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 个人资料页 ViewModel（对应 HTML 的 profile.js 编辑表单）。
 * 编辑态用 Compose State 持有，保存时整行 upsert 回 ProfileEntity（id 固定 1）。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    // v1.0.0：分享卡片要显示「成就数 / 完成任务数」，这两个数不在 profile 表里，
    // 由各自的仓储提供（Hilt 已注入，不用在 UI 层再开一个 VM）
    private val achRepo: AchievementRepository,
    private val taskRepo: TaskRepository,
    // v1.0.3：分享卡片扩展（连续记录 / 本月关键词 / 称号）
    private val memoRepo: MemoRepository,
    private val locationRepo: LocationRepository,
    private val settings: SettingsDataStore,
    // v1.0.0：头像改成「原图文件」后，VM 需要读写私有目录（旧字节迁移 / 分享卡片取图）
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val profile = repo.observe()

    /* ⚠️ 这里必须用 Eagerly 而不是 WhileSubscribed：
       分享卡片是「点一下按钮」时才读这三个值，UI 平时并不 collect 它们。
       用 WhileSubscribed 的话没有任何订阅者，flow 根本没启动，.value 会永远停在初始值 0
       —— 卡片上就成了「成就 0/0、完成任务 0」，看起来像数据丢了。 */
    val unlockedAch: StateFlow<Int> =
        achRepo.unlockedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val totalAch: StateFlow<Int> =
        achRepo.totalCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val doneTasks: StateFlow<Int> =
        taskRepo.doneCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // 当前库内行（用于 copy 保留未编辑字段）
    private var loaded: ProfileEntity? = null

    init {
        // 称号独立于 profile 表，存 DataStore；collect 保持编辑态与持久化同步
        viewModelScope.launch {
            settings.customTitle.collect { customTitle = it }
        }
    }

    var name by mutableStateOf("")
    var avatarKey by mutableStateOf("")
    var gender by mutableStateOf("")
    var country by mutableStateOf("中国")
    var province by mutableStateOf("")
    var signature by mutableStateOf("")
    /** v1.0.3：自定义称号（空 = 默认「旅行者」），持久化在 DataStore */
    var customTitle by mutableStateOf("")
    var birthDate by mutableStateOf("")
    var customFields by mutableStateOf(listOf<CustomField>())
    /**
     * v1.0.0：头像原图文件路径（filesDir/avatar 下）。原图完整保存，不压缩。
     * [avatarData] 只作为兼容字段保留（旧库里的字节），新头像一律只写路径。
     */
    var avatarPath: String? by mutableStateOf(null)
    var avatarData: ByteArray? = null

    val canSave: Boolean
        get() = name.isNotBlank()

    /** 用库内行初始化编辑态；仅在库数据变化时调用，避免覆盖用户输入。
     *  v1.0.6：自定义字段 JSON 解析较重（字段多时），移出主线程，避免首启填资料页卡顿 */
    suspend fun load(p: ProfileEntity) {
        loaded = p
        name = p.name
        avatarKey = p.avatarKey
        gender = p.gender
        country = p.country
        province = p.province
        signature = p.signature
        birthDate = p.birthDate
        avatarPath = p.avatarPath
        avatarData = p.avatarData
        customFields = withContext(Dispatchers.Default) {
            runCatching {
                Json.decodeFromString<List<CustomField>>(p.customFieldsJson)
            }.getOrDefault(emptyList())
        }

        /* v1.0.0 旧数据迁移：库里只有字节、没有文件路径时（v1.0.0 之前存的头像），
           把那份字节原样落成文件并回填 avatarPath。
           字节本身保留不动 —— 备份导出的兼容逻辑还要用它。 */
        if (p.avatarPath.isNullOrBlank() && p.avatarData != null && p.avatarData!!.isNotEmpty()) {
            val legacy = p.avatarData ?: return
            viewModelScope.launch(Dispatchers.IO) {
                val file = ImageStore.saveBytes(
                    ImageStore.avatarDir(appContext), "avatar", legacy, "jpg"
                ) ?: return@launch
                repo.upsert(p.copy(avatarPath = file.absolutePath))
            }
        }
    }

    /**
     * v1.0.3：组装分享卡片数据（改为 suspend —— 需要现算连续天数与本月记录数）。
     * 用**当前编辑态**的值（用户可能刚改完名字还没保存就点了分享），
     * 编辑态为空时回落到库内已保存的值。
     */
    suspend fun buildCardData(template: Int = 1): ShareCardData {
        val birth = birthDate.takeIf { it.isNotBlank() } ?: loaded?.birthDate.orEmpty()
        val life = lifeStatsOf(birth)

        // 连续记录与本月记录数：日志 ∪ 完成任务 ∪ 足迹三种记录日合并计算
        val recordDays = HashSet<String>()
        memoRepo.observeAll().first().forEach { localDayOf(it.createdAt)?.let(recordDays::add) }
        taskRepo.observeAll().first()
            .filter { it.status == "done" }
            .forEach { it.doneAt?.let { iso -> localDayOf(iso)?.let(recordDays::add) } }
        locationRepo.observeAll().first().forEach { recordDays.add(it.date.take(10)) }
        val streak = GrowthStreak.currentStreak(recordDays)
        val monthPrefix = todayStr().take(7)
        val monthCount = recordDays.count { it.startsWith(monthPrefix) }

        val customTitle = settings.customTitle.first()
        return ShareCardData(
            name = name.takeIf { it.isNotBlank() } ?: loaded?.name.orEmpty(),
            level = life.age,
            levelProgress = life.progress,
            achievements = unlockedAch.value,
            totalAchievements = totalAch.value,
            tasksDone = doneTasks.value,
            signature = signature.takeIf { it.isNotBlank() } ?: loaded?.signature.orEmpty(),
            title = XpRules.titleFor(customTitle),
            streakDays = streak,
            monthKeyword = if (monthCount > 0) "$monthCount 次记录" else "",
            template = template
        )
    }

    /**
     * v1.0.0：设置上传头像 —— 只记**原图文件路径**，不动字节、不做压缩。
     * 旧文件不在这里删：用户可能点完就返回不保存，删了就真没了；
     * 真正的旧文件清理放在 [save]（确认落库之后再删，见下）。
     */
    fun setUploadedAvatarPath(path: String) {
        avatarPath = path
        avatarData = null
        avatarKey = "" // 上传优先；清空预设 key
    }

    fun selectPresetAvatar(key: String) {
        avatarKey = key
        avatarData = null
        avatarPath = null
    }

    /** 重置为默认头像（预设 emoji）：清掉路径与字节 */
    fun resetAvatar() {
        avatarKey = ""
        avatarData = null
        avatarPath = null
    }

    /** 分享卡片用：拿到头像文件（旧字节会自动迁移成临时文件，保证卡片上有图） */
    suspend fun avatarFile(): File? = withContext(Dispatchers.IO) {
        avatarPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }?.let { return@withContext it }
        val bytes = avatarData
        if (bytes == null || bytes.isEmpty()) return@withContext null
        ImageStore.saveBytes(ImageStore.avatarDir(appContext), "avatar", bytes, "jpg")
    }

    fun addCustomField() {
        if (customFields.size < 20) customFields = customFields + CustomField(id = "cf_${System.currentTimeMillis()}")
    }

    fun updateCustomField(idx: Int, label: String, value: String) {
        val list = customFields.toMutableList()
        if (idx in list.indices) {
            list[idx] = list[idx].copy(label = label.take(30), value = value.take(200))
            customFields = list
        }
    }

    fun removeCustomField(idx: Int) {
        customFields = customFields.toMutableList().also { it.removeAt(idx) }
    }

    fun save() {
        viewModelScope.launch {
            val base = loaded ?: ProfileEntity()
            // 头像换过了 -> 落库成功后清掉被替换掉的旧文件（确认写库再删，失败也不丢图）
            val oldPath = base.avatarPath
            val newPath = avatarPath
            if (!oldPath.isNullOrBlank() && oldPath != newPath) {
                ImageStore.deleteFile(oldPath)
            }
            repo.upsert(
                base.copy(
                    name = name,
                    avatarKey = avatarKey,
                    avatarPath = newPath,
                    avatarData = if (newPath.isNullOrBlank()) avatarData else null,
                    gender = gender,
                    country = country,
                    province = province,
                    signature = signature.take(200),
                    birthDate = birthDate,
                    customFieldsJson = Json.encodeToString(ListSerializer(CustomField.serializer()), customFields)
                )
            )
            // v1.0.3：称号随保存一起落盘（DataStore）
            settings.setCustomTitle(customTitle)
        }
    }
}
