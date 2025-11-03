package lins.applications.appwidget.model

import androidx.core.util.TimeUtils
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class CHNDateEnity(
    @PrimaryKey(autoGenerate = true) val uid: Int?,
    @ColumnInfo(name = "year") val year: String?,
    @ColumnInfo(name = "lunarDate") val lunarDate: String?,
    @ColumnInfo(name = "huangLiDate") val huangLiDate: String?,
    @ColumnInfo(name = "huiLiDate") val huiLiDate: String?,
    @ColumnInfo(name = "ganZhiDate") val ganZhiDate: String?,
    @ColumnInfo(name = "wuXing") val wuXing: String?,
    @ColumnInfo(name = "zhiRiXingShen") val zhiRiXingShen: String?,
    @ColumnInfo(name = "yi") val yi: String?,
    @ColumnInfo(name = "ji") val ji: String?,
){
    companion object {
        fun covert(chnDate: CHNDate) : CHNDateEnity{
            return CHNDateEnity(
                uid = null,
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

        fun covertForTest(chnDate: CHNDate) : CHNDateEnity{
            return CHNDateEnity(
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
