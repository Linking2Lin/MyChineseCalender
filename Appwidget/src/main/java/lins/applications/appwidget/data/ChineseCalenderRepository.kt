package lins.applications.appwidget.data

import android.util.Log
import com.elvishew.xlog.XLog
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import lins.applications.appwidget.model.CHNDate
import lins.libs.module_base.Logger

class ChineseCalenderRepository {
    private val TAG = "ChineseCalenderReposito"

    companion object {
        /**
         * https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php?date=[2024-08-21]
         * https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php?date=2024-08-21
         *
         */
        const val BASE_URL =
            "https://api.tiax.cn/almanac/?"
            //"http://api.tiax.cn/almanac/?year=2023&month=3&day=2"
    }

    private suspend fun getDateString(
        currentYear: String,
        currentMonth: String,
        currentDay: String
    ): String {
        val body = KtorClient.client.get(BASE_URL + "year=$currentYear&month=$currentMonth&day=$currentDay"){

        }.bodyAsText(

        )
        Logger.d(TAG, "getDateString: $body")

        return body
    }



    suspend fun getLunarDate(
        currentYear: String,
        currentMonth: String,
        currentDay: String
    ) : CHNDate {
        return withContext(Dispatchers.IO) {
           return@withContext runCatching {
                val dataString = getDateString(
                    currentDay = currentDay,
                    currentMonth = currentMonth,
                    currentYear = currentYear
                )
                Logger.d(TAG, "getLunarDate:  + $dataString")
                Json.decodeFromString<CHNDate>(dataString)
            }.getOrNull() ?: CHNDate()
//            }.onFailure {
//                Logger.d(TAG, "getLunarDate: onFailure " + it.stackTraceToString())
//            }.onSuccess {
//                Logger.d(TAG, "getLunarDate onSuccess : $it")
//            }
        }
    }
}

