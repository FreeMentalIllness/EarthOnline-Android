package com.example.earthonline.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.earthonline.data.local.dao.*
import com.example.earthonline.data.local.entity.*

/**
 * v1 -> v2：新增「背包自定义分类」表。
 * 必须是真迁移 —— 一旦走 destructive，用户已录入的任务 / 收藏 / 足迹会被清空。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bag_categories` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`scope` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`)" +
                ")"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bag_categories_scope_sortOrder` " +
                "ON `bag_categories` (`scope`, `sortOrder`)"
        )
    }
}

/**
 * v2 -> v3：成就新增「分类」列。
 * 依旧是真迁移（ADD COLUMN，可空），旧成就行保留、category 为 null，由成就引擎回填。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `achievements` ADD COLUMN `category` TEXT")
    }
}

/**
 * v3 -> v4：头像改为「原图文件 + 路径」（画质不再被压缩）。
 * 真迁移（ADD COLUMN，可空）：老用户库里已经存在的 avatarData 字节不动，
 * 打开资料页时会自动把那一份字节落成文件并回填 avatarPath（见 ProfileViewModel.load）。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `avatarPath` TEXT")
    }
}

/**
 * v4 -> v5：为高频查询列补索引（只加索引，不改列/表，数据零风险）。
 * tasks.doneAt     —— 完成任务数按区间统计（报告页每日聚合）；
 * activities.time  —— 最近动态 / 全部动态按时间倒序与区间过滤；
 * locations.date   —— 足迹按日期区间过滤与年月分组。
 * 索引名与 @Entity(indices=[...]) 声明保持一致（Room schema 校验要求双向匹配）。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_doneAt` ON `tasks` (`doneAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_time` ON `activities` (`time`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_locations_date` ON `locations` (`date`)")
    }
}

/**
 * v5 -> v6：XP 持久化（v1.0.4）。
 * ① profile 加 xp 列（累计经验，NOT NULL DEFAULT 0，可空旧行兼容由 DEFAULT 兜底）；
 * ② 新建 xp_events 流水账表。xp_events.date 建索引 —— 索引名与实体 @Index 声明一致
 *（Room schema 校验要求双向匹配，铁律：迁移里加索引必须实体同声明）。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `xp` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `xp_events` (" +
                "`id` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`reason` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`)" +
                ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_xp_events_date` ON `xp_events` (`date`)")
    }
}

/**
 * v6 -> v7：回收站（v1.0.5）。
 * tasks / memos / items / collections 四表各加 deletedAt TEXT 列（可空，null=未删除）。
 * 注意：任务书里的 5->6 已被 v1.0.4 XP 迁移占用，回收站顺延为 6->7。
 * 四端契约（Android canonical）：软删行不导出 WebDAV 备份，回收站永不跨端。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `deletedAt` TEXT")
        db.execSQL("ALTER TABLE `memos` ADD COLUMN `deletedAt` TEXT")
        db.execSQL("ALTER TABLE `items` ADD COLUMN `deletedAt` TEXT")
        db.execSQL("ALTER TABLE `collections` ADD COLUMN `deletedAt` TEXT")
    }
}

/**
 * v7 -> v8：笔记系统（v1.0.6）。
 * 仅 memos 表加 markdown TEXT 列（可空，null=旧行回退纯文本 [text]）。
 * 全加列、零破坏：旧客户端导出的备份不含该字段，新客户端按主键合并语义不变；
 * 四端契约（Android canonical）零改动。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `memos` ADD COLUMN `markdown` TEXT")
    }
}

/**
 * 主数据库（对应 HTML 的单一主存档键 `earth_data`，本端归一化为多表）。
 * version=8；迁移链 MIGRATION_1_2 … MIGRATION_7_8
 *（fallbackToDestructiveMigration 仅作无匹配迁移时的兜底）。
 */
@Database(
    entities = [
        ProfileEntity::class,
        TaskEntity::class,
        MemoEntity::class,
        ItemEntity::class,
        AchievementEntity::class,
        CollectionEntity::class,
        LocationEntity::class,
        ActivityEntity::class,
        BagCategoryEntity::class,
        XpEventEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun taskDao(): TaskDao
    abstract fun memoDao(): MemoDao
    abstract fun itemDao(): ItemDao
    abstract fun achievementDao(): AchievementDao
    abstract fun collectionDao(): CollectionDao
    abstract fun locationDao(): LocationDao
    abstract fun activityDao(): ActivityDao
    abstract fun bagCategoryDao(): BagCategoryDao
    abstract fun xpEventDao(): XpEventDao

    companion object {
        private const val DB_NAME = "earth_online.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}
