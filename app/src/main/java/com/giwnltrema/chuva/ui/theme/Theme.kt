package com.giwnltrema.chuva.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

private val Light = lightColorScheme(
    primary = Color(0xFF1E5BD8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF001849),
    secondary = Color(0xFF00838F),
    secondaryContainer = Color(0xFFCDF2F6),
    tertiary = Color(0xFF6750A4),
    background = Color(0xFFF7F9FE),
    surface = Color(0xFFF7F9FE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB3C5FF),
    onPrimary = Color(0xFF002B75),
    primaryContainer = Color(0xFF0041A4),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF7FD6E0),
    secondaryContainer = Color(0xFF004F57),
    tertiary = Color(0xFFCFBCFF),
    background = Color(0xFF0F1320),
    surface = Color(0xFF0F1320),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Light),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun ChuvaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}

/** Escala de cores de chuva (mm/h), usada no gráfico, na legenda e no mapa de previsão. */
object RainScale {
    val stops: List<Pair<Double, Color>> = listOf(
        0.1 to Color(0xFF9BE7FF),
        0.5 to Color(0xFF4FC3F7),
        1.0 to Color(0xFF2196F3),
        2.5 to Color(0xFF00C853),
        5.0 to Color(0xFFFFEB3B),
        10.0 to Color(0xFFFF9800),
        20.0 to Color(0xFFF44336),
        40.0 to Color(0xFFD500F9),
    )

    fun colorFor(mmPerHour: Double): Color {
        if (mmPerHour <= stops.first().first) return stops.first().second
        for (i in 1 until stops.size) {
            val (v1, c1) = stops[i]
            if (mmPerHour <= v1) {
                val (v0, c0) = stops[i - 1]
                val t = ((mmPerHour - v0) / (v1 - v0)).toFloat()
                return lerp(c0, c1, t)
            }
        }
        return stops.last().second
    }

    private fun lerp(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )
}
