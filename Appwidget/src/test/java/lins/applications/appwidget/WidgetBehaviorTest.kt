package lins.applications.appwidget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import lins.applications.appwidget.helper.ImageGeometry
import lins.applications.appwidget.helper.updateWidgetsIndependently
import org.junit.Assert.*
import org.junit.Test

/**
 * 验证图片裁剪的数值约束和更新失败策略，不启动桌面宿主。
 * 小组件沿用用户原有布局，不对其施加新的字号、头像或尺寸边界断言。
 */
class WidgetBehaviorTest {
    @Test fun cropIsCenteredForLandscapeAndPortrait() {
        val landscape = ImageGeometry.centerCrop(512, 256)
        assertEquals(128, landscape.left)
        assertEquals(0, landscape.top)
        val portrait = ImageGeometry.centerCrop(256, 512)
        assertEquals(0, portrait.left)
        assertEquals(128, portrait.top)
    }

    @Test fun extremeAspectRatiosNeverCreateZeroSizedBitmaps() {
        assertEquals(1, ImageGeometry.fit(1, 100_000, 512).width)
        assertEquals(1, ImageGeometry.fit(100_000, 1, 512).height)
        assertEquals(256, ImageGeometry.fit(512, 256, 512).height)
    }

    @Test fun oneWidgetFailureDoesNotSkipRemainingWidgetsOrReportSuccess() = runTest {
        val visited = mutableListOf<Int>()
        val result = updateWidgetsIndependently(listOf(1, 2, 3)) {
            visited += it
            if (it == 2) error("widget removed")
        }
        assertEquals(listOf(1, 2, 3), visited)
        assertEquals(1, result.failures)
        assertFalse(result.succeeded)
    }

    @Test fun cancellationDoesNotBecomePartialSuccess() = runTest {
        try {
            updateWidgetsIndependently(listOf(1)) { throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
