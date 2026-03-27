package lins.applications.appwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.CHNDate
import lins.applications.appwidget.model.CHNDateEnity
import lins.libs.module_base.Logger
import java.io.File
import java.util.Calendar

private const val TAG = "MyAppWidget"

/** 自定义头像文件名（与 MainContent 中保存位置一致） */
const val WIDGET_CUSTOM_IMAGE_FILE = "widget_custom_image.jpg"

class MyAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = Room.databaseBuilder(
            context = context,
            AppDataBase::class.java, "database-name"
        ).build()

        val date = withContext(Dispatchers.IO) {
            val re = db.chnDateDao().getLast()
            Logger.d(TAG, "provideGlance: $re")
            re
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
fun WidgetContent(date: CHNDateEnity?, customBitmap: Bitmap?) {
    val size = LocalSize.current
    Logger.d(TAG, "WidgetContent updating for size: $size")

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
            LargeWidgetLayout(date = date, customBitmap = customBitmap)
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
fun SmallWidgetLayout(date: CHNDateEnity, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .cornerRadius(100.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.lunarDate ?: "–",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        )
    }
}


// ────────────────────────────────────────────────────────────────
//  小组件 · 中尺寸 — 仿 OneUI 8 时钟 pill 胶囊设计
//
//  ┌──────────────────────────────────────────┐
//  │  [圆形头像]   周五,                       │
//  │              3月 27日                     │
//  └──────────────────────────────────────────┘
//
//  设计规范：
//  • 浅色半透明药丸背景 (Material widgetBackground)
//  • 左侧 56dp 圆形图片（用户自定义 / 默认 cat）
//  • 右侧两行文字：第 1 行黄历描述、第 2 行农历日期
//  • 水平 padding 12dp，内容垂直居中
// ────────────────────────────────────────────────────────────────

@Composable
fun MediumWidgetLayout(
    date: CHNDateEnity,
    customBitmap: Bitmap?,
    modifier: GlanceModifier = GlanceModifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 外层药丸容器
        Row(
            modifier = GlanceModifier
                .cornerRadius(100.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = GlanceModifier.width(6.dp))

            // ── 左侧：圆形头像图片 ──
            if (customBitmap != null) {
                Image(
                    provider = ImageProvider(customBitmap),
                    contentDescription = "自定义头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(56.dp)
                        .cornerRadius(100.dp),
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.img_maodie),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(56.dp)
                        .cornerRadius(100.dp),
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // ── 右侧：日期文字 ──
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                // 第一行：星期 + 日期（从黄历或公历中提取）
                val weekLine = extractWeekDay(date)
                Text(
                    text = weekLine,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )

                // 第二行：农历日期
                Text(
                    text = date.lunarDate?.replace("农历", "")?.trim() ?: "–",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }

            Spacer(modifier = GlanceModifier.width(14.dp))
        }
    }
}


// ────────────────────────────────────────────────────────────────
//  小组件 · 大尺寸 — 详细日历信息卡片
// ────────────────────────────────────────────────────────────────

@Composable
fun LargeWidgetLayout(
    date: CHNDateEnity,
    customBitmap: Bitmap?,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        // ── 头部：图片 + 公历日期 ──
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (customBitmap != null) {
                Image(
                    provider = ImageProvider(customBitmap),
                    contentDescription = "自定义头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(48.dp)
                        .cornerRadius(100.dp),
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.img_maodie),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(48.dp)
                        .cornerRadius(100.dp),
                )
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column {
                Text(
                    text = date.year ?: "–",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                )
                Text(
                    text = extractWeekDay(date),
                    style = TextStyle(
                        color = GlanceTheme.colors.secondary,
                        fontSize = 13.sp,
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(14.dp))

        // ── 农历大字 ──
        Text(
            text = date.lunarDate?.replace("农历", "")?.trim() ?: "–",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        // ── 黄历 / 干支 ──
        if (!date.huangLiDate.isNullOrBlank()) {
            Text(
                text = date.huangLiDate,
                style = TextStyle(
                    color = GlanceTheme.colors.secondary,
                    fontSize = 12.sp,
                ),
                maxLines = 2,
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        if (!date.ganZhiDate.isNullOrBlank()) {
            Text(
                text = date.ganZhiDate,
                style = TextStyle(
                    color = GlanceTheme.colors.secondary,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // ── 宜忌摘要 ──
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (!date.yi.isNullOrBlank()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "宜",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    )
                    Text(
                        text = date.yi.take(30),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 11.sp,
                        ),
                        maxLines = 2,
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            if (!date.ji.isNullOrBlank()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "忌",
                        style = TextStyle(
                            color = GlanceTheme.colors.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    )
                    Text(
                        text = date.ji.take(30),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 11.sp,
                        ),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}


// ────────────────────────────────────────────────────────────────
//  工具函数
// ────────────────────────────────────────────────────────────────

/**
 * 从公历日期字符串中提取星期和日期。
 * 输入示例："2025年11月3日 星期一" → "星期一, 11月3日"
 */
private fun extractWeekDay(date: CHNDateEnity): String {
    val raw = date.year ?: return getTodayFallback()
    // 尝试提取 "星期X"
    val weekRegex = Regex("星期[一二三四五六日天]")
    val weekMatch = weekRegex.find(raw)
    // 尝试提取 "X月X日"
    val mdRegex = Regex("\\d{1,2}月\\d{1,2}日")
    val mdMatch = mdRegex.find(raw)

    return buildString {
        if (weekMatch != null) {
            append(weekMatch.value)
            append(", ")
        }
        if (mdMatch != null) {
            append(mdMatch.value)
        } else {
            append(raw)
        }
    }
}

/** 当数据库中无公历数据时，使用 Calendar 构造今天的日期文案 */
private fun getTodayFallback(): String {
    val cal = Calendar.getInstance()
    val weekNames = arrayOf("日", "一", "二", "三", "四", "五", "六")
    val week = weekNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    return "星期$week, ${month}月${day}日"
}


// ────────────────────────────────────────────────────────────────
//  Preview
// ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 400, heightDp = 100)
@Composable
fun PreWidgetContent() {
    GlanceTheme {
        WidgetContent(CHNDateEnity.covertForTest(CHNDate.test), customBitmap = null)
    }
}