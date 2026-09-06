package lins.libs.module_base

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lins.libs.module_base.time.CalendarDates
import org.junit.Assert.*
import org.junit.Test

/**
 * 固定 Clock 与虚拟协程时间验证日期逻辑，不修改操作系统时钟，也不依赖真实等待。
 * 这些断言验证计算与 Flow，不能替代设备上 AlarmManager/Doze 的调度测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarDatesTest {
    // 夏令时切换日分别为 23/25 小时，防止把明天午夜写成当前时间加固定 24 小时。
    @Test fun midnightUsesLocalTimezoneAndDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val spring = Clock.fixed(Instant.parse("2026-03-08T05:00:00Z"), zone)
        val fall = Clock.fixed(Instant.parse("2026-11-01T04:00:00Z"), zone)
        assertEquals(23 * 3_600_000L, CalendarDates.nextMidnightMillis(spring) - spring.millis())
        assertEquals(25 * 3_600_000L, CalendarDates.nextMidnightMillis(fall) - fall.millis())
    }

    // 离午夜仅一秒时缩短轮询等待，避免固定周期额外延迟。
    @Test fun checkAtMidnightDoesNotWaitAnotherThirtySeconds() {
        val clock = Clock.fixed(Instant.parse("2026-09-06T15:59:59Z"), ZoneId.of("Asia/Shanghai"))
        assertEquals(1_000L, CalendarDates.millisUntilCheck(clock))
    }

    // 同日不重复发射，正常跨天与手动回拨都发出新日期；runCurrent 推进到期任务。
    @Test fun dateFlowEmitsRolloverAndClockRollbackWithoutDuplicates() = runTest {
        var now = LocalDate.of(2026, 9, 6)
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch { CalendarDates.changes({ now }, { 1_000L }).collect { dates += it } }
        runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, dates.size)
        now = now.plusDays(1)
        advanceTimeBy(1_000); runCurrent()
        now = now.minusDays(2)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("2026-09-06", "2026-09-07", "2026-09-05"), dates.map { it.toString() })
    }
}
