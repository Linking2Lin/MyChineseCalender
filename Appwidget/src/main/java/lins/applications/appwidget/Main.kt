package lins.applications.appwidget

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lins.applications.appwidget.data.ChineseCalenderRepository
import lins.applications.appwidget.model.CHNDate

fun main() {
    /*val str = """
        {"LunarYear":"甲辰年，龍","LunarDate":"七月十八"}
    """.trimIndent()

    val chnDate = Json.decodeFromString<CHNDate>(str)
    println(chnDate.toString())

    println(Json.encodeToString(chnDate))*/

    runBlocking {
        val repository = ChineseCalenderRepository()
        val data = repository.getLunarDate("2024-08-21")
        println(data.toString())
    }

}