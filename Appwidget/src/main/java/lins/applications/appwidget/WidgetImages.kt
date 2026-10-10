package lins.applications.appwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import lins.applications.appwidget.helper.ImageGeometry
import lins.libs.module_base.Logger

/** 已安装用户的自定义图片文件名；修改时需考虑旧图片的迁移或读取兼容。 */
const val WIDGET_CUSTOM_IMAGE_FILE = "widget_custom_image.png"

/**
 * 连接头像文件与 Glance 活跃会话。磁盘文件是持久状态，revision 只是进程内失效通知，
 * 不应持久化版本号或用它判断磁盘是否存在图片。
 */
object WidgetImages {
    private val _revision = MutableStateFlow(0L)
    /** 当前进程的头像失效序号；每个新订阅也会收到初值，从而读取磁盘上的已有图片。 */
    val revision = _revision.asStateFlow()
    /**
     * 只有新文件成功提交后调用，确保订阅者收到通知时能读取完整图片。
     * @return Unit；递增头像失效版本，使活跃订阅重新读取文件。
     */
    fun changed() { _revision.update { it + 1 } }

    /**
     * 新进程的首次订阅也会调用读取；应在 IO 调度器执行。
     * 二次采样减小传给 RemoteViews 的位图体积，读取失败返回 null，由展示层使用默认头像。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @return Bitmap；成功时返回解码结果，图片不存在或读取失败时返回 null。
     */
    fun load(context: Context): Bitmap? = try {
        val file = File(context.filesDir, WIDGET_CUSTOM_IMAGE_FILE)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
        else null
    } catch (e: Exception) {
        Logger.e("WidgetImages", "Unable to decode avatar", e)
        null
    }
}

/**
 * 将源图中心正方形绘制到透明圆形蒙版，返回一个新的软件 Bitmap。
 * 不修改/回收输入；调用方负责释放输入及返回值，不能在 Glance 仍引用图片时提前回收。
 * @param bitmap 调用方持有的源位图；函数不修改或回收它。
 * @return 新创建的圆形透明背景 Bitmap；调用方在使用完毕后负责回收。
 */
fun getCircleBitmap(bitmap: Bitmap): Bitmap {
    val crop = ImageGeometry.centerCrop(bitmap.width, bitmap.height)
    val output = Bitmap.createBitmap(crop.size, crop.size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawCircle(crop.size / 2f, crop.size / 2f, crop.size / 2f, paint)
    // 先画不透明圆，再以 SRC_IN 仅保留源图与圆相交的部分，圆外保持透明。
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, Rect(crop.left, crop.top, crop.left + crop.size, crop.top + crop.size),
        Rect(0, 0, crop.size, crop.size), paint)
    return output
}
