package lins.libs.module_base

import com.elvishew.xlog.XLog

/**
 * 业务日志薄封装，统一 tag 与异常记录方式；使用前由 MyApplication 初始化 XLog。
 * d/i/w 用于普通事件，e 可附带原异常堆栈。输出级别和目标由 Application 集中配置。
 */
object Logger {
    /**
     * 按 DEBUG 级别记录诊断事件。
     * @param tag 日志所属模块或操作标识，用于定位来源。
     * @param message 日志正文，不应包含 Token 等认证信息。
     * @return Unit；按 DEBUG 级别记录诊断事件。
     */
    fun d(tag: String, message: String) {
        XLog.tag(tag).d(message)
    }

    /**
     * 按 INFO 级别记录一般事件。
     * @param tag 日志所属模块或操作标识，用于定位来源。
     * @param message 日志正文，不应包含 Token 等认证信息。
     * @return Unit；按 INFO 级别记录一般事件。
     */
    fun i(tag: String, message: String) {
        XLog.tag(tag).i(message)
    }

    /**
     * 按 WARN 级别记录可恢复异常迹象。
     * @param tag 日志所属模块或操作标识，用于定位来源。
     * @param message 日志正文，不应包含 Token 等认证信息。
     * @return Unit；按 WARN 级别记录可恢复异常迹象。
     */
    fun w(tag: String, message: String) {
        XLog.tag(tag).w(message)
    }

    /**
     * 按 ERROR 级别记录错误，并按需附带原异常。
     * @param tag 日志所属模块或操作标识，用于定位来源。
     * @param message 日志正文，不应包含 Token 等认证信息。
     * @param throwable 可选的原始异常；非 null 时同时记录堆栈。
     * @return Unit；按 ERROR 级别记录错误，并按需附带原异常。
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            XLog.tag(tag).e(message, throwable)
        } else {
            XLog.tag(tag).e(message)
        }
    }
}
