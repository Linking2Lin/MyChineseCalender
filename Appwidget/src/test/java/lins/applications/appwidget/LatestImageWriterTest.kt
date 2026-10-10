package lins.applications.appwidget

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import lins.applications.appwidget.helper.LatestImageWriter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 直接验证临时文件及提交顺序，不依赖 Bitmap。TemporaryFolder 隔离测试文件，
 * CompletableDeferred 显式控制“旧请求尚未完成时发生新选择”，避免靠真实 sleep 制造竞争。
 */
class LatestImageWriterTest {
    @get:Rule val folder = TemporaryFolder()

    /**
     * 旧图片正在编码时产生新选择，验证旧请求不能提交，最终文件只包含完整的新图片。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun newerSelectionWinsEvenWhenOldEncodingIsStillRunning() = runTest {
        val target = File(folder.root, "avatar.png")
        val writer = LatestImageWriter(target)
        val encoding = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val firstId = writer.newRequest()
        val first = async { writer.write(firstId) {
            it.write("old".toByteArray())
            encoding.complete(Unit)
            finish.await()
        } }
        // 先等旧请求开始写入，再模拟新选择，避免测试依赖线程抢占的偶然顺序。
        encoding.await()
        val secondId = writer.newRequest()
        val second = async { writer.write(secondId) { it.write("new-image".toByteArray()) } }
        // 放行旧编码后，它必须重新检查序号；新请求才可以最终替换目标文件。
        finish.complete(Unit)
        assertFalse(first.await())
        assertTrue(second.await())
        assertEquals("new-image", target.readText())
        assertEquals(listOf("avatar.png"), folder.root.list()!!.toList())
    }

    /**
     * 编码部分内容后抛出异常，验证原图片保持完整且本次临时文件被清理。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun encodingFailurePreservesExistingFileAndRemovesTemporaryFile() = runTest {
        val target = File(folder.root, "avatar.png").apply { writeText("original") }
        val writer = LatestImageWriter(target)
        try {
            writer.write(writer.newRequest()) { it.write("partial".toByteArray()); throw IOException("encode failed") }
            fail("Expected encoding failure")
        } catch (_: IOException) { }
        assertEquals("original", target.readText())
        assertEquals(listOf("avatar.png"), folder.root.list()!!.toList())
    }
}
