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
import lins.libs.module_base.model.CHNDate

/**
 * 应用唯一的 Room 数据库，分别缓存主页完整黄历和小组件 HKO 农历。
 *
 * 文件名 chn_calendar.db 属于已安装版本的持久化契约，修改会打开新库而不是升级旧库。
 * 调整 Entity/索引时须增加版本号、添加迁移、更新 schemas 导出并执行设备升级测试；
 * 当前不使用破坏性回退，以免迁移缺失时静默清空数据。
 */
@Database(
    entities = [CHNDateEntity::class, LunarDateEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDataBase : RoomDatabase() {
    /** 主界面使用的 CHNDate DAO（来源于通用黄历接口数据） */
    abstract fun chnDateDao(): CHNDateDao

    /** Widget 后台使用的 LunarDate DAO (HkoRepository) */
    abstract fun lunarDateDao(): LunarDateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDataBase? = null

        /** v2 → v3：将旧表名及驼峰列名改为显式下划线命名，保留 uid 与原有字段。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chn_date_new` (
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
                db.execSQL(
                    """
                    INSERT INTO `chn_date_new` (`uid`, `year`, `lunar_date`, `huang_li_date`, `hui_li_date`, `gan_zhi_date`, `wu_xing`, `zhi_ri_xing_shen`, `yi`, `ji`)
                    SELECT `uid`, `year`, `lunarDate`, `huangLiDate`, `huiLiDate`, `ganZhiDate`, `wuXing`, `zhiRiXingShen`, `yi`, `ji`
                    FROM `CHNDateEntity`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `CHNDateEntity`")
                db.execSQL("ALTER TABLE `chn_date_new` RENAME TO `chn_date`")
            }
        }

        /**
         * v3 → v4：用独立 ISO 日期替代自增 uid。同日重复数据保留最后一条有效记录，
         * 无法解析日期或缺少农历的历史缓存无法安全归属某日，因此不迁入；HKO 表保持原样。
         * 从 v2 安装升级时由 Room 依次运行 2→3、3→4，而不是跳过中间字段转换。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE `chn_date_v4` (
                        `date` TEXT NOT NULL PRIMARY KEY, `year` TEXT, `lunar_date` TEXT,
                        `huang_li_date` TEXT, `hui_li_date` TEXT, `gan_zhi_date` TEXT,
                        `wu_xing` TEXT, `zhi_ri_xing_shen` TEXT, `yi` TEXT, `ji` TEXT
                    )
                """.trimIndent())
                // 按 uid 升序遍历并替换写入，使最后有效值获胜；后来的无效值不会擦掉有效缓存。
                db.query("SELECT year, lunar_date, huang_li_date, hui_li_date, gan_zhi_date, wu_xing, zhi_ri_xing_shen, yi, ji FROM chn_date ORDER BY uid ASC").use { cursor ->
                    while (cursor.moveToNext()) {
                        // 数组顺序对应上方 SELECT 及新表列顺序，修改任何一处需同步核对 INSERT。
                        val values = (0..8).map { if (cursor.isNull(it)) null else cursor.getString(it) }
                        val date = CHNDate.parseGregorianDate(values[0]) ?: continue
                        if (values[1].isNullOrBlank()) continue
                        db.execSQL("INSERT OR REPLACE INTO chn_date_v4 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            (listOf(date.toString()) + values).toTypedArray())
                    }
                }
                db.execSQL("DROP TABLE chn_date")
                db.execSQL("ALTER TABLE chn_date_v4 RENAME TO chn_date")
            }
        }

        /**
         * 进程内双重检查单例。锁内必须再次检查 INSTANCE，避免并发首访各建一个数据库。
         * 使用 applicationContext 避免持有 Activity；build 只构建实例，实际打开/迁移可在首次查询时发生。
         */
        fun getInstance(context: Context): AppDataBase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDataBase::class.java,
                    "chn_calendar.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
