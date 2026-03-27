package lins.applications.mychinesecalender

import android.nfc.Tag
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
import androidx.lifecycle.lifecycleScope
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.delay
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

        viewModel.getLunarDate(this.applicationContext){
            lifecycleScope.launch {
                //update()
            }
        }

        val updateDateRequest = PeriodicWorkRequestBuilder<SyncDateWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = java.util.concurrent.TimeUnit.HOURS,
//            flexTimeInterval = 1,
//            flexTimeIntervalUnit = java.util.concurrent.TimeUnit.HOURS,
        )
            .setInitialDelay(1, java.util.concurrent.TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueue(updateDateRequest)

        lifecycleScope.launch {
            viewModel.lunarData.collect { lunarInfo ->
                if (lunarInfo != null) {
                    Log.d(TAG,"今天是：${lunarInfo.lunarYear} ${lunarInfo.lunarDate}")
                    update()
                }
            }
        }

        viewModel.getTodayLunarInfo()
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
//        lifecycleScope.launch {
//            delay(1000)
//            finish()
//        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}