package lins.applications.appwidget.helper

import kotlinx.coroutines.CancellationException

/**
 * Glance 更新请求的统计。total 是枚举到的实例数；上层枚举失败时也可记录一次整体 failures。
 * 零实例且零失败视为无需更新的成功，不能据此推断桌面上存在或已经显示小组件。
 * @param total 本次枚举到并尝试更新的实例数，枚举失败时为 0。
 * @param failures 实例更新失败次数；枚举整体失败使用 1 作为失败标记。
 */
data class WidgetUpdateResult(val total: Int, val failures: Int) {
    val succeeded: Boolean get() = failures == 0
}

/**
 * 顺序尝试每个实例，使一个失效组件不阻断后面的更新。泛型回调隔离 Android API，便于 JVM 测试。
 * 普通异常由这里计数、具体日志由调用方负责；协程取消终止整个流程，不能算作部分成功。
 * @param ids 本次需要处理的实例标识集合。
 * @param update 单实例更新回调；普通异常累计为失败，取消异常终止整个流程。
 * @return WidgetUpdateResult；统计尝试实例数及失败数，取消异常继续抛出。
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
