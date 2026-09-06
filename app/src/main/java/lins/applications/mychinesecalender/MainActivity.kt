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
import lins.applications.appwidget.helper.WidgetScheduler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import lins.applications.mychinesecalender.ui.content.MainContent
import lins.applications.mychinesecalender.ui.theme.MyChineseCalendarTheme

/**
 * 单页面应用入口，只负责装配 ViewModel、Compose 和前台生命周期。
 * 数据/错误策略放在 ViewModel 与仓库，避免旋转或重新创建 Activity 时重复建立数据流程。
 */
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

        // configure 必须在触发加载之前完成；界面初始化本身只订阅状态，不发请求。
        viewModel.configure(applicationContext)
        viewModel.ensurePoem()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 每次前台都尝试排队组件同步；没有桌面实例时 Scheduler 会直接跳过。
                WidgetScheduler.requestSync(applicationContext)
                // STOPPED 时停止日期轮询；下次 STARTED 立即重新读取今天。
                viewModel.watchDates()
            }
        }
    }
}
