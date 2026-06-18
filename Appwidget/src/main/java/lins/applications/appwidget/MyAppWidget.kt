package lins.applications.appwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lins.applications.appwidget.action.RefreshAction
import lins.libs.module_base.Constants
import lins.libs.module_base.Logger
import lins.libs.module_base.database.AppDataBase
import lins.libs.module_base.model.LunarDateResponse
import java.io.File
import java.time.LocalDate

private const val TAG = "MyAppWidget"

/** 自定义头像文件名（与 MainContent 中保存位置一致） */
const val WIDGET_CUSTOM_IMAGE_FILE = "widget_custom_image.png"

class MyAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        var date: LunarDateResponse? = null
        var customBitmap: Bitmap? = null

        try {
            // 只从数据库缓存读取，网络请求由 WorkManager / DateChangeReceiver 负责
            date = withContext(Dispatchers.IO) {
                val today = LocalDate.now()
                val dateString = today.format(Constants.DATE_FORMATTER)
                val db = AppDataBase.getInstance(context)

                // 优先读取今天的缓存，不命中则用最近一条兜底
                db.lunarDateDao().getByDate(dateString)?.toResponse()
                    ?: db.lunarDateDao().getLast()?.toResponse()
            }

            // 预加载自定义图片 Bitmap（IO 线程）
            customBitmap = withContext(Dispatchers.IO) {
                loadCustomImage(context)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "provideGlance: error loading data", e)
        }

        provideContent {
            WidgetContent(date, customBitmap)
        }
    }

    companion object {
        val SMALL_SQUARE = DpSize(50.dp, 50.dp)
        val HORIZONTAL_RECTANGLE = DpSize(100.dp, 50.dp)
        val BIG_SQUARE = DpSize(250.dp, 50.dp)

        /**
         * 从内部存储加载用户自定义的 widget 头像图片。
         * 文件已在保存时预裁剪为圆形 PNG，这里直接解码即可。
         * 如果不存在或读取失败则返回 null。
         */
        fun loadCustomImage(context: Context): Bitmap? {
            return try {
                val file = File(context.filesDir, WIDGET_CUSTOM_IMAGE_FILE)
                if (file.exists()) {
                    // 限制加载尺寸，避免大图占用过多内存
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(file.absolutePath, options)

                    val maxSize = 256 // Widget 图片最大像素
                    var inSampleSize = 1
                    while (maxOf(
                            options.outWidth,
                            options.outHeight
                        ) / inSampleSize > maxSize * 2
                    ) {
                        inSampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options()
                    decodeOptions.inSampleSize = inSampleSize
                    BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                } else null
            } catch (e: Exception) {
                Logger.e(TAG, "loadCustomImage failed", e)
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

/**
 * 将 Bitmap 裁剪为正圆形，供保存图片时使用。
 */
fun getCircleBitmap(bitmap: Bitmap): Bitmap {
    val size = Math.min(bitmap.width, bitmap.height)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint()
    val rect = Rect(0, 0, size, size)

    paint.isAntiAlias = true
    canvas.drawARGB(0, 0, 0, 0)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, rect, rect, paint)
    return output
}

// ────────────────────────────────────────────────────────────────
//  主入口
// ────────────────────────────────────────────────────────────────

@Composable
fun WidgetContent(date: LunarDateResponse?, customBitmap: Bitmap?) {
    val size = LocalSize.current

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
        size.width >= MyAppWidget.BIG_SQUARE.width -> {
            // 这里为了简单，大尺寸也复用中等尺寸的样式，因为 HkoRepository 只返回了 lunarYear 和 lunarDate
            // MaxWidgetLayout(date = date, customBitmap = customBitmap)
            // 先暂时使用中等，大布局需要确认最终排版
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
            // 使用 XML Drawable 作为背景，系统底层绘制圆角更早更稳定
            .background(ImageProvider(R.drawable.widget_gradient_background))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionRunCallback<RefreshAction>()),
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
//  小组件 · 中等尺寸
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {

        //包裹内容并绘制胶囊背景
        Row(
            modifier = modifier
                .fillMaxSize()
                .cornerRadius(100.dp) // <-- 显式设置圆角以裁剪点击水波纹
                // GlanceTheme.colors.widgetBackground 本质上通常映射到系统的 Surface Color 或者定义的 Widget Background Color，
                // 由于 Glance 暂不支持直接传入 Compose Brush 来绘制渐变，
                // 我们通过一个带有 gradient 渐变的 XML drawable 来实现渐变效果。
                .background(ImageProvider(R.drawable.widget_gradient_background))
                .padding(11.dp)
                .clickable(actionRunCallback<RefreshAction>()),
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
                        .cornerRadius(33.dp), // 双重保险：RemoteViews 级别圆形裁剪，重启缓存恢复后仍生效
                )
            } else {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_round),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(66.dp)
                        .cornerRadius(33.dp),
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
                        fontSize = 22.sp,
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
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}


// ────────────────────────────────────────────────────────────────
//  小组件 · 大尺寸
// ────────────────────────────────────────────────────────────────

@Composable
fun MaxWidgetLayout(
    date: LunarDateResponse,
    customBitmap: Bitmap?,
    modifier: GlanceModifier = GlanceModifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {

        //包裹内容并绘制胶囊背景
        Row(
            modifier = modifier
                .fillMaxSize()
                .cornerRadius(100.dp) // <-- 显式设置圆角以裁剪点击水波纹
                // GlanceTheme.colors.widgetBackground 本质上通常映射到系统的 Surface Color 或者定义的 Widget Background Color，
                // 由于 Glance 暂不支持直接传入 Compose Brush 来绘制渐变，
                // 我们通过一个带有 gradient 渐变的 XML drawable 来实现渐变效果。
                .background(ImageProvider(R.drawable.widget_gradient_background))
                .padding(11.dp)
                .clickable(actionRunCallback<RefreshAction>()),
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
                        .cornerRadius(33.dp), // 双重保险：RemoteViews 级别圆形裁剪，重启缓存恢复后仍生效
                )
            } else {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_round),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(66.dp)
                        .cornerRadius(33.dp),
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // ── 右侧：日期文字 ──
            Column(
                modifier = GlanceModifier.defaultWeight(), // 占用剩余宽度
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {

                // 第一行：lunarYear 和 夫人神好清
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = date.lunarDate + "，" +date.lunarYear,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )

                    /*Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = date.lunarDate,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )*/
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // 第二行：
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "人心好静，而欲牵之",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )

                    /*Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = "而心扰之",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )*/
                }
            }

            Spacer(
                modifier = GlanceModifier
                    .width(20.dp)
                    .height(1.dp)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  Preview
// ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 410, heightDp = 100)
@Composable
fun PreWidgetContent() {
    GlanceTheme {
        WidgetContent(
            date = LunarDateResponse(lunarYear = "丙午年，马", lunarDate = "二月初九"),
            customBitmap = null
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 410, heightDp = 100)
@Composable
fun PreMaxWidgetContent() {
    GlanceTheme {
        MaxWidgetLayout(
            date = LunarDateResponse(lunarYear = "丙午年，马", lunarDate = "二月初九"),
            customBitmap = null
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 410, heightDp = 100)
@Composable
fun PreMediumWidgetContent() {
    GlanceTheme {
        MediumWidgetLayout(
            date = LunarDateResponse(lunarYear = "丙午年，马", lunarDate = "二月初九"),
            customBitmap = null
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 410, heightDp = 100)
@Composable
fun PreSmallWidgetContent() {
    GlanceTheme {
        SmallWidgetLayout(
            date = LunarDateResponse(lunarYear = "丙午年，马", lunarDate = "二月初九"),
            //customBitmap = null
        )
    }
}
