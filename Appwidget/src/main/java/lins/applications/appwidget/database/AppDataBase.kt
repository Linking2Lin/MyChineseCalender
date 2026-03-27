package lins.applications.appwidget.database

import androidx.room.Database
import androidx.room.RoomDatabase
import lins.applications.appwidget.dao.CHNDateDao
import lins.applications.appwidget.dao.LunarDateDao
import lins.applications.appwidget.model.CHNDateEnity
import lins.applications.appwidget.model.LunarDateEntity

@Database(
    entities = [CHNDateEnity::class, LunarDateEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDataBase : RoomDatabase() {
    /** 主界面使用的 CHNDate DAO (ChineseCalenderRepository) */
    abstract fun chnDateDao(): CHNDateDao

    /** Widget 后台使用的 LunarDate DAO (HkoRepository) */
    abstract fun lunarDateDao(): LunarDateDao
}