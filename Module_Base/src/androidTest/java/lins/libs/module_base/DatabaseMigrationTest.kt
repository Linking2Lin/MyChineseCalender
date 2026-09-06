package lins.libs.module_base

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import lins.libs.module_base.database.AppDataBase
import lins.libs.module_base.model.CHNDate
import lins.libs.module_base.model.CHNDateEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * 真实 Android SQLite/Room 升级测试，必须在设备或模拟器执行；仅编译 APK 不代表运行通过。
 * 手工构造 v2/v3 历史结构后交由 Room 升级并验证新结构，不使用生产数据库文件名。
 */
class DatabaseMigrationTest {
    @Test fun v2AndV3UpgradePreserveLunarCacheAndDeduplicateValidAlmanac() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (oldVersion in listOf(2, 3)) {
            val name = "migration-$oldVersion-${System.nanoTime()}.db"
            val path = context.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            val table = if (oldVersion == 2) "CHNDateEntity" else "chn_date"
            val columns = if (oldVersion == 2) listOf("year", "lunarDate", "huangLiDate", "huiLiDate", "ganZhiDate", "wuXing", "zhiRiXingShen", "yi", "ji")
                else listOf("year", "lunar_date", "huang_li_date", "hui_li_date", "gan_zhi_date", "wu_xing", "zhi_ri_xing_shen", "yi", "ji")
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                old.execSQL("CREATE TABLE $table (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ${columns.joinToString { "$it TEXT" }})")
                old.execSQL("CREATE TABLE lunar_date (date TEXT NOT NULL PRIMARY KEY, lunar_year TEXT NOT NULL, lunar_date TEXT NOT NULL)")
                old.execSQL("INSERT INTO lunar_date VALUES ('2026-09-06', '丙午年', '七月廿五')")
                for (lunar in listOf("旧缓存", "新缓存", "")) {
                    old.execSQL("INSERT INTO $table (${columns[0]}, ${columns[1]}) VALUES (?, ?)", arrayOf("2026年9月6日 星期日", lunar))
                }
                old.execSQL("INSERT INTO $table (${columns[0]}, ${columns[1]}) VALUES (?, ?)", arrayOf("非法日期", "不可用"))
                old.version = oldVersion
            }
            // 首次 DAO 查询触发打开与迁移；Room 会核验迁移后的表结构，而不仅是检查 SQL 能执行。
            val upgraded = Room.databaseBuilder(context, AppDataBase::class.java, name)
                .addMigrations(AppDataBase.MIGRATION_2_3, AppDataBase.MIGRATION_3_4).build()
            try {
                val date = LocalDate.of(2026, 9, 6)
                assertEquals("新缓存", upgraded.chnDateDao().getByDate(date.toString())?.lunarDate)
                assertEquals("七月廿五", upgraded.lunarDateDao().getByDate(date.toString())?.lunarDate)
                // 升级后连续覆盖同日数据仍只有一行，验证新主键真正生效。
                repeat(5) { upgraded.chnDateDao().insertOrReplace(CHNDateEntity.fromModel(date, CHNDate(year = date.toString(), lunarDate = "更新"))) }
                upgraded.openHelper.readableDatabase.query("SELECT COUNT(*) FROM chn_date").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1, it.getInt(0))
                }
            } finally {
                upgraded.close()
                context.deleteDatabase(name)
            }
        }
    }
}
