package lins.libs.module_base.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import lins.libs.module_base.dao.CHNDateDao
import lins.libs.module_base.dao.LunarDateDao
import lins.libs.module_base.model.CHNDateEntity
import lins.libs.module_base.model.LunarDateEntity

@Database(
    entities = [CHNDateEntity::class, LunarDateEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDataBase : RoomDatabase() {
    /** 主界面使用的 CHNDate DAO（来源于通用黄历接口数据） */
    abstract fun chnDateDao(): CHNDateDao

    /** Widget 后台使用的 LunarDate DAO (HkoRepository) */
    abstract fun lunarDateDao(): LunarDateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDataBase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建新表（snake_case 列名）
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chn_date` (
                        `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `year` TEXT,
                        `lunar_date` TEXT,
                        `huang_li_date` TEXT,
                        `hui_li_date` TEXT,
                        `gan_zhi_date` TEXT,
                        `wu_xing` TEXT,
                        `zhi_ri_xing_shen` TEXT,
                        `yi` TEXT,
                        `ji` TEXT
                    )
                    """.trimIndent()
                )
                // 2. 把旧表数据迁移到新表（列名映射：驼峰 → 下划线）
                db.execSQL(
                    """
                    INSERT INTO `chn_date` (`uid`, `year`, `lunar_date`, `huang_li_date`, `hui_li_date`, `gan_zhi_date`, `wu_xing`, `zhi_ri_xing_shen`, `yi`, `ji`)
                    SELECT `uid`, `year`, `lunarDate`, `huangLiDate`, `huiLiDate`, `ganZhiDate`, `wuXing`, `zhiRiXingShen`, `yi`, `ji`
                    FROM `CHNDateEntity`
                    """.trimIndent()
                )
                // 3. 删除旧表
                db.execSQL("DROP TABLE IF EXISTS `CHNDateEntity`")
            }
        }

        /**
         * 获取数据库单例。
         */
        fun getInstance(context: Context): AppDataBase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDataBase::class.java,
                    "chn_calendar.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
