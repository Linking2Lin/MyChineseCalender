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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import lins.applications.mychinesecalender.MainViewModel
import lins.applications.mychinesecalender.R
import lins.applications.mychinesecalender.ui.theme.MyChineseCalendarTheme
import lins.applications.mychinesecalender.util.WidgetImageManager
import lins.libs.module_base.data.DateLoadResult
import lins.libs.module_base.model.CHNDate
import lins.libs.module_poem.model.PoemResponse

/**
 * 有状态页面入口：随生命周期订阅 ViewModel，处理图片选择器，将数据与事件交给纯展示组件。
 * 不在重组期间直接刷新网络/数据库，避免频繁重组产生重复副作用。
 */
@Composable
fun MainContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // 页面活跃时收集数据，停止时暂停 UI 收集；后台数据加载由 ViewModel/仓库自行管理。
    val calendar by viewModel.calendar.collectAsStateWithLifecycle()
    val poem by viewModel.poem.collectAsStateWithLifecycle()
    val isPoemLoading by viewModel.isPoemLoading.collectAsStateWithLifecycle()
    val poemError by viewModel.poemError.collectAsStateWithLifecycle()
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
            data = calendar.data ?: CHNDate(),
            calendarState = calendar,
            onRefreshCalendar = { viewModel.refreshCalendar(force = true) },
            poemError = poemError,
            poem = poem,
            isPoemLoading = isPoemLoading,
            onRefreshPoem = { viewModel.fetchPoem() }
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
            Icon(painter = painterResource(R.drawable.ic_image), contentDescription = "自定义组件图片")
        }
    }
}

/**
 * 可预览的纯展示层。正式入口同时传入 data 与包含同一数据的 calendarState；默认参数只方便预览。
 * 维护时以 calendarState 判断加载/错误/可用性，不要用空 CHNDate() 表示一次成功加载。
 */
@Composable
fun MainContentStateless(
    data: CHNDate,
    poem: PoemResponse?,
    isPoemLoading: Boolean,
    onRefreshPoem: () -> Unit,
    modifier: Modifier = Modifier,
    calendarState: DateLoadResult<CHNDate> = DateLoadResult(LocalDate.now(), data),
    onRefreshCalendar: () -> Unit = {},
    poemError: Boolean = false,
) {
    // 这是页面的“纯展示层”：只接收数据和事件，不持有额外业务状态。
    // 这样未来如果你想做页面重构，只需要替换 UI 结构，不需要动数据层。
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 状态提示与数据分离：刷新失败仍展示同日缓存，保存失败也不隐藏有效网络数据。
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val message = when {
                calendarState.loading -> "${calendarState.date} · 正在加载…"
                calendarState.error != null && calendarState.data != null -> "更新失败，显示今日缓存"
                calendarState.error != null -> "今日黄历加载失败，请重试"
                calendarState.cacheError != null -> "今日数据已加载，但缓存保存失败"
                calendarState.data == null -> "${calendarState.date} · 暂无今日数据"
                else -> calendarState.date.toString()
            }
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRefreshCalendar, enabled = !calendarState.loading) { Text("刷新黄历") }
        }
        if (calendarState.data != null) MainDateCard(data = data)
        PoemCard(poem = poem, isLoading = isPoemLoading, onRefresh = onRefreshPoem)
        if (poemError) Text("诗词更新失败，点击诗词卡片重试", style = MaterialTheme.typography.bodyMedium)
        if (calendarState.data != null) {
            DetailInfoCard(data = data)
            YiJiSection(yi = data.yi, ji = data.ji)
        }
    }
}

/** 公历/农历主卡片；农历展示字符串有空格时拆为两行，不依赖该拆分进行日期身份判断。 */
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

/** 补充黄历字段；接口允许这些字段缺省，缺省行由 InfoRow 隐藏。 */
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

/** 标签固定宽度，值占剩余空间；null/空字符串不渲染该行。 */
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

/** 宜忌各占一半宽度，使用共同内在高度让左右卡片底部对齐。 */
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

/** 缺失资料显示“暂无资料”，不能写成“无”，以免把接口缺字段解释成当天没有宜忌事项。 */
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
                text = content?.takeIf { it.isNotBlank() } ?: "暂无资料",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f
            )
        }
    }
}

/**
 * 整张卡片可点击换诗；加载期间禁用重复点击。错误由外层独立展示，已有诗词无需清空。
 * 接口出处允许缺省，有正文时仍可以显示；展示规则不应反过来决定缓存或 Token 行为。
 */
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

/** 固定样例数据的日/夜主题预览，不创建 ViewModel，也不触发真实接口。 */
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
                    Icon(painter = painterResource(R.drawable.ic_image), contentDescription = "自定义组件图片")
                }
            }
        }
    }
}
