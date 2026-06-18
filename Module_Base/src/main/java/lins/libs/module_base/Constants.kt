package lins.libs.module_base

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.format.DateTimeFormatter

object Constants {
    /** 通用日期格式 yyyy-MM-dd，线程安全，全局复用 */
    @RequiresApi(Build.VERSION_CODES.O)
    val DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
