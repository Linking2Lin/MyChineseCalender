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
    private fun data(date: LocalDate) = CHNDate(year = date.toString(), lunarDate = "测试农历")
    private class Cache : DailyCache<CHNDate> {
        val rows = MutableStateFlow<Map<LocalDate, CHNDate>>(emptyMap())
        var broken = false
        override suspend fun read(date: LocalDate): CHNDate? {
            if (broken) throw IOException("database unavailable")
            return rows.value[date]
        }
        override fun observe(date: LocalDate) = rows.map { it[date] }
        override suspend fun write(date: LocalDate, data: CHNDate) {
            if (broken) throw IOException("disk full")
            rows.value += date to data
        }
        override suspend fun prune(before: LocalDate, after: LocalDate) = Unit
    }

    // 让旧请求一直挂起，再切日，确认旧加载被取消且页面只展示新日期的结果。
    @Test fun changingDayCancelsOldLoadAndReplacesSelectedDate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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

    // 同日前台恢复复用缓存；随后手动强刷失败时仍保留原数据并显示错误。
    @Test fun foregroundReentryUsesCacheAndExplicitRetryShowsFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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

    // 缓存读写均失败时，页面继续展示网络数据，同时给出独立的缓存错误。
    @Test fun databaseFailureStillDisplaysNetworkDataWithoutCrashing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val today = LocalDate.of(2026, 9, 6)
            val repository = DailyRepository(Cache().apply { broken = true }, { data(it) },
                { value, date -> value.isValidFor(date) }, { today }, dispatcher)
            val vm = MainViewModel(repository, { today }, dispatcher)
            store.put("calendar", vm)
            vm.refreshCalendar(); advanceUntilIdle()
            assertNotNull(vm.calendar.value.data)
            assertNotNull(vm.calendar.value.cacheError)
            assertNull(vm.calendar.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
