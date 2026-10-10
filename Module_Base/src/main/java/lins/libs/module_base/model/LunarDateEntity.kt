package lins.libs.module_base.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 实体，用于本地缓存 HKO API 返回的农历数据。
 * Widget 通过共享仓库优先读取本地值，Receiver 只安排任务，Worker 负责请求同步。
 * HKO 响应没有公历日期字段，date 必须沿用本次查询日期，不可按响应完成时间重新取“今天”。
 * @param date ISO 公历查询日期，也是数据库唯一主键。
 * @param lunarYear HKO 农历年文本，写入前要求非空白。
 * @param lunarDate HKO 农历月日文本，写入前要求非空白。
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
    /**
     * 转成展示模型；公历身份由外层 DateLoadResult 继续携带。
     * @return LunarDateResponse 展示模型，公历主键不混入农历文本。
     */
    fun toResponse(): LunarDateResponse {
        return LunarDateResponse(lunarYear = lunarYear, lunarDate = lunarDate)
    }

    companion object {
        /**
         * 核心字段为空返回 null；dateKey 由调用方用 LocalDate.toString() 提供，保持日期排序格式统一。
         * @param dateKey 请求使用的 ISO 公历日期文本，由 LocalDate.toString() 生成。
         * @param response HKO 返回模型，核心农历字段为空时无法生成有效缓存实体。
         * @return LunarDateEntity；核心字段不完整时返回 null，不制造无效缓存。
         */
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
