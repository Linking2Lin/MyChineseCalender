package lins.applications.appwidget.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import lins.applications.appwidget.dao.CHNDateDao
import lins.applications.appwidget.dao.LunarDateDao
import lins.applications.appwidget.model.CHNDateEntity
import lins.applications.appwidget.model.LunarDateEntity

@Database(
    entities = [CHNDateEntity::class, LunarDateEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDataBase : RoomDatabase() {
    /** 主界面使用的 CHNDate DAO (ChineseCalenderRepository) */
    abstract fun chnDateDao(): CHNDateDao

    /** Widget 后台使用的 LunarDate DAO (HkoRepository) */
    abstract fun lunarDateDao(): LunarDateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDataBase? = null

        /** 
         * 数据库迁移示例，由于后续业务扩展如果需要增加字段/表，可以在这里手写迁移逻辑。
         * 在测试阶段使用 fallbackToDestructiveMigration 尚可，但发布线上后如果有自定义持久化数据
         * 必须使用 addMigrations，以避免清理掉用户数据。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 示例：db.execSQL("ALTER TABLE lunar_date ADD COLUMN new_column TEXT DEFAULT '' NOT NULL")
            }
        }

        fun getInstance(context: Context): AppDataBase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDataBase::class.java,
                    "database-name"
                )
                    // 目前由于只存取可再次请求的网络缓存数据，清库影响不大。
                    // 未来如果存在用户数据（例如日程、笔记），请去除下方 fallbackToDestructiveMigration，
                    // 改用 .addMigrations(MIGRATION_1_2) 等。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}