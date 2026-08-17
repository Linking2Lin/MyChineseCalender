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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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

/**
 * 自定义头像文件名。
 * 这个文件保存在应用内部存储中，供 widget 读取。
 */
const val WIDGET_CUSTOM_IMAGE_FILE = "widget_custom_image.png"

/**
 * Glance Widget 的主体实现。
 *
 * provideGlance 的职责是准备数据，而不是直接写 UI 逻辑：
 * - 从数据库取农历缓存
 * - 从内部存储读取自定义头像
 * - 然后把数据交给 `WidgetContent` 渲染
 */
class MyAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching {
            withContext(Dispatchers.IO) {
                val today = LocalDate.now()
                val dateString = today.format(Constants.DATE_FORMATTER)
                val db = AppDataBase.getInstance(context)
                val date = db.lunarDateDao().getByDate(dateString)?.toResponse()
                    ?: db.lunarDateDao().getLast()?.toResponse()
                val customBitmap = loadCustomImage(context)
                date to customBitmap
            }
        }.onFailure { e ->
            Logger.e(TAG, "provideGlance: error loading data", e)
        }.getOrNull()

        provideContent {
            WidgetContent(snapshot?.first, snapshot?.second)
        }
    }

    companion object {
        val SMALL_SQUARE = DpSize(50.dp, 50.dp)
        // 中/大尺寸高度取 100dp：两行 22sp 文字 + 头像 12dp 上下边距需要约 90dp，
        // 100dp 时自适应头像 ≈ 68dp（68 + 24 + 8 = 100），与 Preview 高度一致。
        val HORIZONTAL_RECTANGLE = DpSize(100.dp, 100.dp)
        val BIG_SQUARE = DpSize(250.dp, 100.dp)

        /**
         * 从内部存储加载用户自定义的 widget 头像图片。
         * 文件已在保存时预裁剪为圆形 PNG，这里直接解码即可。
         * 如果不存在或读取失败则返回 null。
         */
        fun loadCustomImage(context: Context): Bitmap? {
            return try {
                val file = File(context.filesDir, WIDGET_CUSTOM_IMAGE_FILE)
                if (file.exists()) {
                    // 先只读取图片边界，避免把大图直接加载到内存。
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)

                    // 根据目标尺寸计算采样值，尽量让解码后图片接近 widget 所需大小。
                    val maxSize = 256
                    var inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / inSampleSize > maxSize * 2) {
                        inSampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                } else null
            } catch (e: Exception) {
                Logger.e(TAG, "loadCustomImage failed", e)
                null
            }
        }
    }

    // 必须用 Exact 而不是 Responsive：
    // Responsive 模式下 LocalSize 是「声明尺寸」，系统却按 widget 实际尺寸渲染
    // （例如 1 格高实际约 60~80dp，而声明 100dp），导致 fillMaxHeight 的高度与
    // width(avatarSize) 不一致，头像变成矩形；Exact 模式下 LocalSize 永远等于
    // 实际尺寸（Android 12+ 来自 OPTION_APPWIDGET_SIZES，resize 时重新组合），
    // fillMaxHeight 的高度 = width(avatarSize)，头像保证正方形且填满可用空间。
    override val sizeMode = SizeMode.Exact
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
//  布局常量：头像尺寸自适应相关
// ────────────────────────────────────────────────────────────────

/**
 * 外层透明容器的垂直 padding（上下各 4dp）。
 * 头像的自适应计算需要扣除它，因此抽成常量与 padding 保持同步。
 */
private val OUTER_VERTICAL_PADDING = 4.dp

/**
 * 头像相对胶囊背景的固定边距：上、下、左各 12dp。
 * Glance 的 padding 是 View 内边距（缩小内容而不是撑大外层），
 * 因此 12dp 边距以胶囊的内边距实现；头像本身不带 padding，
 * 尺寸 = 胶囊内容高度，正好填满边距内的可用空间。
 * 头像边长 = widget 高度 - 2 * OUTER_VERTICAL_PADDING - 2 * AVATAR_EDGE_MARGIN。
 */
private val AVATAR_EDGE_MARGIN = 12.dp

/**
 * 文字区域上/下边距（各 6dp），字号自适应计算需要扣除它。
 */
private val TEXT_AREA_VERTICAL_PADDING = 6.dp

/**
 * 两行文字之间的间距，字号自适应计算需要扣除它。
 */
private val TEXT_LINE_SPACING = 4.dp

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
    // 头像边长（正方形）：高度由 fillMaxHeight 动态填满；Glance 无 aspectRatio，
    // 宽度与圆角取与高度相同的计算值：widget 高度 - 外层上下 padding - 胶囊上下边距(12dp × 2)
    val avatarSize = (LocalSize.current.height - OUTER_VERTICAL_PADDING * 2 - AVATAR_EDGE_MARGIN * 2)
        .coerceAtLeast(1.dp)
    // 字号自适应：不再写死 fontSize，每行可用高度 = (文字区域高度 - 上下边距 - 两行间距) / 2，
    // 字号取行高的约 85%（行高 ≈ 字号 × 1.17），使文字高度正好填满每行的可用最大高度。
    val textLineHeight = (
        LocalSize.current.height
            - OUTER_VERTICAL_PADDING * 2
            - AVATAR_EDGE_MARGIN * 2
            - TEXT_AREA_VERTICAL_PADDING * 2
            - TEXT_LINE_SPACING
        ) / 2
    val textFontSize = (textLineHeight.coerceAtLeast(1.dp) * 0.85f).value.sp

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = OUTER_VERTICAL_PADDING),
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
                // 头像的 12dp 边距放在胶囊上：Glance 的 padding 是 View 内边距，
                // 若放在图片上会把图片内容缩小（而不是撑大外层），导致图片变小。
                .padding(
                    start = AVATAR_EDGE_MARGIN,
                    top = AVATAR_EDGE_MARGIN,
                    end = AVATAR_EDGE_MARGIN,
                    bottom = AVATAR_EDGE_MARGIN
                )
                .clickable(actionRunCallback<RefreshAction>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            // ── 左侧：圆形头像图片（尺寸自适应，边长 = 胶囊内容高度，正好填满 12dp 边距内的空间）──
            if (customBitmap != null) {
                Image(
                    provider = ImageProvider(customBitmap),
                    contentDescription = "自定义头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(avatarSize)
                        .cornerRadius(avatarSize / 2),
                )
            } else {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_round),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(avatarSize)
                        .cornerRadius(avatarSize / 2),
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // ── 右侧：日期文字（两行作为整体，与图片区域垂直居中对齐，上下各留 6dp）──
            Column(
                modifier = GlanceModifier
                    .wrapContentWidth() // 宽度按内容自适应
                    .fillMaxHeight() // 高度填满：与图片区域同高，两行文字整体垂直居中
                    .padding(vertical = TEXT_AREA_VERTICAL_PADDING), // 上下各 6dp 间距
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                // 第一行：lunarYear
                Text(
                    text = date.lunarYear,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = textFontSize,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )

                Spacer(modifier = GlanceModifier.height(TEXT_LINE_SPACING))

                // 第二行：lunarDate
                Text(
                    text = date.lunarDate,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = textFontSize,
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
    // 头像边长（正方形）：高度由 fillMaxHeight 动态填满；Glance 无 aspectRatio，
    // 宽度与圆角取与高度相同的计算值：widget 高度 - 外层上下 padding - 胶囊上下边距(12dp × 2)
    val avatarSize = (LocalSize.current.height - OUTER_VERTICAL_PADDING * 2 - AVATAR_EDGE_MARGIN * 2)
        .coerceAtLeast(1.dp)
    // 字号自适应：不再写死 fontSize，每行可用高度 = (文字区域高度 - 上下边距 - 两行间距) / 2，
    // 字号取行高的约 85%（行高 ≈ 字号 × 1.17），使文字高度正好填满每行的可用最大高度。
    val textLineHeight = (
        LocalSize.current.height
            - OUTER_VERTICAL_PADDING * 2
            - AVATAR_EDGE_MARGIN * 2
            - TEXT_AREA_VERTICAL_PADDING * 2
            - TEXT_LINE_SPACING
        ) / 2
    val textFontSize = (textLineHeight.coerceAtLeast(1.dp) * 0.85f).value.sp

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = OUTER_VERTICAL_PADDING),
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
                // 头像的 12dp 边距放在胶囊上：Glance 的 padding 是 View 内边距，
                // 若放在图片上会把图片内容缩小（而不是撑大外层），导致图片变小。
                .padding(
                    start = AVATAR_EDGE_MARGIN,
                    top = AVATAR_EDGE_MARGIN,
                    end = AVATAR_EDGE_MARGIN,
                    bottom = AVATAR_EDGE_MARGIN
                )
                .clickable(actionRunCallback<RefreshAction>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            // ── 左侧：圆形头像图片（尺寸自适应，边长 = 胶囊内容高度，正好填满 12dp 边距内的空间）──
            if (customBitmap != null) {
                Image(
                    provider = ImageProvider(customBitmap),
                    contentDescription = "自定义头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(avatarSize)
                        .cornerRadius(avatarSize / 2),
                )
            } else {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_round),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(avatarSize)
                        .cornerRadius(avatarSize / 2),
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // ── 右侧：日期文字（两行作为整体，与图片区域垂直居中对齐，上下各留 6dp）──
            Column(
                modifier = GlanceModifier
                    .defaultWeight() // 占用剩余宽度
                    .fillMaxHeight() // 高度填满：与图片区域同高，两行文字整体垂直居中
                    .padding(vertical = TEXT_AREA_VERTICAL_PADDING), // 上下各 6dp 间距
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
                            fontSize = textFontSize,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )

                    /*Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = date.lunarDate,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = textFontSize,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )*/
                }

                Spacer(modifier = GlanceModifier.height(TEXT_LINE_SPACING))

                // 第二行：
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "人心好静，而欲牵之",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = textFontSize,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )

                    /*Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = "而心扰之",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = textFontSize,
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
@Preview(widthDp = 150, heightDp = 100)
@Composable
fun PreSmallWidgetContent() {
    GlanceTheme {
        SmallWidgetLayout(
            date = LunarDateResponse(lunarYear = "丙午年，马", lunarDate = "二月初九"),
        )
    }
}
