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

    // 统一使用 StateFlow（线程安全，可从任意线程更新）
    private val _lunarDate = MutableStateFlow(CHNDate())
    val lunarDate: StateFlow<CHNDate> = _lunarDate.asStateFlow()

    private val _poem = MutableStateFlow<PoemResponse?>(null)
    val poem: StateFlow<PoemResponse?> = _poem.asStateFlow()

    private var poemRepository: PoemRepository? = null
    private var fetchPoemJob: Job? = null

    private fun getPoemRepository(context: Context): PoemRepository {
        return poemRepository ?: PoemRepository(context.applicationContext).also {
            poemRepository = it
        }
    }

    fun fetchPoem(context: Context) {
        // 取消上一次未完成的请求，避免快速连点导致并发请求
        fetchPoemJob?.cancel()
        fetchPoemJob = viewModelScope.launch {
            val repo = getPoemRepository(context)
            val result = repo.fetchPoem()
            if (result != null) {
                _poem.value = result
            }
        }
    }

    // 内部可变的 StateFlow，用于保存请求结果
    private val _lunarData = MutableStateFlow<LunarDateResponse?>(null)
    // 暴露给 UI 层的不可变 StateFlow
    val lunarData: StateFlow<LunarDateResponse?> = _lunarData.asStateFlow()

    /**
     * 获取今天的农历信息
     * 优化：优先从数据库缓存读取，实现"秒开"，然后再从网络拉取最新数据刷新并覆盖缓存。
     */
    fun getTodayLunarInfo(context: Context) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val dateString = today.format(Constants.DATE_FORMATTER)

            val db = AppDataBase.getInstance(context)

            // 1. 优先读取数据库缓存，让 UI 秒级渲染
            val cached = withContext(Dispatchers.IO) {
                db.lunarDateDao().getByDate(dateString)
            }
            if (cached != null) {
                _lunarData.value = cached.toResponse()
            }

            // 2. 异步发起网络请求获取最新数据 (在 IO 线程)
            val result = withContext(Dispatchers.IO) {
                HkoRepository.fetchLunarDate(dateString)
            }

            // 3. 网络结果如果成功，则更新 UI，同时刷新本地数据库
            if (result != null) {
                _lunarData.value = result
                withContext(Dispatchers.IO) {
                    db.lunarDateDao().insertOrReplace(
                        LunarDateEntity.fromResponse(dateString, result)
                    )
                }
            }
        }
    }


    fun getLunarDate(
        applicationContext : Context,
        after: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val repository = ChineseCalenderRepository()
            val calendar = Calendar.getInstance()
            val result =  repository.getLunarDate(
                currentYear = calendar.get(Calendar.YEAR).toString(),
                currentMonth = (calendar.get(Calendar.MONTH) + 1).toString(),
                currentDay = calendar.get(Calendar.DAY_OF_MONTH).toString()
            )
            val db = AppDataBase.getInstance(applicationContext)

            db.chnDateDao().insertDate(CHNDateEntity.convert(result))

            // 使用 StateFlow 更新，线程安全
            _lunarDate.value = result
            after()
        }
    }
}