package lins.applications.mychinesecalender

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lins.applications.appwidget.data.ChineseCalenderRepository
import lins.applications.appwidget.data.HkoRepository
import lins.libs.module_base.Constants
import lins.libs.module_base.database.AppDataBase
import lins.libs.module_base.model.CHNDate
import lins.libs.module_base.model.CHNDateEntity
import lins.libs.module_base.model.LunarDateEntity
import lins.libs.module_base.model.LunarDateResponse
import lins.libs.module_poem.model.PoemResponse
import lins.libs.module_poem.repository.PoemRepository
import java.time.LocalDate
import java.util.Calendar

class MainViewModel() : ViewModel() {

    // 用 StateFlow 保存主页面的“传统农历/黄历数据”。
    // 这样 UI 可以通过 collect 订阅状态变化，而不是手动调用刷新。
    private val _lunarDate = MutableStateFlow(CHNDate())
    val lunarDate: StateFlow<CHNDate> = _lunarDate.asStateFlow()

    // 用 StateFlow 保存“今日诗词”。
    // 允许 UI 在诗词到达后自动重组。
    private val _poem = MutableStateFlow<PoemResponse?>(null)
    val poem: StateFlow<PoemResponse?> = _poem.asStateFlow()

    // 诗词仓库懒加载缓存，避免每次请求都重新创建对象。
    private var poemRepository: PoemRepository? = null

    // 记录当前进行中的诗词请求。
    // 用户连续点击“刷新诗词”时，旧请求会被取消，避免重复并发。
    private var fetchPoemJob: Job? = null

    private val _isPoemLoading = MutableStateFlow(false)
    val isPoemLoading: StateFlow<Boolean> = _isPoemLoading.asStateFlow()

    // 诗词仓库需要 Context 才能访问 DataStore，所以这里统一从 applicationContext 构建，避免 Activity 泄露。
    private fun getPoemRepository(context: Context): PoemRepository {
        return poemRepository ?: PoemRepository(context.applicationContext).also {
            poemRepository = it
        }
    }

    // 拉取今日诗词。
    // 该方法只负责更新 _poem 状态，不直接操作 UI，从而保持 ViewModel 的职责单一。
    fun fetchPoem(context: Context) {
        fetchPoemJob?.cancel()
        fetchPoemJob = viewModelScope.launch {
            _isPoemLoading.value = true
            try {
                val result = getPoemRepository(context).fetchPoem()
                if (result != null) {
                    _poem.value = result
                }
            } finally {
                _isPoemLoading.value = false
            }
        }
    }

    // 内部可变的农历缓存状态。
    // 该数据来源于 HKO 接口，主要服务于页面显示和 widget 同步。
    private val _lunarData = MutableStateFlow<LunarDateResponse?>(null)
    // 对外只暴露只读版本，避免外部随意写入。
    val lunarData: StateFlow<LunarDateResponse?> = _lunarData.asStateFlow()

    /**
     * 获取今天的农历信息。
     * 读取顺序是：
     * 1. 先查本地数据库缓存，让页面尽快显示
     * 2. 再请求网络接口获取最新数据
     * 3. 如果网络成功，则覆盖本地缓存并更新 UI
     */
    fun getTodayLunarInfo(context: Context) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val dateString = today.format(Constants.DATE_FORMATTER)

            val db = AppDataBase.getInstance(context)

            // 先读缓存，提升启动速度和弱网体验。
            val cached = withContext(Dispatchers.IO) {
                db.lunarDateDao().getByDate(dateString)
            }
            if (cached != null) {
                _lunarData.value = cached.toResponse()
            }

            // 再请求网络，若拿到有效新数据则刷新缓存和 UI。
            val result = withContext(Dispatchers.IO) {
                HkoRepository.fetchLunarDate(dateString)
            }

            if (result.isValid()) {
                val validResult = result!!
                _lunarData.value = validResult
                withContext(Dispatchers.IO) {
                    LunarDateEntity.fromResponse(dateString, validResult)?.let { entity ->
                        db.lunarDateDao().insertOrReplace(entity)
                    }
                }
            }
        }
    }

    private fun LunarDateResponse?.isValid(): Boolean {
        return this != null && lunarYear.isNotBlank() && lunarDate.isNotBlank()
    }

    private fun CHNDate.isValid(): Boolean {
        return year != null || lunarDate != null || huangLiDate != null || huiLiDate != null || ganZhiDate != null || wuXing != null || zhiRiXingShen != null || yi != null || ji != null
    }

    /**
     * 获取当前日历对应的通用黄历数据。
     * 这个接口是主界面更“完整”的数据来源，会落库保存，供后续直接读取。
     *
     * @param applicationContext 使用 applicationContext 访问数据库，避免持有 Activity 引用。
     * @param after 执行结束后的回调，无论成功失败都会执行，适合做收尾动作。
     */
    fun getLunarDate(
        applicationContext: Context,
        after: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repository = ChineseCalenderRepository()
                val calendar = Calendar.getInstance()
                val result = repository.getLunarDate(
                    currentYear = calendar.get(Calendar.YEAR).toString(),
                    currentMonth = (calendar.get(Calendar.MONTH) + 1).toString(),
                    currentDay = calendar.get(Calendar.DAY_OF_MONTH).toString()
                )
                val db = AppDataBase.getInstance(applicationContext)

                db.chnDateDao().insertDate(CHNDateEntity.convert(result))
                _lunarDate.value = result
            } finally {
                withContext(Dispatchers.Main) { after() }
            }
        }
    }
}
