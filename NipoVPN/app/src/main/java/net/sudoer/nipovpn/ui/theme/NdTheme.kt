package net.sudoer.nipo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import net.sudoer.nipo.R

/**
 * Nothing Design System — token set, mirrored from the design bundle's
 * `nd-core.jsx` (ND_DARK / ND_LIGHT). OLED-black instrument-panel aesthetic:
 * flat surfaces, hairline borders, red as the single signal accent.
 */
@Immutable
data class NdColors(
    val black: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderVisible: Color,
    val disabled: Color,
    val secondary: Color,
    val primary: Color,
    val display: Color,
    val accent: Color,
    val accentSubtle: Color,
    val success: Color,
    val warning: Color,
    val interactive: Color,
    val emptySeg: Color,
    val isLight: Boolean,
)

val NdDark = NdColors(
    black = Color(0xFF000000),
    surface = Color(0xFF111111),
    surfaceRaised = Color(0xFF1A1A1A),
    border = Color(0xFF222222),
    borderVisible = Color(0xFF333333),
    disabled = Color(0xFF666666),
    secondary = Color(0xFF999999),
    primary = Color(0xFFE8E8E8),
    display = Color(0xFFFFFFFF),
    accent = Color(0xFFD71921),
    accentSubtle = Color(0x26D71921), // rgba(215,25,33,0.15)
    success = Color(0xFF4A9E5C),
    warning = Color(0xFFD4A843),
    interactive = Color(0xFF5B9BF6),
    emptySeg = Color(0xFF222222),
    isLight = false,
)

val NdLight = NdColors(
    black = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF0F0F0),
    border = Color(0xFFE8E8E8),
    borderVisible = Color(0xFFCCCCCC),
    disabled = Color(0xFF999999),
    secondary = Color(0xFF666666),
    primary = Color(0xFF1A1A1A),
    display = Color(0xFF000000),
    accent = Color(0xFFD71921),
    accentSubtle = Color(0x1FD71921), // rgba(215,25,33,0.12)
    success = Color(0xFF3E8B4F),
    warning = Color(0xFFB98F2E),
    interactive = Color(0xFF007AFF),
    emptySeg = Color(0xFFE0E0E0),
    isLight = true,
)

/** The three typefaces, each doing a distinct job (per the design). */
object NothingFonts {
    // Doto — dot-matrix display face, used for the hero timer.
    val Display = FontFamily(
        Font(R.font.doto_regular, FontWeight.Normal),
        Font(R.font.doto_medium, FontWeight.Medium),
        Font(R.font.doto_semibold, FontWeight.SemiBold),
        Font(R.font.doto_bold, FontWeight.Bold),
    )

    // Space Grotesk — UI body text.
    val Body = FontFamily(
        Font(R.font.space_grotesk_light, FontWeight.Light),
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        Font(R.font.space_grotesk_bold, FontWeight.Bold),
    )

    // Space Mono — ALL-CAPS instrument labels and data values.
    val Mono = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )
}

val LocalNdColors = staticCompositionLocalOf { NdDark }

/** Convenience accessor: `NdTheme.colors` inside a composable. */
object NdTheme {
    val colors: NdColors
        @Composable get() = LocalNdColors.current
}

@Composable
fun NothingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val nd = if (darkTheme) NdDark else NdLight

    // Back the Material color scheme with Nothing tokens so Surface / any
    // Material3 surfaces (dialog scrims, etc.) sit on the right background.
    val scheme = if (darkTheme) {
        darkColorScheme(
            background = nd.black, surface = nd.surface,
            onBackground = nd.primary, onSurface = nd.primary,
            primary = nd.display, onPrimary = nd.black,
        )
    } else {
        lightColorScheme(
            background = nd.black, surface = nd.surface,
            onBackground = nd.primary, onSurface = nd.primary,
            primary = nd.display, onPrimary = nd.surface,
        )
    }

    CompositionLocalProvider(LocalNdColors provides nd) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
