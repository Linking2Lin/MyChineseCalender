package lins.applications.mychinesecalender

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lins.libs.module_base.data.DailyCache
import lins.libs.module_base.data.DailyRepository
import lins.libs.module_base.model.CHNDate
import org.junit.Assert.*
import org.junit.Test

/**
 * 替换 Main 调度器并注入仓库/日期，验证页面状态而非 Compose 绘制。
 * 每例 finally 清理 ViewModelStore 并恢复 Dispatchers.Main，避免残留协程影响其他测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    /**
     * 构造能通过日期校验的最小黄历模型，避免测试依赖真实接口。
     * @param date 样例所属日期，同时写入模型的公历展示字段。
     * @return CHNDate；包含指定公历日期与非空测试农历。
     */
    private fun data(date: LocalDate) = CHNDate(year = date.toString(), lunarDate = "测试农历")
    /** 页面用内存缓存：broken 同时模拟读写故障，观察流仍保留以接收仓库状态。 */
    private class Cache : DailyCache<CHNDate> {
        val rows = MutableStateFlow<Map<LocalDate, CHNDate>>(emptyMap())
        var broken = false
        /**
         * 按指定日期读取测试缓存，并允许主动制造磁盘故障。
         * @param date 本次精确查询的公历日期。
         * @return CHNDate?；不存在时为 null，读取故障时抛出 IOException。
         */
        override suspend fun read(date: LocalDate): CHNDate? {
            if (broken) throw IOException("database unavailable")
            return rows.value[date]
        }
        /**
         * 订阅指定日期的内存记录，模拟 Room 可观察查询。
         * @param date 需要观察的公历日期，不使用其他日期的记录兜底。
         * @return 冷 Flow；发出该日期的测试数据或 null，不发起网络请求。
         */
        override fun observe(date: LocalDate) = rows.map { it[date] }
        /**
         * 模拟同日缓存替换；故障开关在修改数据前生效。
         * @param date 目标记录的公历日期键。
         * @param data 需要保存的测试数据。
         * @return Unit；替换内存表中的指定日记录，写入故障时抛出 IOException。
         */
        override suspend fun write(date: LocalDate, data: CHNDate) {
            if (broken) throw IOException("disk full")
            rows.value += date to data
        }
        /**
         * 该页面测试替身忽略清理；日期保留范围由 DailyRepositoryTest 单独验证。
         * @param before 保留闭区间起点；页面测试替身不使用此参数。
         * @param after 保留闭区间终点；页面测试替身不使用此参数。
         * @return Unit；不修改测试数据。
         */
        override suspend fun prune(before: LocalDate, after: LocalDate) = Unit
    }

    /**
     * 旧日期请求持续挂起时切换今天，验证页面取消旧加载并只接收新日期结果。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun changingDayCancelsOldLoadAndReplacesSelectedDate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // viewModelScope 使用 Main；替换后与仓库共用同一个可控测试调度器。
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val original = LocalDate.of(2026, 9, 6)
            var today = original
            val repository = DailyRepository(Cache(), { date ->
                if (date == original) awaitCancellation()
                data(date)
            }, { value, date -> value.isValidFor(date) }, { today }, dispatcher)
            val vm = MainViewModel(repository, { today }, dispatcher)
            // 交给 Store 托管，finally 中 clear 会取消 ViewModel 的长寿命订阅。
            store.put("calendar", vm)
            vm.refreshCalendar()
            runCurrent()
            today = today.plusDays(1)
            vm.refreshCalendar()
            advanceUntilIdle()
            assertEquals(today, vm.calendar.value.date)
            assertTrue(vm.calendar.value.data!!.isValidFor(today))
            assertFalse(vm.calendar.value.loading)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    /**
     * 同日再次恢复前台应命中缓存；随后强制刷新失败时保留内容并发布网络错误。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun foregroundReentryUsesCacheAndExplicitRetryShowsFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // viewModelScope 使用 Main；替换后与仓库共用同一个可控测试调度器。
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val today = LocalDate.of(2026, 9, 6)
            var requests = 0
            val repository = DailyRepository(Cache(), { date ->
                if (++requests > 1) throw IOException("offline")
                data(date)
            }, { value, date -> value.isValidFor(date) }, { today }, dispatcher)
            val vm = MainViewModel(repository, { today }, dispatcher)
            // 交给 Store 托管，finally 中 clear 会取消 ViewModel 的长寿命订阅。
            store.put("calendar", vm)
            vm.refreshCalendar(); advanceUntilIdle()
            vm.refreshCalendar(); advanceUntilIdle()
            assertEquals(1, requests)
            vm.refreshCalendar(force = true); advanceUntilIdle()
            assertEquals(2, requests)
            assertNotNull(vm.calendar.value.error)
            assertNotNull(vm.calendar.value.data)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    /**
     * 缓存读写都失败时，页面仍显示有效网络数据，并单独发布缓存错误。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun databaseFailureStillDisplaysNetworkDataWithoutCrashing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // viewModelScope 使用 Main；替换后与仓库共用同一个可控测试调度器。
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val today = LocalDate.of(2026, 9, 6)
            val repository = DailyRepository(Cache().apply { broken = true }, { data(it) },
                { value, date -> value.isValidFor(date) }, { today }, dispatcher)
            val vm = MainViewModel(repository, { today }, dispatcher)
            // 交给 Store 托管，finally 中 clear 会取消 ViewModel 的长寿命订阅。
            store.put("calendar", vm)
            vm.refreshCalendar(); advanceUntilIdle()
            assertNotNull(vm.calendar.value.data)
            assertNotNull(vm.calendar.value.cacheError)
            assertNull(vm.calendar.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
