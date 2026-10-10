package lins.libs.module_base.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Room v4 完整黄历缓存。date 是 ISO 公历主键，与接口返回的 year 展示文本分离。
 * 同日刷新替换整行，避免按请求完成顺序取“最新记录”导致旧日期冒充今天。
 * 变更列名、可空性或主键时须同步维护 AppDataBase 迁移及 schemas。
 * @param date ISO 公历主键，由本次查询日期生成。
 * @param year 接口的公历展示文本，不能代替 date 主键。
 * @param lunarDate 农历展示文本，写入前要求非空白。
 * @param huangLiDate 黄历日期说明，null 保留“接口未提供”的含义。
 * @param huiLiDate 回历日期说明，可缺省。
 * @param ganZhiDate 干支日期说明，可缺省。
 * @param wuXing 五行纳音说明，可缺省。
 * @param zhiRiXingShen 值日星神说明，可缺省。
 * @param yi 宜事项原文，可缺省。
 * @param ji 忌事项原文，可缺省。
 */
@Entity(tableName = "chn_date")
data class CHNDateEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "year") val year: String?,
    @ColumnInfo(name = "lunar_date") val lunarDate: String?,
    @ColumnInfo(name = "huang_li_date") val huangLiDate: String?,
    @ColumnInfo(name = "hui_li_date") val huiLiDate: String?,
    @ColumnInfo(name = "gan_zhi_date") val ganZhiDate: String?,
    @ColumnInfo(name = "wu_xing") val wuXing: String?,
    @ColumnInfo(name = "zhi_ri_xing_shen") val zhiRiXingShen: String?,
    @ColumnInfo(name = "yi") val yi: String?,
    @ColumnInfo(name = "ji") val ji: String?,
) {
    /**
     * 还原接口展示字段，查询日期仍由仓库状态携带，不拼接到接口的 year 字段。
     * @return 还原展示字段的 CHNDate，查询日期身份继续由外层状态维护。
     */
    fun toModel() = CHNDate(year, lunarDate, huangLiDate, huiLiDate, ganZhiDate, wuXing, zhiRiXingShen, yi, ji)

    companion object {
        /**
         * 写库前再次校验，防止未来新增调用入口绕过仓库后保存错误日期或残缺数据。
         * @param date 本次操作的公历日期，使用设备当前时区解释；不能用请求完成时的日期替换。
         * @param model 待转换的完整黄历模型，公历必须匹配 date，农历必须非空。
         * @return 匹配查询日期的 CHNDateEntity；输入不满足要求时抛出 IllegalArgumentException。
         */
        fun fromModel(date: LocalDate, model: CHNDate): CHNDateEntity {
            require(model.isValidFor(date))
            return CHNDateEntity(date.toString(), model.year, model.lunarDate, model.huangLiDate,
                model.huiLiDate, model.ganZhiDate, model.wuXing, model.zhiRiXingShen, model.yi, model.ji)
        }
    }
}
