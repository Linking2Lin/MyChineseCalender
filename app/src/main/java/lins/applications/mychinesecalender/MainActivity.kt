package lins.applications.mychinesecalender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lins.applications.appwidget.helper.WidgetDataSyncHelper
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

        // 启动时做一次统一同步，保证页面和 widget 使用同一份有效缓存。
        lifecycleScope.launch {
            WidgetDataSyncHelper.syncAndUpdate(applicationContext)
        }
    }
}
