package lins.libs.module_base

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.format.DateTimeFormatter

/** 共享格式定义；“当前日期”的计算统一使用 CalendarDates，不能把某次计算结果放在这里常驻。 */
object Constants {
    /** 通用日期格式 yyyy-MM-dd，线程安全，全局复用 */
    @RequiresApi(Build.VERSION_CODES.O)
    val DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
