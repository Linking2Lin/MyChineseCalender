package lins.applications.mychinesecalender

import android.os.Bundle
import android.util.Log
import android.util.TimeUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lins.applications.appwidget.data.ChineseCalenderRepository
import lins.applications.mychinesecalender.ui.theme.MyChineseCalenderTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyChineseCalenderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = ChineseCalenderRepository()
            val calendar = Calendar.getInstance()
            val data = repository.getLunarDate(
                currentYear = calendar.get(Calendar.YEAR).toString(),
                currentMonth = (calendar.get(Calendar.MONTH) + 1).toString(),
                currentDay = calendar.get(Calendar.DAY_OF_MONTH).toString()
            )
            Log.d("lin", data.toString())
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyChineseCalenderTheme {
        Greeting("Android")
    }
}