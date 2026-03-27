package lins.applications.appwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentWidth
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lins.applications.appwidget.data.HkoRepository
import lins.applications.appwidget.model.LunarDateResponse
import lins.libs.module_base.Logger
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "MyAppWidget"

/** 自定义头像文件名（与 MainContent 中保存位置一致） */
const val WIDGET_CUSTOM_IMAGE_FILE = "widget_custom_image.jpg"

class MyAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        // 获取 LunarDateResponse
        val date = withContext(Dispatchers.IO) {
            try {
                val today = LocalDate.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val dateString = today.format(formatter)
                val response = HkoRepository().fetchLunarDate(dateString)
                Logger.d(TAG, "provideGlance fetched: $response")
                response
            } catch (e: Exception) {
                Logger.d(TAG, "provideGlance fetch failed: ${e.message}")
                null
            }
        }

        // 预加载自定义图片 Bitmap（IO 线程）
        val customBitmap = withContext(Dispatchers.IO) {
            loadCustomImage(context)
        }

        provideContent {
            WidgetContent(date, customBitmap)
        }
    }

    companion object {
        val SMALL_SQUARE = DpSize(50.dp, 50.dp)
        val HORIZONTAL_RECTANGLE = DpSize(100.dp, 50.dp)
        val BIG_SQUARE = DpSize(400.dp, 400.dp)

        /**
         * 从内部存储加载用户自定义的 widget 头像图片。
         * 如果不存在或读取失败则返回 null。
         */
        fun loadCustomImage(context: Context): Bitmap? {
            return try {
                val file = File(context.filesDir, WIDGET_CUSTOM_IMAGE_FILE)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "loadCustomImage failed", e)
                null
            }
        }
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SQUARE,
            HORIZONTAL_RECTANGLE,
            BIG_SQUARE
        )
    )
}

// ────────────────────────────────────────────────────────────────
//  主入口
// ────────────────────────────────────────────────────────────────

@Composable
fun WidgetContent(date: LunarDateResponse?, customBitmap: Bitmap?) {
    val size = LocalSize.current
    // XLog is not initialized in Preview, so we use standard Log.d instead.
    Log.d(TAG, "WidgetContent updating for size: $size")

    if (date == null) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "暂无数据",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp
                )
            )
        }
        return
    }

    when {
        size.width >= MyAppWidget.BIG_SQUARE.width
                && size.height >= MyAppWidget.BIG_SQUARE.height -> {
            // 这里为了简单，大尺寸也复用中等尺寸的样式，因为 HkoRepository 只返回了 lunarYear 和 lunarDate
            MediumWidgetLayout(date = date, customBitmap = customBitmap)
        }

        size.width >= MyAppWidget.HORIZONTAL_RECTANGLE.width -> {
            MediumWidgetLayout(date = date, customBitmap = customBitmap)
        }

        else -> {
            SmallWidgetLayout(date = date)
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  小组件 · 小尺寸 — 药丸形，仅显示农历日期
// ────────────────────────────────────────────────────────────────

@Composable
fun SmallWidgetLayout(date: LunarDateResponse, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .cornerRadius(100.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.lunarDate,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        )
    }
}

// ────────────────────────────────────────────────────────────────
//  小组件 · 中等尺寸 / 大尺寸 — 统一为要求的双行文字布局
// ────────────────────────────────────────────────────────────────

@Composable
fun MediumWidgetLayout(
    date: LunarDateResponse,
    customBitmap: Bitmap?,
    modifier: GlanceModifier = GlanceModifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            // 根据第一个组件的边距进行微调，让左侧图片更靠边缘，右侧留有一定的 Padding
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {

        //包裹内容并绘制胶囊背景
        Row(
            modifier = modifier
                .fillMaxSize()
                .cornerRadius(100.dp)
                .background(GlanceTheme.colors.widgetBackground)
                // 根据第一个组件的边距进行微调，让左侧图片更靠边缘，右侧留有一定的 Padding
                //.padding(start = 10.dp, end = 10.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            // ── 左侧：圆形头像图片 ──
            if (customBitmap != null) {
                Image(
                    provider = ImageProvider(customBitmap),
                    contentDescription = "自定义头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(66.dp)
                        .cornerRadius(100.dp),
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.img_maodie),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(66.dp)
                        .cornerRadius(100.dp),
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // ── 右侧：日期文字 ──
            Column(
                modifier = GlanceModifier.wrapContentWidth(), // 文字容器根据内容自适应宽度
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                // 第一行：lunarYear
                Text(
                    text = date.lunarYear,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // 第二行：lunarDate
                Text(
                    text = date.lunarDate,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  Preview
// ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 400, heightDp = 100)
@Composable
fun PreWidgetContent() {
    GlanceTheme {
        WidgetContent(
            date = LunarDateResponse(lunarYear = "星期五，3月27日", lunarDate = "二月初九"),
            customBitmap = null
        )
    }
}
