package lins.applications.appwidget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import lins.libs.module_base.model.LunarDateResponse

/**
 * 保留原有整体/大/中/小布局预览，数据与尺寸沿用精调布局时的版本。
 * 预览依赖仅声明为 debugImplementation，因此保留在 src/debug，不放回 main。
 * @return Unit；发出原有 Compose/Glance 内容，不返回业务数据或改变既有视觉参数。
 */
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

/**
 * 按当前数据与状态执行原有流程。
 * @return Unit；发出原有 Compose/Glance 内容，不返回业务数据或改变既有视觉参数。
 */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 410, heightDp = 100)
@Composable
fun PreEmptyWidgetContent() {
    GlanceTheme {
        WidgetContent(date = null, customBitmap = null)
    }
}

/**
 * 按当前数据与状态执行原有流程。
 * @return Unit；发出原有 Compose/Glance 内容，不返回业务数据或改变既有视觉参数。
 */
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

/**
 * 按当前数据与状态执行原有流程。
 * @return Unit；发出原有 Compose/Glance 内容，不返回业务数据或改变既有视觉参数。
 */
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

/**
 * 按当前数据与状态执行原有流程。
 * @return Unit；发出原有 Compose/Glance 内容，不返回业务数据或改变既有视觉参数。
 */
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
