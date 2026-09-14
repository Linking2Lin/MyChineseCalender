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
    /**
     * 使用夏令时开始和结束日，验证下一午夜按本地日历计算，间隔分别为 23 和 25 小时。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun midnightUsesLocalTimezoneAndDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val spring = Clock.fixed(Instant.parse("2026-03-08T05:00:00Z"), zone)
        val fall = Clock.fixed(Instant.parse("2026-11-01T04:00:00Z"), zone)
        assertEquals(23 * 3_600_000L, CalendarDates.nextMidnightMillis(spring) - spring.millis())
        assertEquals(25 * 3_600_000L, CalendarDates.nextMidnightMillis(fall) - fall.millis())
    }

    /**
     * 距离本地午夜仅一秒时，验证日期轮询缩短等待，不额外延后一个完整检查周期。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
    @Test fun checkAtMidnightDoesNotWaitAnotherThirtySeconds() {
        val clock = Clock.fixed(Instant.parse("2026-09-06T15:59:59Z"), ZoneId.of("Asia/Shanghai"))
        assertEquals(1_000L, CalendarDates.millisUntilCheck(clock))
    }

    /**
     * 推进虚拟时间并修改注入日期，验证同日去重、正常跨日及手动回拨均按约定发射。
     * @return Unit；断言通过时正常结束，失败由 JUnit 报告。
     */
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
