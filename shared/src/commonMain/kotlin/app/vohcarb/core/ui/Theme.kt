package app.vohcarb.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** The font words and definitions are set in (see [WordFont]); everything else uses the system font. */
val LocalWordFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }

/** Body text for definitions and examples: the word font, a little larger than the default. */
val DefinitionStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = TextStyle(fontFamily = LocalWordFont.current, fontSize = 20.sp, lineHeight = 30.sp)

private fun typography(word: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = word, fontWeight = FontWeight.Medium),
        displayMedium = base.displayMedium.copy(fontFamily = word, fontWeight = FontWeight.Medium),
        displaySmall = base.displaySmall.copy(fontFamily = word),
        headlineLarge = base.headlineLarge.copy(fontFamily = word),
        headlineMedium = base.headlineMedium.copy(fontFamily = word),
        headlineSmall = base.headlineSmall.copy(fontFamily = word),
        titleLarge = base.titleLarge.copy(fontFamily = word),
    )
}

@Composable
fun VohcarbTheme(look: Look, font: WordFont, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWordFont provides font.family) {
        MaterialTheme(colorScheme = look.palette.colorScheme(), typography = typography(font.family), content = content)
    }
}

/** The theme's background, behind every screen (their own backgrounds are see-through). */
@Composable
fun Backdrop(look: Look, modifier: Modifier = Modifier) {
    val paper = look.palette.paper
    when (val backdrop = look.backdrop) {
        Backdrop.Plain -> Box(modifier.fillMaxSize().background(paper))
        is Backdrop.Gradient -> Box(modifier.fillMaxSize().background(Brush.verticalGradient(backdrop.colors)))
        Backdrop.Grain -> Canvas(modifier.fillMaxSize().background(paper)) {
            val random = Random(7)
            val speck = look.palette.ink.copy(alpha = 0.035f)
            repeat((size.width * size.height / 900).toInt().coerceAtMost(4_000)) {
                drawCircle(speck, radius = 1.2f, center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
            }
        }
        Backdrop.Lines -> Canvas(modifier.fillMaxSize().background(paper)) {
            val line = look.palette.ink.copy(alpha = 0.07f)
            val gap = 36.dp.toPx()
            var y = gap * 2
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += gap
            }
            val margin = 28.dp.toPx()
            drawLine(look.palette.highlight.copy(alpha = 0.18f), Offset(margin, 0f), Offset(margin, size.height), strokeWidth = 1.dp.toPx())
        }
        is Backdrop.Stars -> Canvas(modifier.fillMaxSize().background(Brush.verticalGradient(backdrop.colors))) {
            val random = Random(11)
            repeat(140) {
                val alpha = 0.15f + random.nextFloat() * 0.4f // faint enough not to fight with text
                drawCircle(
                    Color.White.copy(alpha = alpha),
                    radius = (0.6f + random.nextFloat() * 1.6f).dp.toPx(),
                    center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
                )
            }
        }
        is Backdrop.Photo -> {
            val image by produceState<ImageBitmap?>(null, backdrop.path) { value = loadImage(backdrop.path) }
            Box(modifier.fillMaxSize().background(paper)) {
                image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = backdrop.dim)))
            }
        }
    }
}
