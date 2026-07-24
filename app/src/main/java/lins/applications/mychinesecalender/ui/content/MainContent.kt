package lins.applications.mychinesecalender.ui.content

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lins.libs.module_base.model.CHNDate
import lins.applications.mychinesecalender.MainViewModel
import lins.applications.mychinesecalender.ui.theme.MyChineseCalendarTheme
import lins.applications.mychinesecalender.util.WidgetImageManager
import lins.libs.module_poem.model.PoemResponse

@Composable
fun MainContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // 订阅 ViewModel 暴露的状态；只要数据变了，界面就会自动重组。
    val data by viewModel.lunarDate.collectAsState()
    val poem by viewModel.poem.collectAsState()
    val isPoemLoading by viewModel.isPoemLoading.collectAsState()
    val context = LocalContext.current

    // 通过一个布尔状态控制“是否准备打开系统图片选择器”。
    // 这样可以避免在 Compose 重组时重复 launch 系统界面。
    var shouldLaunchPicker by remember { mutableStateOf(false) }

    // 系统图片选择器：只允许用户选择图片，不允许视频/其他类型。
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            shouldLaunchPicker = false
            if (uri != null) {
                // 用户选中图片后，交给 WidgetImageManager 统一做缩放、裁剪、保存和刷新。
                WidgetImageManager.saveImageToInternalStorage(context, uri)
            }
        }
    )

    // 只有当 shouldLaunchPicker 从 false 变为 true 时，才真正打开系统选择器。
    // Compose 中不建议在组合期间直接做副作用，所以这里用 LaunchedEffect 做桥接。
    LaunchedEffect(shouldLaunchPicker) {
        if (shouldLaunchPicker) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            shouldLaunchPicker = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MainContentStateless(
            data = data,
            poem = poem,
            isPoemLoading = isPoemLoading,
            onRefreshPoem = { viewModel.fetchPoem(context) }
        )

        // 右下角浮动按钮：用于更换 widget 自定义头像。
        FloatingActionButton(
            onClick = {
                shouldLaunchPicker = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Image, contentDescription = "自定义组件图片")
        }
    }
}

@Composable
fun MainContentStateless(
    data: CHNDate,
    poem: PoemResponse?,
    isPoemLoading: Boolean,
    onRefreshPoem: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 这是页面的“纯展示层”：只接收数据和事件，不持有额外业务状态。
    // 这样未来如果你想做页面重构，只需要替换 UI 结构，不需要动数据层。
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MainDateCard(data = data)
        PoemCard(poem = poem, isLoading = isPoemLoading, onRefresh = onRefreshPoem)
        DetailInfoCard(data = data)
        YiJiSection(yi = data.yi, ji = data.ji)
    }
}

@Composable
fun MainDateCard(data: CHNDate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = data.year ?: "未知公历日期",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            val lunarParts = data.lunarDate?.split(" ", limit = 2)
            if (lunarParts != null && lunarParts.size == 2) {
                Text(
                    text = lunarParts[0],
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lunarParts[1],
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = data.lunarDate ?: "未知农历日期",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = data.ganZhiDate ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun DetailInfoCard(data: CHNDate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow(label = "黄历", value = data.huangLiDate)
            InfoRow(label = "五行", value = data.wuXing)
            InfoRow(label = "星神", value = data.zhiRiXingShen)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    if (value.isNullOrEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun YiJiSection(yi: String?, ji: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        YiJiCard(
            modifier = Modifier.weight(1f),
            title = "宜",
            content = yi,
            // 采用 Material3 动态配色：次要色彩容器适合“宜”
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleColor = MaterialTheme.colorScheme.onSecondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
        YiJiCard(
            modifier = Modifier.weight(1f),
            title = "忌",
            content = ji,
            // 采用 Material3 动态配色：错误色彩容器天然适合“忌”的警示色彩
            containerColor = MaterialTheme.colorScheme.errorContainer,
            titleColor = MaterialTheme.colorScheme.onErrorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun YiJiCard(
    modifier: Modifier = Modifier,
    title: String,
    content: String?,
    containerColor: Color,
    titleColor: Color,
    contentColor: Color
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content ?: "无",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f
            )
        }
    }
}

@Composable
fun PoemCard(
    poem: PoemResponse?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onRefresh() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val poemData = poem?.data
            if (isLoading) {
                Text(
                    text = "正在加载诗词…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (poemData != null) {
                Text(
                    text = poemData.content,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                val origin = poemData.origin
                val sourceText = if (origin != null) {
                    "—— [${origin.dynasty}] ${origin.author} 《${origin.title}》"
                } else {
                    ""
                }
                if (sourceText.isNotEmpty()) {
                    Text(
                        text = sourceText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                    )
                }
            } else {
                Text(
                    text = "点击加载今日诗词...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun MainContentPreview() {
    MyChineseCalendarTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                MainContentStateless(
                    data = CHNDate.test,
                    poem = null,
                    isPoemLoading = false,
                    onRefreshPoem = {}
                )
                FloatingActionButton(
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = "自定义组件图片")
                }
            }
        }
    }
}