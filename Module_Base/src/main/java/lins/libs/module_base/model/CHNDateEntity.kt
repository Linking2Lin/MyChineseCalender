package lins.libs.module_base.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chn_date")
data class CHNDateEntity(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo(name = "year") val year: String?,
    @ColumnInfo(name = "lunar_date") val lunarDate: String?,
    @ColumnInfo(name = "huang_li_date") val huangLiDate: String?,
    @ColumnInfo(name = "hui_li_date") val huiLiDate: String?,
    @ColumnInfo(name = "gan_zhi_date") val ganZhiDate: String?,
    @ColumnInfo(name = "wu_xing") val wuXing: String?,
    @ColumnInfo(name = "zhi_ri_xing_shen") val zhiRiXingShen: String?,
    @ColumnInfo(name = "yi") val yi: String?,
    @ColumnInfo(name = "ji") val ji: String?,
){
    fun toModel(): CHNDate {
        return CHNDate(
            year = year,
            lunarDate = lunarDate,
            huangLiDate = huangLiDate,
            huiLiDate = huiLiDate,
            ganZhiDate = ganZhiDate,
            wuXing = wuXing,
            zhiRiXingShen = zhiRiXingShen,
            yi = yi,
            ji = ji
        )
    }

    companion object {
        fun convert(chnDate: CHNDate) : CHNDateEntity{
            return CHNDateEntity(
                uid = 0,
                year = chnDate.year,
                lunarDate = chnDate.lunarDate,
                huangLiDate = chnDate.huangLiDate,
                huiLiDate = chnDate.huiLiDate,
                ganZhiDate = chnDate.ganZhiDate,
                wuXing = chnDate.wuXing,
                zhiRiXingShen = chnDate.zhiRiXingShen,
                yi = chnDate.yi,
                ji = chnDate.ji
            )
        }

        fun convertForTest(chnDate: CHNDate) : CHNDateEntity{
            return CHNDateEntity(
                uid = 1,
                year = chnDate.year,
                lunarDate = chnDate.lunarDate,
                huangLiDate = chnDate.huangLiDate,
                huiLiDate = chnDate.huiLiDate,
                ganZhiDate = chnDate.ganZhiDate,
                wuXing = chnDate.wuXing,
                zhiRiXingShen = chnDate.zhiRiXingShen,
                yi = chnDate.yi,
                ji = chnDate.ji
            )
        }
    }
}
