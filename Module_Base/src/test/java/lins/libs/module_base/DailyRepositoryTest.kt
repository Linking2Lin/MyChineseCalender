package lins.libs.module_base

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
    private class Cache : DailyCache<String> {
        val rows = MutableStateFlow<Map<LocalDate, String>>(emptyMap())
        var failRead = false
        var failWrite = false
        var writes = 0
        override suspend fun read(date: LocalDate): String? {
            if (failRead) throw IOException("unreadable")
            return rows.value[date]
        }
        override fun observe(date: LocalDate) = rows.map { it[date] }
        override suspend fun write(date: LocalDate, data: String) {
            if (failWrite) throw IOException("disk full")
            writes++
            rows.value += date to data
        }
        override suspend fun prune(before: LocalDate, after: LocalDate) {
            rows.value = rows.value.filterKeys { it >= before && it <= after }
        }
    }

    // 十个并发普通请求只联网一次，同日只保存一行；强制刷新不属于这一去重承诺。
    @Test fun concurrentLoadsFetchOnceAndStoreOneRow() = runTest {
        val cache = Cache()
        var requests = 0
        val repository = DailyRepository(cache, { requests++; delay(100); "$it valid" },
            { data, date -> data.startsWith(date.toString()) }, { today })
        val results = List(10) { async { repository.load(today) } }.awaitAll()
        assertEquals(1, requests)
        assertEquals(1, cache.writes)
        assertEquals(1, cache.rows.value.size)
        assertTrue(results.all { it.data == "$today valid" })
    }

    // 离线且只有昨天/明天缓存时，今天必须保持空态并携带今天的查询日期。
    @Test fun yesterdayAndFutureCacheNeverAppearAsToday() = runTest {
        val cache = Cache()
        cache.rows.value = mapOf(today.minusDays(1) to "old", today.plusDays(1) to "future")
        val repository = DailyRepository(cache, { throw IOException("offline") }, { _, _ -> true }, { today })
        val result = repository.load(today)
        assertNull(result.data)
        assertEquals(today, result.date)
        assertNotNull(result.error)
    }

    // 强制刷新返回残缺内容时显示错误，但保留原先同日有效缓存且不执行覆盖写入。
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

    // 磁盘故障不丢弃已获取数据；存储恢复后只补写缓存，不重复联网。
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

    // 同一个订阅者必须看到首次空态、加载成功以及第二次刷新结果，保护 Glance 活跃会话。
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

    // 取消是控制流，必须抛给调用方，不能转换成数据并写入缓存。
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

    // 人为延迟旧日请求并切换日期，确认它完成时不会污染新日期的观察状态。
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
}
