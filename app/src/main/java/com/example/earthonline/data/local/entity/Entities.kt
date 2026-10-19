package com.example.earthonline.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 全局个人资料（对应 HTML state.profile + 顶级 birthDate）。
 * 全应用仅一行（id 固定为 1），缺失即视为未初始化（引导页写入）。
 */
@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val avatarKey: String = "",          // "" / "default" / "earth" / "rocket" / "game" / "cat" / "leaf" / "music" / "star"
    /**
     * v1.0.0：上传头像的**原图文件**路径（filesDir/avatar 下的绝对路径）。
     * 原图完整保存（不缩放、不重编码），显示时由 Coil 按控件尺寸采样 —— 见 util/ImageStore。
     * 空 = 未上传（回落到 [avatarKey] 的预设 emoji）。
     */
    val avatarPath: String? = null,
    @Serializable(with = Base64BytesSerializer::class)
    val avatarData: ByteArray? = null,   // 【兼容字段】v1.0.0 之前头像直接存字节；只出不进，备份导出时按需回填
    val gender: String = "",             // "" / "male" / "female" / "walmart"
    val country: String = "",
    val province: String = "",
    val signature: String = "",
    val birthDate: String = "",          // YYYY-MM-DD，空串=未设置（等级=年龄由它计算）
    /**
     * v1.0.4：累计经验值持久化（DB 5->6 新增列，默认 0）。
     * 单一事实源仍是 util/XpRules.totalXp 的派生口径 —— 本列由主页加载时对账校准，
     * 保证导出 JSON 里的 xp 与报告页/小组件展示的 XP 永远一致；旧包导入缺省按 0。
     */
    val xp: Int = 0,
    val customFieldsJson: String = ""    // List<CustomField> 的 JSON
) {
    // ByteArray 默认 equals 用引用，Room 不受影响；这里覆盖以避免警告
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProfileEntity
        return id == other.id && name == other.name && avatarKey == other.avatarKey &&
                gender == other.gender && country == other.country && province == other.province &&
                signature == other.signature && birthDate == other.birthDate &&
                customFieldsJson == other.customFieldsJson && xp == other.xp &&
                (avatarData.contentEquals(other.avatarData) == true)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + avatarKey.hashCode()
        result = 31 * result + (avatarData?.contentHashCode() ?: 0)
        result = 31 * result + gender.hashCode()
        result = 31 * result + country.hashCode()
        result = 31 * result + province.hashCode()
        result = 31 * result + signature.hashCode()
        result = 31 * result + birthDate.hashCode()
        result = 31 * result + xp
        result = 31 * result + customFieldsJson.hashCode()
        return result
    }
}

/**
 * v1.0.4：经验值流水账（DB 5->6 新增表）。
 * 每一笔 XP 进出（任务完成 / 成就解锁 / 自定义里程碑）落一行，amount 带符号（撤销为负）。
 * 纯审计流水：展示口径仍以 XpRules.totalXp 派生值为准，本表不参与计算。
 */
@Serializable
@Entity(
    tableName = "xp_events",
    indices = [Index("date")]   // 按日查流水（报告对齐）；索引名与 MIGRATION_5_6 建表语句一致
)
data class XpEventEntity(
    @PrimaryKey val id: String,
    /** task / ach / custom */
    val kind: String,
    /** 带符号增减量：+10 / -10 / +50 … */
    val amount: Int,
    /** 人类可读原因，如「完成任务：晨跑」 */
    val reason: String,
    /** ISO 时间戳 */
    val createdAt: String,
    /** 日键 YYYY-MM-DD（本地时区，与 activities 口径一致） */
    val date: String
)

/** 任务（支持父子嵌套；对应 HTML state.tasks） */
@Serializable
@Entity(tableName = "tasks", indices = [Index("doneAt")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val category: String = "todo",       // main / side / todo
    val title: String = "",
    val status: String = "planning",     // planning / active / paused / done
    val progress: Int = 0,               // 0-100
    val note: String? = null,
    val dueDate: String? = null,         // 仅 todo 使用，YYYY-MM-DD
    val createdAt: String = "",           // YYYY-MM-DD
    val lastModified: String = "",        // YYYY-MM-DD
    val doneAt: String? = null,          // 最近一次完成时间 ISO；非 done 时为 null（完成任务数唯一口径）
    /**
     * v1.0.5 回收站：软删时间（DB 6->7 新增列，ISO 字符串，null=未删除）。
     * 四端契约（Android canonical）：字段名 deletedAt；软删行【不导出】WebDAV 备份，
     * 回收站内容永不跨端 —— WebDAV 按主键合并语义零改动。30 天后启动时物理清理。
     */
    val deletedAt: String? = null,
    @ColumnInfo(name = "sort_order") val order: Int = 0
)

/** 世界日志 / 灵感闪念（对应 HTML state.memos） */
@Serializable
@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey val id: String,
    val text: String = "",
    /**
     * v1.0.6 笔记系统：Markdown 富文本源（加粗/列表/标题/代码块/图片）。
     * 旧行 markdown=NULL → 读取时回退 [text]；新笔记双写：
     * [text] 保留纯文本兜底（备份兼容 / WebDAV 旧客户端可读），[markdown] 为富文本源。
     */
    val markdown: String? = null,
    val type: String = "note",           // note / important / idea
    val createdAt: String = "",           // ISO
    val deletedAt: String? = null         // v1.0.5 回收站：软删时间 ISO；null=未删除
)

/** 背包物品（对应 HTML state.items） */
@Serializable
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val type: String = "physical",       // virtual / physical（兼容字段）
    val description: String? = null,
    val category: String? = null,        // 自定义分类 id 或空
    val createdAt: String = "",           // YYYY-MM-DD
    val deletedAt: String? = null         // v1.0.5 回收站：软删时间 ISO；null=未删除
)

/** 成就（自动 + 手动；对应 HTML state.achievements） */
@Serializable
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val desc: String = "",
    val type: String = "manual",         // auto / manual
    val autoKey: String? = null,         // 自动成就规则 key
    val unlocked: Boolean = false,
    val unlockedAt: String? = null,      // ISO
    /** 分类 id（见 ui/achievements/AchievementCatalog.kt 的 ACH_CATEGORIES）；
     *  空=未分类（v2 及以前的旧行），UI 上归入「自定义」。v3 迁移新增，可空。 */
    val category: String? = null
)

/** 收藏（对应 HTML state.collections；文件二进制改为沙盒持久化，仅存元信息与 Uri） */
@Serializable
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val category: String? = null,        // 可空=未分类
    val title: String = "",
    val note: String? = null,
    val fileMetaJson: String? = null,    // {name,mime,size} 元信息 JSON
    val fileUri: String? = null,         // 沙盒内持久化文件的 Uri（替代 HTML 会话内 Blob）
    val createdAt: String = "",           // YYYY-MM-DD
    val deletedAt: String? = null         // v1.0.5 回收站：软删时间 ISO；null=未删除
)

/** 足迹地图坐标（对应 HTML state.locations） */
@Serializable
@Entity(tableName = "locations", indices = [Index("date")])
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val date: String = "",               // YYYY-MM-DD
    val note: String? = null,
    val tagsJson: String? = null         // List<String> JSON（多标签）
)

/** 最近动态 feed（对应 HTML state.activities，最多保留 50 条环形裁剪） */
@Serializable
@Entity(tableName = "activities", indices = [Index("time")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val time: String = "",               // ISO
    val kind: String = "",               // ach / task / item / memo
    val title: String = ""
)
