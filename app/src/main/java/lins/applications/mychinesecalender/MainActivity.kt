package lins.applications.mychinesecalender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lins.applications.appwidget.MyAppWidget
import lins.applications.appwidget.MyAppWidgetReceiver
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

        viewModel.getLunarDate(this.applicationContext)

        lifecycleScope.launch {
//            GlanceAppWidgetManager(this@MainActivity)
//                .requestPinGlanceAppWidget(
//                    receiver = MyAppWidgetReceiver::class.java,
//                    preview = MyAppWidget(),
//                    successCallback = null
//                )

            val manager = GlanceAppWidgetManager(context = this@MainActivity)
            val widget = MyAppWidget()
            val glanceIds =  manager.getGlanceIds(widget::class.java)
            glanceIds.forEach {
                widget.update(
                    this@MainActivity,
                    it
                )
            }
        }

    }

    override fun onResume() {
        super.onResume()
        finish()
    }
}