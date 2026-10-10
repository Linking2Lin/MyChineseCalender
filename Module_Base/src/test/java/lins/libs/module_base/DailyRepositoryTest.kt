package lins.libs.module_base

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import lins.libs.module_base.data.DailyCache
import lins.libs.module_base.data.DailyRepository
import lins.libs.module_base.data.DateLoadResult
import org.junit.Assert.*
import org.junit.Test

/**
 * 按日仓库的行为约定：并发去重、日期隔离、故障恢复和活跃订阅更新。
 * 内存 Cache 可主动制造读写故障；不连接真实 Room，数据库迁移另由仪器测试验证。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyRepositoryTest {
    private val today = LocalDate.of(2026, 9, 6)
    /** 可注入读、写、清理故障的内存缓存；仅用于协程测试，不承担真实数据库并发语义。 */
    private class Cache : DailyCache<String> {
        val rows = MutableStateFlow<Map<LocalDate, String>>(emptyMap())
        // 故障开关分别对应读取、写入和清理阶段；不让一个开关掩盖其他路径。
        var failRead = false
        var failWrite = false
        var failPrune = false
        var cancelPrune = false
        // prunes 统计尝试次数（包含失败），writes 仅统计成功进入写入的次数。
        var prunes = 0
        var observeDelayMillis = 0L
        var writes = 0
        /**
         * 按指定日期读取测试缓存，并允许主动制造磁盘故障。
         * @param date 本次精确查询的公历日期。
         * @return String?；不存在时为 null，读取故障时抛出 IOException。
         */
        override suspend fun read(date: LocalDate): String? {
            if (failRead) throw IOException("unreadable")
            return rows.value[date]
        }
        /**
         * 订阅指定日期的内存记录，可延迟首值以模拟慢磁盘。
         * @param date 需要观察的公历日期，不使用其他日期的记录兜底。
         * @return 冷 Flow；发出该日期的测试数据或 null，不发起网络请求。
         */
        override fun observe(date: LocalDate) = rows.map { it[date] }
            .onStart { delay(observeDelayMillis) }
        /**
         * 模拟同日缓存替换；故障开关在修改数据前生效。
         * @param date 目标记录的公历日期键。
         * @param data 需要保存的测试数据。
         * @return Unit；替换内存表中的指定日记录，写入故障时抛出 IOException。
         */
        override suspend fun write(date: LocalDate, data: String) {
            if (failWrite) throw IOException("disk full")
            writes++
            rows.value += date to data
        }
        /**
         * 统计清理调用，并模拟取消、磁盘失败或成功删除区间外记录。
         * @param before 保留闭区间起点，包含当天。
         * @param after 保留闭区间终点，包含当天。
         * @return Unit；成功时过滤记录，故障或取消时抛出对应异常。
         */
        override suspend fun prune(before: LocalDate, after: LocalDate) {
            prunes++
            if (cancelPrune) throw CancellationException("cancel during cleanup")
            if (failPrune) throw IOException("cleanup failed")
            rows.value = rows.value.filterKeys { it >= before && it <= after }
        }
    }

    /**
     * 同时发起十次同日普通加载，验证只请求一次网络且只保存一行有效缓存。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun concurrentLoadsFetchOnceAndStoreOneRow() = runTest {
        val cache = Cache()
        var requests = 0
        val repository = DailyRepository(cache, { requests++; delay(100); "$it valid" },
            { data, date -> data.startsWith(date.toString()) }, { today })
        // 网络适配器中的虚拟延迟让请求重叠，观察同日互斥是否复用前一次结果。
        val results = List(10) { async { repository.load(today) } }.awaitAll()
        assertEquals(1, requests)
        assertEquals(1, cache.writes)
        assertEquals(1, cache.rows.value.size)
        assertTrue(results.all { it.data == "$today valid" })
    }

    /**
     * 离线且只有昨天与明天缓存时，验证今天保持无数据状态，并保留今天的查询身份。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun yesterdayAndFutureCacheNeverAppearAsToday() = runTest {
        val cache = Cache()
        cache.rows.value = mapOf(today.minusDays(1) to "old", today.plusDays(1) to "future")
        val repository = DailyRepository(cache, { throw IOException("offline") }, { _, _ -> true }, { today })
        val result = repository.load(today)
        assertNull(result.data)
        assertEquals(today, result.date)
        assertNotNull(result.error)
    }

    /**
     * 强制刷新取得残缺数据时，验证返回错误但不覆盖同日有效缓存。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun invalidForcedResponsePreservesValidCache() = runTest {
        val cache = Cache()
        cache.rows.value = mapOf(today to "valid")
        val repository = DailyRepository(cache, { "incomplete" }, { data, _ -> data == "valid" }, { today })
        val result = repository.load(today, force = true)
        assertEquals("valid", result.data)
        assertEquals("valid", cache.read(today))
        assertNotNull(result.error)
        assertEquals(0, cache.writes)
    }

    /**
     * 延迟首次磁盘观察，验证无内存状态时等待真实缓存首值，不预先发空值或联网。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun firstObservationWaitsForPersistedTodayCache() = runTest {
        val cache = Cache().apply {
            rows.value = mapOf(today.minusDays(1) to "old", today to "valid")
            observeDelayMillis = 100
        }
        var requests = 0
        val repository = DailyRepository(cache, { requests++; "network" },
            { data, _ -> data == "valid" }, { today }, StandardTestDispatcher(testScheduler))
        val snapshot = async { repository.observe(today).first() }
        runCurrent()
        assertFalse(snapshot.isCompleted)
        // 推进虚拟时钟后再执行当前到期任务，不等待真实 100 毫秒。
        advanceTimeBy(100)
        runCurrent()
        assertEquals(today, snapshot.await().date)
        assertEquals("valid", snapshot.await().data)
        assertEquals(0, requests)
    }

    /**
     * 同日强刷离线时检查全部观察状态仍有缓存；切到无缓存的新日期后必须为空。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun offlineRefreshKeepsCacheInEveryObservedStateButNotAcrossDays() = runTest {
        val cache = Cache().apply { rows.value = mapOf(today to "valid") }
        var now = today
        val repository = DailyRepository(cache, { delay(100); throw IOException("offline") },
            { data, _ -> data == "valid" }, { now }, StandardTestDispatcher(testScheduler))
        val observed = mutableListOf<DateLoadResult<String>>()
        backgroundScope.launch { repository.observe(today).collect { observed += it } }
        runCurrent()

        val refresh = async { repository.load(today, force = true) }
        runCurrent()
        assertTrue(observed.last().loading)
        // 推进虚拟时钟后再执行当前到期任务，不等待真实 100 毫秒。
        advanceTimeBy(100)
        runCurrent()
        assertNotNull(refresh.await().error)
        assertNotNull(observed.last().error)
        assertFalse(observed.last().loading)
        assertTrue(observed.all { it.date == today && it.data == "valid" })

        now = today.plusDays(1)
        val nextDay = repository.observe(now).first()
        assertEquals(now, nextDay.date)
        assertNull(nextDay.data)
    }

    /**
     * 先使缓存读写失败，再恢复磁盘，验证有效网络数据不丢失且后续只补写、不重复联网。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun databaseFailureKeepsLiveDataAndRetriesPersistence() = runTest {
        val cache = Cache().apply { failRead = true; failWrite = true }
        var requests = 0
        val repository = DailyRepository(cache, { requests++; "valid" }, { _, _ -> true }, { today })
        val first = repository.load(today)
        assertEquals("valid", first.data)
        assertNotNull(first.cacheError)
        cache.failRead = false
        cache.failWrite = false
        val retry = repository.load(today)
        assertNull(retry.cacheError)
        assertEquals("valid", cache.read(today))
        assertEquals(1, requests)
    }

    /**
     * 保持同一个订阅，验证它依次收到空态、首次加载结果和后续强刷结果。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun activeObserverSeesEmptyThenNewDataAndSubsequentRefresh() = runTest {
        val cache = Cache()
        var version = 0
        val repository = DailyRepository(cache, { "value-${++version}" }, { _, _ -> true },
            { today }, StandardTestDispatcher(testScheduler))
        val observed = mutableListOf<DateLoadResult<String>>()
        val job = backgroundScope.launch { repository.observe(today).collect { observed += it } }
        runCurrent()
        assertNull(observed.last().data)
        repository.load(today)
        runCurrent()
        assertEquals("value-1", observed.last().data)
        repository.load(today, force = true)
        runCurrent()
        assertEquals("value-2", observed.last().data)
        job.cancel()
    }

    /**
     * 网络加载抛出取消异常时，验证调用方能收到取消且缓存中没有写入失败内容。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun cancellationIsRethrownAndDoesNotWriteFailurePayload() = runTest {
        val cache = Cache()
        val repository = DailyRepository(cache, { throw CancellationException("stop") }, { _, _ -> true }, { today })
        try {
            repository.load(today)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(cache.rows.value.isEmpty())
        }
    }

    /**
     * 延迟旧日请求并切换观察日期，验证迟到的旧结果不会污染新日订阅。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun delayedPreviousDayDoesNotLeakIntoNextDayObserver() = runTest {
        val cache = Cache()
        var now = today
        val repository = DailyRepository(cache, { date -> delay(100); date.toString() },
            { data, date -> data == date.toString() }, { now }, StandardTestDispatcher(testScheduler))
        val yesterdayLoad = async { repository.load(today) }
        runCurrent()
        now = today.plusDays(1)
        var displayed: DateLoadResult<String>? = null
        backgroundScope.launch { repository.observe(now).collect { displayed = it } }
        runCurrent()
        yesterdayLoad.await()
        runCurrent()
        assertEquals(now, displayed?.date)
        assertNull(displayed?.data)
        repository.load(now)
        runCurrent()
        assertEquals(now.toString(), displayed?.data)
    }

    /**
     * 磁盘旧值与新内存值并存时，验证补写失败仍保留新值，恢复后修复磁盘且不再次联网。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun newerMemorySurvivesFailedWriteAndRepairsOldDisk() = runTest {
        val cache = Cache().apply { rows.value = mapOf(today to "old"); failWrite = true }
        var requests = 0
        val repository = DailyRepository(cache, { requests++; "new" }, { _, _ -> true }, { today })
        assertEquals("new", repository.load(today, force = true).data)
        val failedRepair = repository.load(today)
        assertEquals("new", failedRepair.data)
        assertNotNull(failedRepair.cacheError)
        // 恢复磁盘后仍走普通加载：必须补写新内存值，不能重新申请网络数据。
        cache.failWrite = false
        val repaired = repository.load(today)
        assertEquals("new", repaired.data)
        assertEquals("new", cache.read(today))
        assertNull(repaired.cacheError)
        assertEquals(1, requests)
    }

    /**
     * 首次写入成功但清理失败，验证下次命中缓存也会重试并删除过期记录。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun cachedLoadRetriesFailedCleanup() = runTest {
        val cache = Cache().apply {
            rows.value = mapOf(today.minusDays(30) to "expired")
            failPrune = true
        }
        val repository = DailyRepository(cache, { "new" }, { _, _ -> true }, { today })
        assertNotNull(repository.load(today).cacheError)
        cache.failPrune = false
        assertNull(repository.load(today).cacheError)
        assertEquals(2, cache.prunes)
        assertFalse(cache.rows.value.containsKey(today.minusDays(30)))
    }

    /**
     * 新数据已写入后在清理阶段取消，验证内存保持新值，普通加载能继续补做清理。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun cancellationAfterWriteKeepsNewestDataAndRetriesCleanup() = runTest {
        val cache = Cache().apply { rows.value = mapOf(today to "old"); cancelPrune = true }
        val repository = DailyRepository(cache, { "new" }, { _, _ -> true }, { today }, StandardTestDispatcher(testScheduler))
        try { repository.load(today, force = true); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals("new", cache.read(today))
        assertEquals("new", repository.observe(today).first().data)
        cache.cancelPrune = false
        repository.load(today)
        assertEquals(2, cache.prunes)
    }

    /**
     * 仓库已有内存结果时延迟磁盘首值，验证新订阅无需等待磁盘就能取得该结果。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun knownMemoryIsEmittedBeforeSlowDiskObservation() = runTest {
        val cache = Cache()
        val repository = DailyRepository(cache, { "valid" }, { _, _ -> true }, { today }, StandardTestDispatcher(testScheduler))
        repository.load(today)
        cache.observeDelayMillis = 10_000
        val state = async { repository.observe(today).first() }
        runCurrent()
        assertTrue(state.isCompleted)
        assertEquals("valid", state.await().data)
    }

    /**
     * 让明日预取一直挂起，验证今天的加载仍能完成，同日去重由单独的并发用例覆盖。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun stalledTomorrowPrefetchDoesNotBlockToday() = runTest {
        // 显式握手确保明日请求已经进入网络阶段，再测试今天是否能独立完成。
        val started = CompletableDeferred<Unit>()
        val repository = DailyRepository(Cache(), { date ->
            if (date == today.plusDays(1)) { started.complete(Unit); awaitCancellation() }
            "today"
        }, { _, _ -> true }, { today })
        val tomorrow = backgroundScope.launch { repository.load(today.plusDays(1)) }
        started.await()
        val current = async { repository.load(today) }
        runCurrent()
        try { assertTrue(current.isCompleted); assertEquals("today", current.await().data) }
        finally { tomorrow.cancel() }
    }
}
