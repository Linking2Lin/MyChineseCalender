package lins.applications.appwidget

import android.content.Context
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentSize
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lins.applications.appwidget.database.AppDataBase
import lins.applications.appwidget.model.CHNDate
import lins.applications.appwidget.model.CHNDateEnity

private const val TAG = "MyAppWidget"

class MyAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        val db = Room.databaseBuilder(
            context = context,
            AppDataBase::class.java, "database-name"
        ).build()


        val date = withContext(Dispatchers.IO) {
//            val dates =  db.chnDateDao().getAll()
//            Log.d(TAG, "provideGlance: $dates")
//            dates.last()
            val re = db.chnDateDao().getLast()
            Log.d(TAG, "provideGlance: $re")
            re
        }


        provideContent {

            WidgetContent(
                date
            )
        }
    }

    companion object {
        val SMALL_SQUARE = DpSize(50.dp, 50.dp)
        val HORIZONTAL_RECTANGLE = DpSize(100.dp, 50.dp) // Adjusted for clarity
        val BIG_SQUARE = DpSize(400.dp, 400.dp)
    }

    /* override val sizeMode = SizeMode.Responsive(
         setOf(
             SMALL_SQUARE,
             HORIZONTAL_RECTANGLE,
             BIG_SQUARE
         )
     )*/

    // [修复] 启用 Responsive 模式以支持大小调整
    override val sizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SQUARE,
            HORIZONTAL_RECTANGLE,
            BIG_SQUARE
        )
    )

    // override val sizeMode = SizeMode.Exact // 注释掉或删除这一行
}


@Composable
fun WidgetContent(date: CHNDateEnity?) {
    // Get the current size of the widget instance
    val size = LocalSize.current
    Log.d(TAG, "WidgetContent updating for size: $size")

    // Use a default corner radius for all sizes
    val modifier = GlanceModifier
//        .padding(16.dp)
//        .fillMaxSize()
//        .background(GlanceTheme.colors.widgetBackground)
//        .cornerRadius(16.dp)

    // Conditionally render UI based on the available size
    if (date == null) {
        // Show a loading or error state if there's no data
        Column(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("暂无数据")
        }
    } else {
        // [修复] 应该同时比较宽度和高度，以正确匹配矩形和方形
        when {
            // 当小组件尺寸大于或等于 BIG_SQUARE 时，显示大布局
            size.width >= MyAppWidget.BIG_SQUARE.width
                    && size.height >= MyAppWidget.BIG_SQUARE.height -> {
                LargeWidgetLayout(date = date, modifier = modifier)
            }
            // 当小组件尺寸大于或等于 HORIZONTAL_RECTANGLE 时，显示中等布局
            size.width >= MyAppWidget.HORIZONTAL_RECTANGLE.width -> {
                MediumWidgetLayout(date = date, modifier = modifier)
            }
            // 否则，显示最小的布局
            else -> {
                SmallWidgetLayout(date = date, modifier = modifier)
            }
        }
    }
}

// 最小尺寸的布局
@Composable
fun SmallWidgetLayout(date: CHNDateEnity, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .cornerRadius(100.dp)
            .background(GlanceTheme.colors.primary)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = date.lunarDate ?: "error")
    }
}

// 中等尺寸的布局
@Composable
fun MediumWidgetLayout(date: CHNDateEnity, modifier: GlanceModifier) {

    Row(

        modifier = GlanceModifier
            .padding(horizontal = 8.dp)
            .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically


    ) {
        Row(
            modifier = GlanceModifier
                .cornerRadius(100.dp)
                .background(GlanceTheme.colors.primary)
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {


            Spacer(modifier = GlanceModifier.size(8.dp))

            Image(
                provider = ImageProvider(R.drawable.img_maodie),
                contentDescription = null,
                modifier = GlanceModifier
                    .size(68.dp)
                    .cornerRadius(100.dp)
            )

//            CircleIconButton(
//                imageProvider = ImageProvider(com.google.android.material.R.color.design_default_color_background),
//                contentDescription = null,
//                onClick = { /* Handle click */ },
//                modifier = GlanceModifier
//
//                    .size(68.dp)
//            )
            Spacer(modifier = GlanceModifier.size(8.dp))

            Column(
                modifier = GlanceModifier
                    //.background(GlanceTheme.colors.secondaryContainer)
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = date.lunarDate?.substringAfter(" ") ?: "error",
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 20.sp
                    ),
                    modifier = GlanceModifier.wrapContentSize()
                )
            }

            Spacer(modifier = GlanceModifier.size(8.dp))

        }
    }
}

// 最大尺寸的布局
@Composable
fun LargeWidgetLayout(date: CHNDateEnity, modifier: GlanceModifier) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = date.lunarDate ?: "error")
        Text(text = date.huangLiDate ?: "error")
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 400, heightDp = 100)
@Composable
fun PreWidgetContent() {
    GlanceTheme {
        WidgetContent(CHNDateEnity.covertForTest(CHNDate.test))
    }
}