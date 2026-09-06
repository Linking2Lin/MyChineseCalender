package lins.applications.appwidget.helper

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 同一目标文件的“最后选择生效”写入器，与 Bitmap/Android 解耦，便于直接测试竞争时序。
 *
 * Mutex 串行化编码与写入，避免并发占用大量图片内存；每次写入使用独立临时文件，
 * 防止失败清理影响其他请求。commitLock 将“创建新选择序号”和“检查后替换文件”互斥，
 * 不能只依赖原子计数器，否则检查序号到 rename 之间仍可能插入一次新选择。
 *
 * target 的父目录应已存在。临时文件位于同一目录，在应用内部文件系统上通过 rename
 * 替换使读者看到旧文件或完整新文件；这是进程内写入协调，不是跨进程文件锁。
 */
class LatestImageWriter(private val target: File) {
    private val latest = AtomicLong()
    private val mutex = Mutex()
    private val commitLock = Any()
    /** 在用户选择事件发生时立即取号，不能等后台协程开始后才取号，否则会丢失真实选择顺序。 */
    fun newRequest(): Long = synchronized(commitLock) { latest.incrementAndGet() }

    /**
     * 返回 true 表示已提交，false 表示被后续选择淘汰；编码/落盘异常继续抛给 UI 入口。
     * 最新选择失败时保留原目标，不让已经过时的请求随后覆盖它；不会自动回退到上一次选择。
     */
    suspend fun write(request: Long, encode: suspend (OutputStream) -> Unit): Boolean = mutex.withLock {
        if (request != latest.get()) return@withLock false
        val temporary = File.createTempFile(target.name, ".tmp", target.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                encode(output)
                // 编码完全结束并同步文件内容后，才允许它成为目标；目录级断电恢复不在此契约内。
                output.fd.sync()
            }
            // 编码可能包含非挂起阻塞操作，提交前显式检查取消，避免已取消请求继续替换文件。
            currentCoroutineContext().ensureActive()
            synchronized(commitLock) {
                if (request != latest.get()) false else {
                    check(temporary.renameTo(target)) { "Unable to replace widget image" }
                    true
                }
            }
        } finally {
            // 成功 rename 后源路径已不存在；失败/淘汰时这里只删除该请求自己的临时文件。
            temporary.delete()
        }
    }
}
