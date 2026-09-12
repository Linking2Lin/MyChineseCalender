package lins.applications.appwidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import lins.applications.appwidget.action.RefreshAction
import lins.applications.appwidget.data.CalendarRepositories
import lins.applications.appwidget.helper.WidgetScheduler
import lins.libs.module_base.model.LunarDateResponse
import lins.libs.module_base.time.CalendarDates

/**
 * 小组件展示入口，订阅共享仓库和头像版本，不直接请求接口或写数据库。
 *
 * Glance 活跃会话中的 update 不一定重新执行 provideGlance，因此不能在这里读一次数据
 * 再只把快照传入 UI；首帧以本地状态初始化，之后仍须在 provideContent 内收集 Flow，
 * 让已有组合能收到后续刷新。
 * 会话结束后进程内订阅也会停止，后台唤醒仍由 WidgetScheduler/WorkManager 负责。
 */
class MyAppWidget : GlanceAppWidget() {
    // 保留原有 Exact 模式与尺寸分支，让 LocalSize 对应桌面的实际尺寸。
    override val sizeMode = SizeMode.Exact

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = CalendarRepositories.get(context).lunar
        // 日期变化时取消旧日期订阅；即使旧请求稍后完成，也不会把结果绑定到新的一天。
        val dates = CalendarDates.changes().flatMapLatest { date -> repository.observe(date) }
            .onEach { state ->
                // 只在尚未加载的空态自动触发；已有失败由点击或后台任务重试，避免观察流反复请求。
                if (state.data == null && !state.loading && state.error == null) WidgetScheduler.requestSync(context)
            }
        // 头像版本只表示“文件可能变化”；解码移到 IO，避免阻塞组合和交互。
        val images = WidgetImages.revision.map { withContext(Dispatchers.IO) { WidgetImages.load(context) } }
        // 点击可能重建 Glance 会话。先读取本地头像和当天缓存，再提交首帧，避免空态/默认头像闪烁。
        // first 只等待本地查询，不等待网络；之后继续收集原流，接收刷新、换日与头像变化。
        val initialBitmap = images.first()
        val initialState = dates.first()
        provideContent {
            // remember 保持重组期间使用同一个流；Flow 的更新由 collectAsState 转为组合状态。
            val state by remember { dates }.collectAsState(initialState)
            val bitmap by remember { images }.collectAsState(initialBitmap)
            // 网络失败时 data 仍可携带当天有效缓存；没有当天数据才进入空态。
            // 初次读取或重组期间也可能跨日，旧日期快照不能冒充今天。
            WidgetContent(state.data.takeIf { state.date == CalendarDates.today() }, bitmap)
        }
    }

    companion object {
        // 保留用户已调好的尺寸分支；与下方原始布局一起维护，不在逻辑修复时修改视觉参数。
        val SMALL_SQUARE = DpSize(50.dp, 50.dp)
        val HORIZONTAL_RECTANGLE = DpSize(100.dp, 100.dp)
        val BIG_SQUARE = DpSize(250.dp, 100.dp)
    }
}

/**
 * 外层透明容器的垂直 padding（上下各 8dp）。
 * 头像的自适应计算需要扣除它，因此抽成常量与 padding 保持同步。
 */
private val OUTER_VERTICAL_PADDING = 8.dp

/**
 * 胶囊内容区的上下边距。外边距增加 2dp 的同时，这里减少 2dp，
 * 使胶囊背景变矮，但头像和文字的可用内容高度保持不变。
 */
private val CONTENT_VERTICAL_MARGIN = 10.dp

/**
 * 左侧内容边距单独减少 2dp，使头像和文字与上方 Widget 对齐。
 */
private val CONTENT_START_MARGIN = 10.dp

/** 胶囊内容区右边距，保持原有水平布局。 */
private val CONTENT_END_MARGIN = 12.dp

/**
 * 头像在胶囊内容区中额外收缩的垂直边距（上下各 1.5dp）。
 * 不改变胶囊和文字尺寸，头像直径比内容区小 3dp。
 */
private val AVATAR_VISUAL_INSET = 1.5.dp

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

/**
 * 数据来自按日仓库的持续订阅，失败时继续展示当天有效缓存；没有当天数据则展示空态。
 * 空态统一复用中等布局，仅替换两行文案；有数据时保留原有尺寸分支与布局参数。
 */
@Composable
fun WidgetContent(date: LunarDateResponse?, customBitmap: Bitmap?) {
    val size = LocalSize.current

    if (date == null) {
        MediumWidgetLayout(date = null, customBitmap = customBitmap)
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

/** date 为空时仅切换两行文案，空态与正常中等布局共用头像、背景、排版和刷新动作。 */
@Composable
fun MediumWidgetLayout(
    date: LunarDateResponse?,
    customBitmap: Bitmap?,
    modifier: GlanceModifier = GlanceModifier
) {
    // 头像边长（正方形）：在胶囊内容高度基础上再上下各收进 1.5dp。
    val avatarSize = (
        LocalSize.current.height
            - OUTER_VERTICAL_PADDING * 2
            - CONTENT_VERTICAL_MARGIN * 2
            - AVATAR_VISUAL_INSET * 2
        )
        .coerceAtLeast(1.dp)
    // 字号自适应：不再写死 fontSize，每行可用高度 = (文字区域高度 - 上下边距 - 两行间距) / 2，
    // 字号取行高的约 85%（行高 ≈ 字号 × 1.17），使文字高度正好填满每行的可用最大高度。
    val textLineHeight = (
        LocalSize.current.height
            - OUTER_VERTICAL_PADDING * 2
            - CONTENT_VERTICAL_MARGIN * 2
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
                // 分别控制水平和垂直边距，使胶囊收紧时不改变头像和文字大小。
                .padding(
                    start = CONTENT_START_MARGIN,
                    top = CONTENT_VERTICAL_MARGIN,
                    end = CONTENT_END_MARGIN,
                    bottom = CONTENT_VERTICAL_MARGIN
                )
                .clickable(actionRunCallback<RefreshAction>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            // ── 左侧：圆形头像，在胶囊内容区中垂直居中──
            if (customBitmap != null) {
                Image(
                    provider = ImageProvider(customBitmap),
                    contentDescription = "自定义头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .height(avatarSize)
                        .width(avatarSize)
                        .cornerRadius(avatarSize / 2),
                )
            } else {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_round),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .height(avatarSize)
                        .width(avatarSize)
                        .cornerRadius(avatarSize / 2),
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // ── 右侧：日期文字（两行作为整体，与图片区域垂直居中对齐，上下各留 6dp）──
            Column(
                modifier = GlanceModifier
                    .wrapContentWidth() // 宽度按内容自适应
                    .fillMaxHeight() // 高度填满：与图片区域同高，两行文字整体垂直居中
                    .padding(vertical = TEXT_AREA_VERTICAL_PADDING), // 上下各 6dp 间距
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                // 第一行：农历年，或空态固定文案。
                Text(
                    text = date?.lunarYear ?: "常能遣其欲而心自静，",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = textFontSize,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )

                //Spacer(modifier = GlanceModifier.height(TEXT_LINE_SPACING))

                // 第二行：农历月日，或空态固定文案。
                Text(
                    text = date?.lunarDate ?: "澄其心而神自清。",
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
    // 头像边长（正方形）：在胶囊内容高度基础上再上下各收进 1.5dp。
    val avatarSize = (
        LocalSize.current.height
            - OUTER_VERTICAL_PADDING * 2
            - CONTENT_VERTICAL_MARGIN * 2
            - AVATAR_VISUAL_INSET * 2
        )
        .coerceAtLeast(1.dp)
    // 字号自适应：不再写死 fontSize，每行可用高度 = (文字区域高度 - 上下边距 - 两行间距) / 2，
    // 字号取行高的约 85%（行高 ≈ 字号 × 1.17），使文字高度正好填满每行的可用最大高度。
    val textLineHeight = (
        LocalSize.current.height
            - OUTER_VERTICAL_PADDING * 2
            - CONTENT_VERTICAL_MARGIN * 2
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
                // 分别控制水平和垂直边距，使胶囊收紧时不改变头像和文字大小。
                .padding(
                    start = CONTENT_START_MARGIN,
                    top = CONTENT_VERTICAL_MARGIN,
                    end = CONTENT_END_MARGIN,
                    bottom = CONTENT_VERTICAL_MARGIN
                )
                .clickable(actionRunCallback<RefreshAction>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            // ── 左侧：圆形头像，在胶囊内容区中垂直居中──
            if (customBitmap != null) {
                Image(
                    provider = ImageProvider(customBitmap),
                    contentDescription = "自定义头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .height(avatarSize)
                        .width(avatarSize)
                        .cornerRadius(avatarSize / 2),
                )
            } else {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_round),
                    contentDescription = "默认头像",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .height(avatarSize)
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
