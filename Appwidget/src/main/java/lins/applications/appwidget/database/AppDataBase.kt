package lins.applications.appwidget.database

import androidx.room.Database
import androidx.room.RoomDatabase
import lins.applications.appwidget.dao.CHNDateDao
import lins.applications.appwidget.model.CHNDate
import lins.applications.appwidget.model.CHNDateEnity

@Database(entities = [CHNDateEnity::class], version = 1, exportSchema = false)
abstract class AppDataBase : RoomDatabase() {
    abstract fun chnDateDao() : CHNDateDao
}