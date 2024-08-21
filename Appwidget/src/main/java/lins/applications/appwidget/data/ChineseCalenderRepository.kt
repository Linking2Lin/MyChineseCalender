package lins.applications.appwidget.data

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.client.utils.EmptyContent.headers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import lins.applications.appwidget.model.CHNDate

class ChineseCalenderRepository {

    companion object {
        /**
         * https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php?date=[2024-08-21]
         * https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php?date=2024-08-21
         *
         */
        const val BASE_URL =
            "https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php?date=YYYY-MM-DD"
    }

    private suspend fun getDateString(
        currentDate: String
    ): String {
        return KtorClient.client.get(BASE_URL.replace("YYYY-MM-DD", currentDate)){

        }.bodyAsText()
    }



    suspend fun getLunarDate(
        currentDate: String
    ) {
        return withContext(Dispatchers.IO) {
            runCatching {
                val dataString = getDateString(currentDate)
                println(dataString)
                Json.decodeFromString<CHNDate>(dataString)
            }
        }
    }
}

