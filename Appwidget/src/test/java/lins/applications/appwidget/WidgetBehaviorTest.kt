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
    /**
     * 分别检查横图和竖图的裁剪起点，保证保留源图中心正方形。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun cropIsCenteredForLandscapeAndPortrait() {
        val landscape = ImageGeometry.centerCrop(512, 256)
        assertEquals(128, landscape.left)
        assertEquals(0, landscape.top)
        val portrait = ImageGeometry.centerCrop(256, 512)
        assertEquals(0, portrait.left)
        assertEquals(128, portrait.top)
    }

    /**
     * 检查极端长宽比和普通尺寸，保证缩放后的任一方向都至少保留 1 像素。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun extremeAspectRatiosNeverCreateZeroSizedBitmaps() {
        assertEquals(1, ImageGeometry.fit(1, 100_000, 512).width)
        assertEquals(1, ImageGeometry.fit(100_000, 1, 512).height)
        assertEquals(256, ImageGeometry.fit(512, 256, 512).height)
    }

    /**
     * 让中间一个小组件更新失败，验证后续实例仍被处理，整体统计也保留失败。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
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

    /**
     * 单实例更新被取消时，验证取消异常直接传播，不被计数成普通失败或部分成功。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun cancellationDoesNotBecomePartialSuccess() = runTest {
        try {
            updateWidgetsIndependently(listOf(1)) { throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
