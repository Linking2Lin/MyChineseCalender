package lins.libs.module_base.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 实体，用于本地缓存 HKO API 返回的农历数据。
 * Widget 优先从数据库读取，后台 Worker/Receiver 负责定期刷新。
 */
@Entity(tableName = "lunar_date")
data class LunarDateEntity(
    /** 使用查询日期 (yyyy-MM-dd) 作为主键，保证每天只存一条 */
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "lunar_year")
    val lunarYear: String = "",

    @ColumnInfo(name = "lunar_date")
    val lunarDate: String = "",
) {
    fun toResponse(): LunarDateResponse {
        return LunarDateResponse(lunarYear = lunarYear, lunarDate = lunarDate)
    }

    companion object {
        fun fromResponse(dateKey: String, response: LunarDateResponse): LunarDateEntity? {
            if (response.lunarYear.isBlank() || response.lunarDate.isBlank()) return null
            return LunarDateEntity(
                date = dateKey,
                lunarYear = response.lunarYear,
                lunarDate = response.lunarDate,
            )
        }
    }
}
