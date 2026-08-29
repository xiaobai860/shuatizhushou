package com.example.shuati.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 品牌色板：柔和渐变 · 亲和活力（Softlight Desk / 柔光书桌）。
 *
 * 三条硬规则：
 * 1. 中性色恒为暖米灰，不参与 Material You 动态取色；
 * 2. 语义色（正确 / 警告 / 错误 / 信息）永不取色——壁纸若为红色，会与「答错」语义冲突；
 * 3. 动态取色只放行 primary / secondary / tertiary 及其 container。
 */
object BrandColors {

    // ---------- 中性色（暖米灰）----------
    val NeutralBg = Color(0xFFFBF7F3)
    val NeutralBgDark = Color(0xFF141210)
    val NeutralSurface = Color(0xFFFFFFFF)
    val NeutralSurfaceDark = Color(0xFF1E1A17)
    val NeutralOnSurface = Color(0xFF241E19)
    val NeutralOnSurfaceDark = Color(0xFFF3ECE4)

    // ---------- 角色色（可被动态取色覆盖）----------
    // Primary 陶土橙：选暖橙而非紫，是为了与语义的红 / 绿拉开距离
    val Primary = Color(0xFFC8551E)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFFFE1CF)
    val OnPrimaryContainer = Color(0xFF5A1F06)

    val Secondary = Color(0xFF6F5FA8)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFEDE8FB)
    val OnSecondaryContainer = Color(0xFF2C1E5C)

    val Tertiary = Color(0xFF3C8A75)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFD3F0E7)
    val OnTertiaryContainer = Color(0xFF05372C)

    // 角色色 · 深色
    val PrimaryDark = Color(0xFFFFB490)
    val OnPrimaryDark = Color(0xFF4A1A05)
    val PrimaryContainerDark = Color(0xFF5C2A12)
    val OnPrimaryContainerDark = Color(0xFFFFDDCB)

    val SecondaryDark = Color(0xFFCFC2FF)
    val OnSecondaryDark = Color(0xFF33225E)
    val SecondaryContainerDark = Color(0xFF3B2C6B)
    val OnSecondaryContainerDark = Color(0xFFE6DEFF)

    val TertiaryDark = Color(0xFF9ADCC6)
    val OnTertiaryDark = Color(0xFF06382C)
    val TertiaryContainerDark = Color(0xFF14453A)
    val OnTertiaryContainerDark = Color(0xFFCBF0E2)

    // ---------- 语义色（永不参与动态取色）----------
    val Success = Color(0xFF2E8B62)
    val SuccessContainer = Color(0xFFD9F0E3)
    val OnSuccessContainer = Color(0xFF063D28)

    val Warning = Color(0xFFA96C10)
    val WarningContainer = Color(0xFFFCEBCF)
    val OnWarningContainer = Color(0xFF42280A)

    val Error = Color(0xFFC2403A)
    val ErrorContainer = Color(0xFFFBE1DE)
    val OnErrorContainer = Color(0xFF5A1310)

    val Info = Color(0xFF3D74B8)
    val InfoContainer = Color(0xFFDCE8FA)
    val OnInfoContainer = Color(0xFF0E2A50)

    // 语义色 · 深色
    val SuccessDark = Color(0xFF7DCEA6)
    val SuccessContainerDark = Color(0xFF173F31)
    val OnSuccessContainerDark = Color(0xFFBDEBD4)

    val WarningDark = Color(0xFFE5B769)
    val WarningContainerDark = Color(0xFF463314)
    val OnWarningContainerDark = Color(0xFFF7DDAE)

    val ErrorDark = Color(0xFFF29189)
    val ErrorContainerDark = Color(0xFF4E1F1C)
    val OnErrorContainerDark = Color(0xFFFCC9C4)

    val InfoDark = Color(0xFFA3C6F5)
    val InfoContainerDark = Color(0xFF1E3557)
    val OnInfoContainerDark = Color(0xFFC9DDFA)
}

// ---------------------------------------------------------------------------
// 配色方案
// ---------------------------------------------------------------------------

private val LightColorScheme = lightColorScheme(
    primary = BrandColors.Primary,
    onPrimary = BrandColors.OnPrimary,
    primaryContainer = BrandColors.PrimaryContainer,
    onPrimaryContainer = BrandColors.OnPrimaryContainer,
    secondary = BrandColors.Secondary,
    onSecondary = BrandColors.OnSecondary,
    secondaryContainer = BrandColors.SecondaryContainer,
    onSecondaryContainer = BrandColors.OnSecondaryContainer,
    tertiary = BrandColors.Tertiary,
    onTertiary = BrandColors.OnTertiary,
    tertiaryContainer = BrandColors.TertiaryContainer,
    onTertiaryContainer = BrandColors.OnTertiaryContainer,
    background = BrandColors.NeutralBg,
    onBackground = BrandColors.NeutralOnSurface,
    surface = BrandColors.NeutralSurface,
    onSurface = BrandColors.NeutralOnSurface,
    surfaceVariant = Color(0xFFF6F0EA),
    onSurfaceVariant = Color(0xFF5E544B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF9F5),
    surfaceContainer = Color(0xFFF6F0EA),
    surfaceContainerHigh = Color(0xFFEFE7DE),
    surfaceContainerHighest = Color(0xFFE6DCD1),
    outline = Color(0xFFE2D8CC),
    outlineVariant = Color(0xFFBCAE9F)
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandColors.PrimaryDark,
    onPrimary = BrandColors.OnPrimaryDark,
    primaryContainer = BrandColors.PrimaryContainerDark,
    onPrimaryContainer = BrandColors.OnPrimaryContainerDark,
    secondary = BrandColors.SecondaryDark,
    onSecondary = BrandColors.OnSecondaryDark,
    secondaryContainer = BrandColors.SecondaryContainerDark,
    onSecondaryContainer = BrandColors.OnSecondaryContainerDark,
    tertiary = BrandColors.TertiaryDark,
    onTertiary = BrandColors.OnTertiaryDark,
    tertiaryContainer = BrandColors.TertiaryContainerDark,
    onTertiaryContainer = BrandColors.OnTertiaryContainerDark,
    background = BrandColors.NeutralBgDark,
    onBackground = BrandColors.NeutralOnSurfaceDark,
    surface = BrandColors.NeutralSurfaceDark,
    onSurface = BrandColors.NeutralOnSurfaceDark,
    surfaceVariant = Color(0xFF2A2621),
    onSurfaceVariant = Color(0xFFBEB4A9),
    surfaceContainerLowest = Color(0xFF0F0D0B),
    surfaceContainerLow = Color(0xFF232019),
    surfaceContainer = Color(0xFF2A2621),
    surfaceContainerHigh = Color(0xFF322D27),
    surfaceContainerHighest = Color(0xFF3B352E),
    outline = Color(0xFF39332C),
    outlineVariant = Color(0xFF554D44)
)

// ---------------------------------------------------------------------------
// 语义色：Material 3 的 ColorScheme 无对应字段，用 CompositionLocal 扩展
// ---------------------------------------------------------------------------

data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

private val LightSemantic = SemanticColors(
    success = BrandColors.Success,
    onSuccess = Color.White,
    successContainer = BrandColors.SuccessContainer,
    onSuccessContainer = BrandColors.OnSuccessContainer,
    warning = BrandColors.Warning,
    onWarning = Color.White,
    warningContainer = BrandColors.WarningContainer,
    onWarningContainer = BrandColors.OnWarningContainer,
    error = BrandColors.Error,
    onError = Color.White,
    errorContainer = BrandColors.ErrorContainer,
    onErrorContainer = BrandColors.OnErrorContainer,
    info = BrandColors.Info,
    onInfo = Color.White,
    infoContainer = BrandColors.InfoContainer,
    onInfoContainer = BrandColors.OnInfoContainer
)

private val DarkSemantic = SemanticColors(
    success = BrandColors.SuccessDark,
    onSuccess = Color(0xFF063D28),
    successContainer = BrandColors.SuccessContainerDark,
    onSuccessContainer = BrandColors.OnSuccessContainerDark,
    warning = BrandColors.WarningDark,
    onWarning = Color(0xFF42280A),
    warningContainer = BrandColors.WarningContainerDark,
    onWarningContainer = BrandColors.OnWarningContainerDark,
    error = BrandColors.ErrorDark,
    onError = Color(0xFF5A1310),
    errorContainer = BrandColors.ErrorContainerDark,
    onErrorContainer = BrandColors.OnErrorContainerDark,
    info = BrandColors.InfoDark,
    onInfo = Color(0xFF0E2A50),
    infoContainer = BrandColors.InfoContainerDark,
    onInfoContainer = BrandColors.OnInfoContainerDark
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

/** 页面内访问：MaterialTheme.semantic.success */
val MaterialTheme.semantic: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current

// ---------------------------------------------------------------------------
// 主题入口
// ---------------------------------------------------------------------------

@Composable
fun ShuatiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You 默认开启；动态取色只放行角色色，中性色与语义色恒为品牌值
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dyn = if (darkTheme) dynamicDarkColorScheme(context)
                      else dynamicLightColorScheme(context)
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            base.copy(
                primary = dyn.primary,
                onPrimary = dyn.onPrimary,
                primaryContainer = dyn.primaryContainer,
                onPrimaryContainer = dyn.onPrimaryContainer,
                secondary = dyn.secondary,
                onSecondary = dyn.onSecondary,
                secondaryContainer = dyn.secondaryContainer,
                onSecondaryContainer = dyn.onSecondaryContainer,
                tertiary = dyn.tertiary,
                onTertiary = dyn.onTertiary,
                tertiaryContainer = dyn.tertiaryContainer,
                onTertiaryContainer = dyn.onTertiaryContainer
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalSemanticColors provides if (darkTheme) DarkSemantic else LightSemantic
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ShuatiTypography,
            shapes = ShuatiShapes,
            content = content
        )
    }
}
