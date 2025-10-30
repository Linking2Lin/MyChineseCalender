package lins.applications.appwidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.text.Text
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.CHNDateEnity

class MyAppWidget: GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        val db = Room.databaseBuilder(
            context = context,
            AppDataBase::class.java,"database-name"
        ).build()

        val date =  withContext(Dispatchers.IO){
            db.chnDateDao().getAll().last()

        }


        provideContent {
            GlanceTheme {
                WidgetContent(
                    date
                )
            }
        }
    }

    companion object {
        val ACTION_UPDATE_DATE = "lins.ACTION_UPDATE_DATE"
    }

}

@Composable
fun WidgetContent(date: CHNDateEnity) {


    Text(
        text = date.lunarDate ?: "error",
        modifier = GlanceModifier.background(
            GlanceTheme.colors.primary
        )
    )
}