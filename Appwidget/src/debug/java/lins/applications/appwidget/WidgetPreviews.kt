package lins.applications.appwidget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import lins.libs.module_base.model.LunarDateResponse

/**
 * 保留原有整体/大/中/小布局预览，数据与尺寸沿用精调布局时的版本。
 * 预览依赖仅声明为 debugImplementation，因此保留在 src/debug，不放回 main。
 * @return Unit；为 IDE 发出静态 Glance 预览内容，不创建仓库或请求网络。
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
 * 预览无日期、无自定义头像时的统一空态，检查中等布局的默认头像和两行固定文案。
 * @return Unit；为 IDE 发出静态 Glance 预览内容，不创建仓库或请求网络。
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
 * 直接预览保留的 MaxWidgetLayout；此预览不代表正式大尺寸分支已经启用它。
 * @return Unit；为 IDE 发出静态 Glance 预览内容，不创建仓库或请求网络。
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
 * 预览有农历数据、使用默认头像的中等胶囊布局。
 * @return Unit；为 IDE 发出静态 Glance 预览内容，不创建仓库或请求网络。
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
 * 直接预览小尺寸的农历月日布局，避免经过 WidgetContent 的其他尺寸分支。
 * @return Unit；为 IDE 发出静态 Glance 预览内容，不创建仓库或请求网络。
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
