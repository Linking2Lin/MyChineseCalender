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

    // 旧编码已开始后再创建新序号，旧请求必须放弃提交，最终只保留完整的新文件。
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
        encoding.await()
        val secondId = writer.newRequest()
        val second = async { writer.write(secondId) { it.write("new-image".toByteArray()) } }
        finish.complete(Unit)
        assertFalse(first.await())
        assertTrue(second.await())
        assertEquals("new-image", target.readText())
        assertEquals(listOf("avatar.png"), folder.root.list()!!.toList())
    }

    // 编码写出一部分后故意失败，已有文件必须保持原内容，临时文件必须清理。
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
