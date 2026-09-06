package lins.applications.appwidget.helper

import kotlinx.coroutines.CancellationException

/**
 * Glance 更新请求的统计。total 是枚举到的实例数；上层枚举失败时也可记录一次整体 failures。
 * 零实例且零失败视为无需更新的成功，不能据此推断桌面上存在或已经显示小组件。
 */
data class WidgetUpdateResult(val total: Int, val failures: Int) {
    val succeeded: Boolean get() = failures == 0
}

/**
 * 顺序尝试每个实例，使一个失效组件不阻断后面的更新。泛型回调隔离 Android API，便于 JVM 测试。
 * 普通异常由这里计数、具体日志由调用方负责；协程取消终止整个流程，不能算作部分成功。
 */
suspend fun <T> updateWidgetsIndependently(ids: List<T>, update: suspend (T) -> Unit): WidgetUpdateResult {
    var failures = 0
    for (id in ids) {
        try {
            update(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failures++
        }
    }
    return WidgetUpdateResult(ids.size, failures)
}
