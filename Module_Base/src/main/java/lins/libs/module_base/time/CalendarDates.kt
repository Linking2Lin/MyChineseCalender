package lins.libs.module_base.time

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * 统一“今天”的定义：使用设备当前时区的公历日期，不固定为 UTC 或服务端时区。
 * 日期轮询用于存活的前台/Glance 协程，进程停止后的刷新由 WidgetScheduler 接管。
 */
object CalendarDates {
    /** 每次重新读取系统时区与时间；不要把结果作为长期静态常量缓存。 */
    fun today(): LocalDate = LocalDate.now()

    /**
     * 返回下一本地日期起点的时间戳。使用日历运算而非“现在加 24 小时”，兼容夏令时的
     * 23/25 小时日；默认 Clock 每次新建，使用户修改时区后立即影响下一次计算。
     */
    fun nextMidnightMillis(clock: Clock = Clock.systemDefaultZone()): Long =
        LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()

    /** 最多 30 秒检查一次；临近午夜时缩短等待，并避免 0 毫秒忙循环。并不保证协程准点唤醒。 */
    fun millisUntilCheck(clock: Clock = Clock.systemDefaultZone()): Long =
        Duration.between(clock.instant(), LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone))
            .toMillis().coerceIn(1, 30_000)

    /**
     * 每个新订阅立即发出日期，之后只发出变化（包括时间回拨）。收集方取消时，delay 一并取消。
     * 两个函数参数供测试注入可控日期与等待周期；生产通常使用默认值。
     */
    fun changes(today: () -> LocalDate = ::today, waitMillis: () -> Long = { millisUntilCheck() }) = flow {
        while (true) {
            emit(today())
            delay(waitMillis())
        }
    }.distinctUntilChanged()
}
