package lins.libs.module_base

import com.elvishew.xlog.XLog

/**
 * 业务日志薄封装，统一 tag 与异常记录方式；使用前由 MyApplication 初始化 XLog。
 * d/i/w 用于普通事件，e 可附带原异常堆栈。输出级别和目标由 Application 集中配置。
 */
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
