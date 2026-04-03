package lins.applications.mychinesecalender

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.woker.SyncDateWorker
import lins.applications.mychinesecalender.ui.content.MainContent
import lins.applications.mychinesecalender.ui.theme.MyChineseCalenderTheme

class MainActivity : ComponentActivity() {
    private val viewModel by lazy { MainViewModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyChineseCalenderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        viewModel.getLunarDate(this.applicationContext) {
            lifecycleScope.launch {
                //update()
            }
        }

        // ── WorkManager 兜底：每 6 小时同步一次 ──
        val updateDateRequest = PeriodicWorkRequestBuilder<SyncDateWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = java.util.concurrent.TimeUnit.HOURS,
        ).build()

        // 使用 enqueueUniquePeriodicWork 避免重复注册
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync_lunar_date",
            ExistingPeriodicWorkPolicy.KEEP,
            updateDateRequest
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lunarData.collect { lunarInfo ->
                    if (lunarInfo != null) {
                        Log.d(TAG, "今天是：${lunarInfo.lunarYear} ${lunarInfo.lunarDate}")
                        update()
                    }
                }
            }
        }

        // 修改：传入 ApplicationContext，以支持 ViewModel 优先加载缓存
        viewModel.getTodayLunarInfo(this.applicationContext)
    }

    private suspend fun update() {
        val manager = GlanceAppWidgetManager(context = this@MainActivity)
        val widget = MyAppWidget()
        val glanceIds = manager.getGlanceIds(widget::class.java)
        glanceIds.forEach {
            widget.update(
                this@MainActivity,
                it
            )
        }
    }

    override fun onResume() {
        super.onResume()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}