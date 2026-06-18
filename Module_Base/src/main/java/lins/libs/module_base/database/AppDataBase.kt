package lins.libs.module_base.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import lins.libs.module_base.dao.CHNDateDao
import lins.libs.module_base.dao.LunarDateDao
import lins.libs.module_base.model.CHNDateEntity
import lins.libs.module_base.model.LunarDateEntity

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
         * 获取数据库单例。
         * 注意：未来如果存在用户数据（例如日程、笔记），请去除 fallbackToDestructiveMigration，
         * 改用 .addMigrations(...) 以避免清理掉用户数据。
         */
        fun getInstance(context: Context): AppDataBase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDataBase::class.java,
                    "chn_calendar.db"
                )
                    // 目前由于只存取可再次请求的网络缓存数据，清库影响不大。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
