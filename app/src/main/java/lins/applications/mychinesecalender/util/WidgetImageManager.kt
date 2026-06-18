package lins.applications.mychinesecalender.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.WIDGET_CUSTOM_IMAGE_FILE
import lins.applications.appwidget.getCircleBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Widget 自定义图片管理单例。
 * 自身持有 Application 级别的 CoroutineScope，生命周期与 App 进程一致，
 * 避免在 Composable 中创建无生命周期绑定的 CoroutineScope。
 */
object WidgetImageManager {

    /** Widget 自定义图片的最大边长（像素），超过会等比缩放 */
    private const val MAX_IMAGE_SIZE = 512

    // Application 级别的协程作用域，进程存活期间有效
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 将选中的图片解码、缩放后保存到内部存储，然后刷新 Widget。
     *
     * - 使用 BitmapFactory 解码，兼容 HEIF / WEBP / PNG 等各种格式
     * - 先计算 inSampleSize 降采样，避免大图 OOM
     * - 裁剪为圆形后以 PNG 保存（支持透明通道）
     * - 完成后弹出 Toast 提示
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri) {
        // 使用 applicationContext 避免 Activity 泄露
        val appContext = context.applicationContext
        scope.launch {
            try {
                // 1. 获取图片原始尺寸（不加载到内存）
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

                // 2. 计算降采样倍数
                var inSampleSize = 1
                val maxSide = maxOf(origWidth, origHeight)
                while (maxSide / inSampleSize > MAX_IMAGE_SIZE * 2) {
                    inSampleSize *= 2
                }

                // 3. 解码图片
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

                // 4. 等比缩放到 MAX_IMAGE_SIZE
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

                // 5. 裁剪为圆形后以 PNG 保存（PNG 支持透明通道）
                val circular = getCircleBitmap(scaled)
                if (circular !== scaled) scaled.recycle()
                val file = File(appContext.filesDir, WIDGET_CUSTOM_IMAGE_FILE)
                FileOutputStream(file).use { fos ->
                    circular.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                circular.recycle()

                // 6. 刷新所有 Widget
                val manager = GlanceAppWidgetManager(appContext)
                val widget = MyAppWidget()
                val glanceIds = manager.getGlanceIds(MyAppWidget::class.java)
                glanceIds.forEach { glanceId ->
                    widget.update(appContext, glanceId)
                }

                // 7. Toast 提示成功
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
