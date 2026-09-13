package lins.applications.mychinesecalender

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lins.applications.appwidget.data.CalendarRepositories
import lins.libs.module_base.data.DailyRepository
import lins.libs.module_base.data.DateLoadResult
import lins.libs.module_base.model.CHNDate
import lins.libs.module_base.time.CalendarDates
import lins.libs.module_poem.model.PoemResponse
import lins.libs.module_poem.repository.PoemRepository

/**
 * 主页状态协调者：黄历由按日仓库驱动，诗词独立加载，两者失败互不清空对方的数据。
 *
 * Activity 先 configure，再在 STARTED 生命周期中调用 watchDates。刷新入口与状态选择
 * 应从主线程调用；耗时 load 通过 loadDispatcher 执行，observer 在 viewModelScope 更新 UI。
 * 构造参数保留默认值以供 Android ViewModel 工厂创建，也允许测试注入仓库、日期与调度器。
 * @param calendarRepository 黄历仓库；null 时由 configure 装配真实依赖。
 * @param today 当前公历日期提供函数，默认读取设备当前时区。
 * @param loadDispatcher 黄历加载协程的调度器；测试可与虚拟时间调度器共用。
 */
class MainViewModel(
    private var calendarRepository: DailyRepository<CHNDate>? = null,
    private val today: () -> LocalDate = CalendarDates::today,
    private val loadDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _calendar = MutableStateFlow(DateLoadResult<CHNDate>(today()))
    val calendar = _calendar.asStateFlow()
    // selectedDate 是当前页面订阅的日期，不是最近一次网络完成的日期。
    private var selectedDate: LocalDate? = null
    // 订阅和加载分开管理：同一天强制刷新复用订阅，换日时两者都需要取消并重建。
    private var calendarObserver: Job? = null
    private var calendarLoad: Job? = null

    private var poemRepository: PoemRepository? = null
    private val _poem = MutableStateFlow<PoemResponse?>(null)
    val poem = _poem.asStateFlow()
    private val _isPoemLoading = MutableStateFlow(false)
    val isPoemLoading = _isPoemLoading.asStateFlow()
    private val _poemError = MutableStateFlow(false)
    val poemError = _poemError.asStateFlow()

    /**
     * 只在首次需要时装配真实依赖，配置重建不替换仓库；只向长生命周期对象传应用 Context。
     * @param context 调用入口的 Context；长生命周期依赖使用 applicationContext，避免持有页面。
     * @return Unit；补齐尚未设置的业务依赖，不立即返回网络数据。
     */
    fun configure(context: Context) {
        if (calendarRepository == null) calendarRepository = CalendarRepositories.get(context).almanac
        if (poemRepository == null) poemRepository = PoemRepository(context.applicationContext)
    }

    /**
     * 前台日期监听。repeatOnLifecycle 每次重新启动收集都会立即检查，覆盖后台跨天后返回。
     * 本收集结束只停止日期轮询，已启动的 viewModelScope 加载仍可完成并缓存结果。
     * @return Unit（挂起）；持续监听直到收集方取消，每次日期变化触发刷新。
     */
    suspend fun watchDates() {
        // 同一天重新进入也会 refresh，但非强制加载优先用缓存，不意味着每次前台都联网。
        CalendarDates.changes(today).collect { refreshCalendar() }
    }

    /**
     * 根据调用瞬间的今天选择状态：换日取消旧加载并订阅新日期，同日正在加载则忽略重复操作。
     * force 由手动按钮使用；遇到同日活动请求也不会额外排队一次 force。
     * @param force true 表示尝试强制联网；false 优先复用同日有效缓存。
     * @return Unit；排队加载并维护当前日期订阅，结果通过 calendar 状态流发布。
     */
    fun refreshCalendar(force: Boolean = false) {
        val date = today()
        val repository = checkNotNull(calendarRepository)
        if (selectedDate != date) {
            calendarLoad?.cancel()
            calendarObserver?.cancel()
            selectedDate = date
            _calendar.value = DateLoadResult(date, loading = true)
            calendarObserver = viewModelScope.launch {
                repository.observe(date).collect { result ->
                    // 双重守卫：既确认仍被页面选中，也防止日期轮询尚未运行时旧结果短暂冒充今天。
                    if (selectedDate == result.date && today() == result.date) _calendar.value = result
                }
            }
        } else if (calendarLoad?.isActive == true) {
            return
        }
        calendarLoad = viewModelScope.launch(loadDispatcher) {
            repository.load(date, force)
        }
    }

    /**
     * 首次进入时尝试加载；ViewModel 保留已有诗词，旋转/恢复页面不会强制更换内容。
     * @return Unit；缺少诗词且未加载时启动请求，已有诗词保持原样。
     */
    fun ensurePoem() {
        if (_poem.value == null && !_isPoemLoading.value) fetchPoem()
    }

    /**
     * 主动换一首；失败保留旧诗词并单独置错误标记，取消时仍通过 finally 清除加载状态。
     * @return Unit；启动异步请求，结果、错误和加载状态分别通过原有状态流发布。
     */
    fun fetchPoem() {
        if (_isPoemLoading.value) return
        _isPoemLoading.value = true
        _poemError.value = false
        viewModelScope.launch {
            try {
                val result = checkNotNull(poemRepository).fetchPoem()
                if (result != null) _poem.value = result else _poemError.value = true
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isPoemLoading.value = false
            }
        }
    }
}
