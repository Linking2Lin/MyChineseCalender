package lins.applications.mychinesecalender

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lins.applications.appwidget.data.ChineseCalenderRepository
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.CHNDate
import lins.applications.appwidget.model.CHNDateEnity
import java.util.Calendar

class MainViewModel() : ViewModel() {

    private val _lunarDate : MutableState<CHNDate> =  mutableStateOf(CHNDate())
    val lunarDate: State<CHNDate> = _lunarDate


    fun getLunarDate(
        applicationContext : Context
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val repository = ChineseCalenderRepository()
            val calendar = Calendar.getInstance()
            val result =  repository.getLunarDate(
                currentYear = calendar.get(Calendar.YEAR).toString(),
                currentMonth = (calendar.get(Calendar.MONTH) + 1).toString(),
                currentDay = calendar.get(Calendar.DAY_OF_MONTH).toString()
            )
            val db =  Room.databaseBuilder(
                applicationContext,
                AppDataBase::class.java, "database-name"
            ).build()

            if (db.chnDateDao().getById(result.hashCode()) != null){
                db.chnDateDao().deleteById(result.hashCode())
            }

            db.chnDateDao().insertDate(CHNDateEnity.covert(result))

            _lunarDate.value = result
        }
    }
}