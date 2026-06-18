package lins.libs.module_base

import com.elvishew.xlog.XLog

object Logger {
    fun d(tag: String, message: String) {
        XLog.tag(tag).d(message)
    }

    fun i(tag: String, message: String) {
        XLog.tag(tag).i(message)
    }

    fun w(tag: String, message: String) {
        XLog.tag(tag).w(message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            XLog.tag(tag).e(message, throwable)
        } else {
            XLog.tag(tag).e(message)
        }
    }
}