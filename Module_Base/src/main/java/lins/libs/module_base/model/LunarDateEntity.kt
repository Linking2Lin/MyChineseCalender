package lins.libs.module_base.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 实体，用于本地缓存 HKO API 返回的农历数据。
 * Widget 通过共享仓库优先读取本地值，Receiver 只安排任务，Worker 负责请求同步。
 * HKO 响应没有公历日期字段，date 必须沿用本次查询日期，不可按响应完成时间重新取“今天”。
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
    /** 转成展示模型；公历身份由外层 DateLoadResult 继续携带。 */
    fun toResponse(): LunarDateResponse {
        return LunarDateResponse(lunarYear = lunarYear, lunarDate = lunarDate)
    }

    companion object {
        /** 核心字段为空返回 null；dateKey 由调用方用 LocalDate.toString() 提供，保持日期排序格式统一。 */
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
