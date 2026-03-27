package lins.applications.mychinesecalender

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lins.applications.appwidget.data.ChineseCalenderRepository
import lins.applications.appwidget.data.HkoRepository
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.CHNDate
import lins.applications.appwidget.model.CHNDateEnity
import lins.applications.appwidget.model.LunarDateResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class MainViewModel() : ViewModel() {

    private val _lunarDate : MutableState<CHNDate> =  mutableStateOf(CHNDate())
    val lunarDate: State<CHNDate> = _lunarDate

    private val repository = HkoRepository()

    // 内部可变的 StateFlow，用于保存请求结果
    private val _lunarData = MutableStateFlow<LunarDateResponse?>(null)
    // 暴露给 UI 层的不可变 StateFlow
    val lunarData: StateFlow<LunarDateResponse?> = _lunarData.asStateFlow()

    /**
     * 获取今天的农历信息
     */
    fun getTodayLunarInfo() {
        viewModelScope.launch {
            // 1. 获取今天的公历日期，并格式化为 YYYY-MM-DD
            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val dateString = today.format(formatter)

            // 2. 发起网络请求 (由于在 viewModelScope 中，这是在 IO/Default 线程池挂起的，不卡顿UI)
            val result = repository.fetchLunarDate(dateString)

            // 3. 将结果推送到 StateFlow，通知 UI 刷新
            _lunarData.value = result
        }
    }


    fun getLunarDate(
        applicationContext : Context,
        after: () -> Unit
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

            // calendar.timeInMillis

//            if (db.chnDateDao().getById(result.year.hashCode()) != null){
//                db.chnDateDao().deleteById(result.year.hashCode())
//            }

            db.chnDateDao().insertDate(CHNDateEnity.covert(result))

            _lunarDate.value = result
            after()
        }
    }
}