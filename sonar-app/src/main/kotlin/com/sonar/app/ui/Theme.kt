package com.sonar.app.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.CubicBezierEasing
import com.sonar.app.R
import androidx.compose.ui.unit.sp

val SonarBackground = Color(0xFF121418)
val SonarSurface = Color(0xFF1E1D22)
val SonarElevated = Color(0xFF26252A)
val SonarOutline = Color(0xFF2F2D34)
val SonarGreen = Color(0xFF3CE068)
val SonarPink = Color(0xFFFF7B93)
val SonarCyan = Color(0xFF68D9F5)
val SonarText = Color(0xFFF8F8F7)
val SonarMuted = Color(0xFFA7A5AD)
val SonarControlSurface = Color(0xFFD9DCE1)
val SonarControlContent = Color(0xFF1E2228)

val SpringBounce = CubicBezierEasing(.34f, 1.56f, .64f, 1f)
val ExpressiveEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)

private val SonarColors = darkColorScheme(
    primary = SonarGreen,
    onPrimary = Color(0xFF07120A),
    secondary = SonarPink,
    tertiary = SonarCyan,
    background = SonarBackground,
    onBackground = SonarText,
    surface = SonarSurface,
    onSurface = SonarText,
    surfaceVariant = SonarElevated,
    onSurfaceVariant = SonarMuted,
    outline = SonarOutline,
)

val SonarLogoFont = FontFamily(Font(R.font.disco, FontWeight.Normal))

val PlayerBodyFont = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
    Font(R.font.plus_jakarta_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.plus_jakarta_sans_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.plus_jakarta_sans_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
)

val RubikFont = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_semibold, FontWeight.SemiBold),
    Font(R.font.rubik_bold, FontWeight.Bold),
    Font(R.font.rubik_extrabold, FontWeight.ExtraBold),
    Font(R.font.rubik_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.rubik_bolditalic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.rubik_extrabolditalic, FontWeight.ExtraBold, FontStyle.Italic),
)

val PlayerDisplayFont = FontFamily(
    Font(R.font.syne_regular, FontWeight.Normal),
    Font(R.font.syne_medium, FontWeight.Medium),
    Font(R.font.syne_semibold, FontWeight.SemiBold),
    Font(R.font.syne_bold, FontWeight.Bold),
    Font(R.font.syne_extrabold, FontWeight.ExtraBold),
)

private val SonarTypography = Typography(
    displayLarge = TextStyle(fontFamily = SonarLogoFont, fontSize = 32.sp, letterSpacing = 4.sp),
)

@Composable
fun SonarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SonarColors,
        typography = SonarTypography,
        content = content,
    )
}
