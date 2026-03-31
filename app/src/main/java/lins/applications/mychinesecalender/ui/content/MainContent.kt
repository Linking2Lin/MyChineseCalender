package lins.applications.mychinesecalender.ui.content

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.WIDGET_CUSTOM_IMAGE_FILE
import java.io.File
import java.io.FileOutputStream
import lins.applications.appwidget.model.CHNDate
import lins.applications.mychinesecalender.MainViewModel
import lins.applications.mychinesecalender.ui.theme.MyChineseCalenderTheme

/** Widget 自定义图片的最大边长（像素），超过会等比缩放 */
private const val MAX_IMAGE_SIZE = 512

@Composable
fun MainContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val data = viewModel.lunarDate.value
    val context = LocalContext.current

    // 用 state 控制是否需要弹出选择器，避免 recomposition 重复弹出
    var shouldLaunchPicker by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            shouldLaunchPicker = false  // 选择器关闭后重置标记
            if (uri != null) {
                saveImageToInternalStorage(context, uri)
            }
        }
    )

    // 只在 shouldLaunchPicker 从 false→true 时才 launch 一次
    if (shouldLaunchPicker) {
        shouldLaunchPicker = false
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        MainContentStateless(data = data)

        FloatingActionButton(
            onClick = {
                shouldLaunchPicker = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Image, contentDescription = "自定义组件图片")
        }
    }
}

/**
 * 将选中的图片解码、缩放后保存到内部存储，然后刷新 Widget。
 *
 * 修复：
 * - 使用 BitmapFactory 解码而非直接 copyTo，兼容 HEIF / WEBP / PNG 等各种格式
 * - 先计算 inSampleSize 降采样，避免大图 OOM
 * - 压缩为 JPEG 存储，确保 Widget 能读取
 * - 完成后弹出 Toast 提示
 */
private fun saveImageToInternalStorage(context: Context, uri: Uri) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 1. 获取图片原始尺寸（不加载到内存）
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "无法读取该图片，请换一张试试", Toast.LENGTH_SHORT).show()
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
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
            if (bitmap == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "无法解码该图片，请换一张试试", Toast.LENGTH_SHORT).show()
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

            // 5. 压缩为 JPEG 保存
            val file = File(context.filesDir, WIDGET_CUSTOM_IMAGE_FILE)
            FileOutputStream(file).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            scaled.recycle()

            // 6. 刷新所有 Widget
            val manager = GlanceAppWidgetManager(context)
            val widget = MyAppWidget()
            val glanceIds = manager.getGlanceIds(MyAppWidget::class.java)
            glanceIds.forEach { glanceId ->
                widget.update(context, glanceId)
            }

            // 7. Toast 提示成功
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Widget 图片已更新 ✓", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "图片设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun MainContentStateless(
    data: CHNDate,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 主日期卡片
        MainDateCard(data = data)

        // 详细历法卡片
        DetailInfoCard(data = data)

        // 宜忌卡片
        YiJiSection(yi = data.yi, ji = data.ji)
    }
}

@Composable
fun MainDateCard(data: CHNDate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = data.year ?: "未知公历日期",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            val lunarParts = data.lunarDate?.split(" ", limit = 2)
            if (lunarParts != null && lunarParts.size == 2) {
                Text(
                    text = lunarParts[0],
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lunarParts[1],
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = data.lunarDate ?: "未知农历日期",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = data.ganZhiDate ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun DetailInfoCard(data: CHNDate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow(label = "黄历", value = data.huangLiDate)
            InfoRow(label = "五行", value = data.wuXing)
            InfoRow(label = "星神", value = data.zhiRiXingShen)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    if (value.isNullOrEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun YiJiSection(yi: String?, ji: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        YiJiCard(
            modifier = Modifier.weight(1f),
            title = "宜",
            content = yi,
            // 采用 Material3 动态配色：次要色彩容器适合“宜”
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleColor = MaterialTheme.colorScheme.onSecondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
        YiJiCard(
            modifier = Modifier.weight(1f),
            title = "忌",
            content = ji,
            // 采用 Material3 动态配色：错误色彩容器天然适合“忌”的警示色彩
            containerColor = MaterialTheme.colorScheme.errorContainer,
            titleColor = MaterialTheme.colorScheme.onErrorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun YiJiCard(
    modifier: Modifier = Modifier,
    title: String,
    content: String?,
    containerColor: Color,
    titleColor: Color,
    contentColor: Color
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content ?: "无",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun MainContentPreview() {
    MyChineseCalenderTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                MainContentStateless(data = CHNDate.test)
                FloatingActionButton(
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = "自定义组件图片")
                }
            }
        }
    }
}