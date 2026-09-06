package lins.libs.module_base

import android.app.Application
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.ConsolePrinter
import com.elvishew.xlog.printer.file.FilePrinter

/**
 * 基础进程初始化，仅负责日志；主模块 App 继承后再装配组件调度。
 * 必须先调用 super.onCreate 再初始化上层功能，确保后台入口记录错误时日志框架已就绪。
 */
open class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Debug 保留诊断日志，Release 仅记录 WARN 及以上，减少噪声和运行开销。
        val logConfig = LogConfiguration.Builder()
            .logLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN)

            .build()

        val androidPrinter = AndroidPrinter()
        val consolePrinter = ConsolePrinter()
        // 日志位于应用内部 files/logs，不需要外部存储权限；没有在此另设容量/保留天数策略。
        val filePrinter = FilePrinter.Builder(
            java.io.File(this@MyApplication.filesDir, "logs").absolutePath
        )
            .build()


        XLog.init(
            logConfig,
           *arrayOf(androidPrinter, consolePrinter, filePrinter)
        )
    }
}
