package lins.applications.mychinesecalender.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 统一页面正文的字体与行距，单位 sp 会响应系统字体缩放；未覆盖样式使用 Material3 默认值。
// 小组件沿用 MyAppWidget 中独立的字号公式，不受此 Typography 配置影响。
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    // 如需覆盖标题/按钮样式，在此增加对应参数；同时检查大字体下的换行和卡片高度。
)
