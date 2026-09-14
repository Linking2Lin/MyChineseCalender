package lins.applications.appwidget.action

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import lins.applications.appwidget.helper.WidgetDataSyncHelper
import lins.libs.module_base.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "RefreshAction"

/**
 * 点击 widget 时触发的刷新动作。
 *
 * 这个回调负责：
 * 1. 通过原子锁避免用户连续点击导致并发刷新
 * 2. 复用当天有效农历缓存，缺失时才请求接口
 * 3. 请求刷新全部 widget 实例，保持多个副本一致
 * 4. 分别反馈数据、缓存和更新请求的失败
 *
 * Glance 通过无参构造器创建本回调，类名可能被保存到已有点击动作中，修改类名或
 * 发布压缩规则时需考虑兼容。此处的锁只合并点击，Worker 的并发由共享仓库协调。
 */
class RefreshAction : ActionCallback {

    companion object {
        /**
         * 进程内刷新锁。
         * 只要当前有一个刷新任务在执行，其他点击就暂时忽略。
         */
        private val isRefreshing = AtomicBoolean(false)
    }

    /**
     * 合并重复点击，执行当天缓存优先同步，并按失败阶段给出 Toast。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @param glanceId 触发点击的 Glance 实例标识，实际同步仍覆盖所有已登记实例。
     * @param parameters 框架传入的点击参数；当前刷新动作不读取额外业务参数。
     * @return Unit（挂起）；同步完成后通过原有 Toast 反馈，退出时释放点击互斥标记。
     */
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // 如果当前已经有刷新任务在跑，就直接返回，避免重复请求。
        if (!isRefreshing.compareAndSet(false, true)) {
            Logger.d(TAG, "onAction: already refreshing, ignoring click")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "正在刷新中…", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Logger.d(TAG, "onAction: refreshing widget $glanceId")

        try {
            // 点击与后台采用相同的缓存规则；已有当天有效数据时只更新展示，不重复联网。
            val result = WidgetDataSyncHelper.syncAndUpdate(context)

            withContext(Dispatchers.Main) {
                val message = when {
                    !result.dataReady -> "今日数据获取失败，请稍后重试"
                    !result.cacheSaved -> "数据已获取，但保存失败"
                    !result.updates.succeeded -> "部分组件更新失败，请重试"
                    else -> "今日数据已就绪"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } finally {
            // 包括取消在内的所有退出路径都释放点击锁，避免一次中断导致后续永远无法刷新。
            isRefreshing.set(false)
        }
    }
}
