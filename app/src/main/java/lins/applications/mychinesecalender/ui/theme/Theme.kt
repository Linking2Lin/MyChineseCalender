package lins.applications.mychinesecalender.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 关闭 dynamicColor 时使用固定色板；两个色板分别适用于深色和浅色背景。
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    // 未显式指定的语义色使用 Material3 默认值；扩展配色应成对核对背景与对应 on* 前景色。
)

/**
 * 页面主题默认跟随系统深浅色与 Android 12+ 壁纸动态色；可关闭动态色检查固定色板。
 * 此处只作用于 Compose 页面，小组件由自己的 GlanceTheme 提供颜色。
 * @param darkTheme 是否使用深色主题，默认跟随系统设置。
 * @param dynamicColor 是否使用 Android 动态配色，默认行为保持不变。
 * @param content 使用本主题渲染的 Compose 内容回调。
 * @return Unit；在选定的 MaterialTheme 色板和 Typography 下组合 content。
 */
@Composable
fun MyChineseCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 项目最低支持 Android 12，仍保留平台判断以清晰表达动态色 API 的使用边界。
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 动态色优先；只有关闭动态色或平台不支持时，才选用本项目固定色板。
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
