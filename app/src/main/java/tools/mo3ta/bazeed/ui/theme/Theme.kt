package tools.mo3ta.bazeed.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val BazeedColorScheme = lightColorScheme(
    primary = Green,
    onPrimary = Paper,
    primaryContainer = Mint,
    onPrimaryContainer = GreenDeep,
    secondary = Terracotta,
    onSecondary = Paper,
    secondaryContainer = Rose,
    onSecondaryContainer = Ink,
    tertiary = Saffron,
    onTertiary = Ink,
    tertiaryContainer = SaffronLight,
    onTertiaryContainer = Ink,
    background = Sand,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper2,
    onSurfaceVariant = InkSoft,
    outline = Line,
    outlineVariant = LineSoft
)

@Composable
fun BazeedTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BazeedColorScheme,
        typography = BazeedTypography
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            content = content
        )
    }
}
