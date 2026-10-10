package lins.applications.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import lins.applications.appwidget.helper.WidgetScheduler

/** 桌面宿主的生命周期桥接：调度归 WidgetScheduler，实际 RemoteViews 生成仍交给 Glance 父类。 */
class MyAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyAppWidget()

    /**
     * 首个实例启用时建立后台任务；onUpdate 仍会再次确认，覆盖恢复等时序。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @return Unit；保留父类处理并为首个组件安排后台任务。
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetScheduler.ensureScheduled(context)
    }

    /**
     * 首次添加或宿主请求更新时，排队同步并保留父类的 Glance 更新处理。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @param manager 系统提供的小组件管理器，继续传给 Glance 父类处理。
     * @param ids 本次需要处理的实例标识集合。
     * @return Unit；安排同步并将更新事件继续交给 Glance 父类。
     */
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetScheduler.ensureScheduled(context)
        WidgetScheduler.requestSync(context)
        super.onUpdate(context, manager, ids)
    }

    /**
     * 最后一个实例被移除时停止本组件的周期、即时任务和午夜闹钟。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @return Unit；保留父类处理并取消本组件的后台任务及午夜闹钟。
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancel(context)
    }
}
