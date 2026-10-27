package app.vohcarb.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paper and ink: warm off-white, deep blue-grey, one muted accent. Words are set in a big serif, like a dictionary.

private val Light = lightColorScheme(
    primary = Color(0xFF2E3A59),
    onPrimary = Color(0xFFFBF8F3),
    primaryContainer = Color(0xFFDCE1EE),
    onPrimaryContainer = Color(0xFF16203A),
    secondary = Color(0xFFB0563B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DED5),
    onSecondaryContainer = Color(0xFF3D150A),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1D1B18),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1D1B18),
    surfaceVariant = Color(0xFFEDE7DD),
    onSurfaceVariant = Color(0xFF5B564E),
    surfaceContainer = Color(0xFFF3EEE6),
    outline = Color(0xFF8C867C),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB7C3E3),
    onPrimary = Color(0xFF16203A),
    primaryContainer = Color(0xFF34405F),
    onPrimaryContainer = Color(0xFFDCE1EE),
    secondary = Color(0xFFF0A58C),
    onSecondary = Color(0xFF3D150A),
    secondaryContainer = Color(0xFF6E3020),
    onSecondaryContainer = Color(0xFFF6DED5),
    background = Color(0xFF151413),
    onBackground = Color(0xFFEAE4DA),
    surface = Color(0xFF151413),
    onSurface = Color(0xFFEAE4DA),
    surfaceVariant = Color(0xFF2A2825),
    onSurfaceVariant = Color(0xFFC9C2B7),
    surfaceContainer = Color(0xFF201E1C),
    outline = Color(0xFF948E84),
)

private val base = Typography()

private val VohcarbTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
)

/** Body text for definitions and examples: serif, a little larger than the default. */
val DefinitionStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 30.sp)

@Composable
fun VohcarbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = VohcarbTypography,
        content = content,
    )
}
