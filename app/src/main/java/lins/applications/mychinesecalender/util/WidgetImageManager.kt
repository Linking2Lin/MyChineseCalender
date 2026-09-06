package lins.applications.mychinesecalender.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lins.applications.appwidget.WIDGET_CUSTOM_IMAGE_FILE
import lins.applications.appwidget.WidgetImages
import lins.applications.appwidget.getCircleBitmap
import lins.applications.appwidget.helper.ImageGeometry
import lins.applications.appwidget.helper.LatestImageWriter
import lins.applications.appwidget.helper.WidgetDataSyncHelper
import lins.libs.module_base.Logger

/**
 * 系统照片选择器的保存入口：选择取号 → IO 解码/居中裁剪 → 原子替换 → 通知活跃组件 → Toast。
 *
 * 使用应用级 SupervisorJob，页面重建不会中断已选图片的保存，单次失败也不会取消后续选择。
 * 此协程作用域不跨进程持久化；进程被杀后的重试需要用户重新选择。
 */
object WidgetImageManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var writer: LatestImageWriter? = null

    /** 所有选择共用一个写入器及序号，不能每次点击新建，否则无法保证最后选择生效。 */
    private fun writer(context: Context) = writer ?: synchronized(this) {
        writer ?: LatestImageWriter(File(context.filesDir, WIDGET_CUSTOM_IMAGE_FILE)).also { writer = it }
    }

    /**
     * 在选择器回调中立即调用。URI 是本次获授权读取的来源，不长期保存，也不保留 Activity 引用。
     * 只在保存成功后通知 WidgetImages；被更新选择淘汰的任务安静结束，不提示虚假的成功。
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        val destination = writer(appContext)
        // 在启动异步工作前取号，以用户选择顺序为准，而非线程实际执行顺序。
        val request = destination.newRequest()
        scope.launch {
            try {
                val saved = destination.write(request) { output ->
                    // ImageDecoder 处理图片编码方向；软件位图才可用于后续 Canvas 裁剪。
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        // 在解码阶段限制最长边，避免先完整解码超大相片再缩小导致内存峰值。
                        val target = ImageGeometry.fit(info.size.width, info.size.height, 512)
                        decoder.setTargetSize(target.width, target.height)
                    }
                    try {
                        // 返回新位图，两层 finally 分别释放裁剪结果和原图，失败路径同样释放。
                        val circular = getCircleBitmap(bitmap)
                        try {
                            check(circular.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode image" }
                        } finally {
                            circular.recycle()
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                if (!saved) return@launch
                // 已落盘后再发失效通知；即使某个组件更新请求失败，也保留已保存的头像。
                WidgetImages.changed()
                val result = WidgetDataSyncHelper.updateAllWidgets(appContext)
                val message = if (result.succeeded) "头像已保存" else "头像已保存，部分组件更新失败，请点击组件重试"
                withContext(Dispatchers.Main) { Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("WidgetImageManager", "Unable to save avatar", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "图片设置失败，请换一张重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
