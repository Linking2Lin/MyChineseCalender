package lins.libs.module_base

import android.app.Application
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.ConsolePrinter
import com.elvishew.xlog.printer.file.FilePrinter

open class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val logConfig = LogConfiguration.Builder()
            .logLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN)

            .build()

        val androidPrinter = AndroidPrinter()
        val consolePrinter = ConsolePrinter()
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