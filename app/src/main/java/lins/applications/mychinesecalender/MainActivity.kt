package lins.applications.mychinesecalender

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import lins.applications.appwidget.MyAppWidget
import lins.applications.mychinesecalender.ui.content.MainContent
import lins.applications.mychinesecalender.ui.theme.MyChineseCalendarTheme

class MainActivity : ComponentActivity() {
    // ViewModel 负责承载页面级状态；Activity 仅负责装配 UI 和协调少量副作用。
    // 这里使用 viewModels() 可以让 ViewModel 在配置变更（旋转屏幕、深色模式切换）时保持不被重建。
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用沉浸式边缘布局，让内容可以延伸到系统栏下方，页面更现代。
        enableEdgeToEdge()

        // Compose UI 入口：把数据和事件交给 MainContent，Activity 不直接写业务 UI。
        setContent {
            MyChineseCalendarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        // 启动时先加载“今天的农历”和“今日诗词”。
        // 这里传 applicationContext 是为了避免把 Activity Context 长时间传到后台对象中。
        viewModel.getTodayLunarInfo(applicationContext)
        viewModel.fetchPoem(applicationContext)

        // 监听农历数据变化：只要数据更新，就同步刷新所有 widget。
        // repeatOnLifecycle 会在 STARTED/STOPPED 间自动挂起/恢复，避免页面退到后台后继续收集。
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lunarData.collect { lunarInfo ->
                    if (lunarInfo != null) {
                        Log.d(TAG, "今天是：${lunarInfo.lunarYear} ${lunarInfo.lunarDate}")
                        updateWidgets()
                    }
                }
            }
        }
    }

    // 逐个更新已存在的 widget 实例。
    // 这里不直接依赖某个固定 glanceId，而是通过 manager 查询当前所有实例，保证多副本同步。
    private suspend fun updateWidgets() {
        val manager = GlanceAppWidgetManager(context = this@MainActivity)
        val widget = MyAppWidget()
        manager.getGlanceIds(widget::class.java).forEach { glanceId ->
            widget.update(this@MainActivity, glanceId)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}