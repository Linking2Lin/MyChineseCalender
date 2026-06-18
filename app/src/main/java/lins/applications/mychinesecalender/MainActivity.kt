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
import lins.applications.mychinesecalender.ui.theme.MyChineseCalenderTheme

class MainActivity : ComponentActivity() {
    // 通过 viewModels() 委托创建，配置变更时自动保留
    private val viewModel: MainViewModel by viewModels()

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
        viewModel.fetchPoem(this.applicationContext)
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

    companion object {
        private const val TAG = "MainActivity"
    }
}