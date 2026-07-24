package lins.applications.mychinesecalender.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import lins.applications.appwidget.helper.WidgetDataSyncHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lins.applications.appwidget.WIDGET_CUSTOM_IMAGE_FILE
import lins.applications.appwidget.getCircleBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Widget 自定义图片管理器。
 *
 * 这个对象负责把用户选择的图片转换成适合 widget 使用的头像资源，
 * 整个流程包含：读取 -> 降采样 -> 缩放 -> 圆形裁剪 -> 保存到内部存储 -> 刷新 widget。
 *
 * 设计目标：
 * 1. 不让 UI 层直接接触图片压缩细节
 * 2. 不让 Composable 自己持有复杂文件 IO 逻辑
 * 3. 所有 widget 头像刷新都通过统一入口处理，方便后续替换为别的图片策略
 */
object WidgetImageManager {

    /**
     * widget 自定义图片的目标最大边长。
     * 超过这个尺寸的图片会被压缩，降低内存占用和存储体积。
     */
    private const val MAX_IMAGE_SIZE = 512

    /**
     * 进程级协程作用域。
     * 图片处理是耗时 IO 任务，不应该绑在某个 Activity/Composable 生命周期上，
     * 否则很容易在页面退出时中断处理流程。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 保存用户选择的图片到内部存储，并刷新所有已存在的 widget 实例。
     *
     * 处理步骤：
     * 1. 先通过 inJustDecodeBounds 获取原图尺寸，避免直接把大图解码进内存
     * 2. 根据目标尺寸计算合适的 inSampleSize
     * 3. 真正解码 bitmap
     * 4. 视情况等比缩放
     * 5. 统一裁剪成圆形 PNG
     * 6. 保存到 app 内部存储，确保 widget 下次启动仍然可用
     * 7. 刷新所有 widget
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri) {
        // 这里显式使用 applicationContext，避免持有 Activity 导致内存泄露。
        val appContext = context.applicationContext
        scope.launch {
            try {
                // 第一步：只读取图片边界信息，不真正加载 bitmap。
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                val origWidth = options.outWidth
                val origHeight = options.outHeight
                if (origWidth <= 0 || origHeight <= 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "无法读取该图片，请换一张试试", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 第二步：计算降采样比例，尽量避免一次性解码大图造成 OOM。
                var inSampleSize = 1
                val maxSide = maxOf(origWidth, origHeight)
                while (maxSide / inSampleSize > MAX_IMAGE_SIZE * 2) {
                    inSampleSize *= 2
                }

                // 第三步：真正解码图片。
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                val bitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "无法解码该图片，请换一张试试", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 第四步：如果图片仍然较大，再按比例压到目标尺寸。
                val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_IMAGE_SIZE) {
                    val scale = MAX_IMAGE_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
                    val newW = (bitmap.width * scale).toInt()
                    val newH = (bitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(bitmap, newW, newH, true).also {
                        if (it !== bitmap) bitmap.recycle()
                    }
                } else {
                    bitmap
                }

                // 第五步：裁剪成圆形头像后保存。
                val circular = getCircleBitmap(scaled)
                if (circular !== scaled) scaled.recycle()
                val file = File(appContext.filesDir, WIDGET_CUSTOM_IMAGE_FILE)
                val temporaryFile = File(appContext.filesDir, "$WIDGET_CUSTOM_IMAGE_FILE.tmp")
                try {
                    FileOutputStream(temporaryFile).use { fos ->
                        check(circular.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                            "Unable to encode widget image"
                        }
                        fos.fd.sync()
                    }
                    check(temporaryFile.renameTo(file)) { "Unable to replace widget image" }
                } finally {
                    circular.recycle()
                    if (temporaryFile.exists()) temporaryFile.delete()
                }

                // 第六步：刷新所有 widget 实例。
                WidgetDataSyncHelper.updateAllWidgets(appContext)

                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Widget 图片已更新 ✓", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "图片设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
