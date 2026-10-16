package com.giwnltrema.chuva.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.giwnltrema.chuva.ui.theme.RainScale
import kotlin.math.floor
import kotlin.math.min

/**
 * Converte uma grade de mm/h numa imagem suave (interpolação bilinear),
 * com borda esmaecida para não parecer um quadrado colado no mapa.
 */
fun renderRainGrid(values: Array<DoubleArray>, px: Int = 192): Bitmap {
    val rows = values.size
    val cols = values[0].size
    val pixels = IntArray(px * px)
    val fadeBand = px * 0.12

    for (y in 0 until px) {
        val gy = y.toDouble() / (px - 1) * (rows - 1)
        val r0 = min(floor(gy).toInt(), rows - 2)
        val fy = gy - r0
        for (x in 0 until px) {
            val gx = x.toDouble() / (px - 1) * (cols - 1)
            val c0 = min(floor(gx).toInt(), cols - 2)
            val fx = gx - c0
            val v = values[r0][c0] * (1 - fx) * (1 - fy) +
                values[r0][c0 + 1] * fx * (1 - fy) +
                values[r0 + 1][c0] * (1 - fx) * fy +
                values[r0 + 1][c0 + 1] * fx * fy

            val presence = smoothstep(0.05, 0.25, v)
            if (presence <= 0.0) continue
            val strength = 0.55 + 0.4 * min(1.0, v / 5.0)
            val edge = min(min(x, y), min(px - 1 - x, px - 1 - y)) / fadeBand
            val alpha = (presence * strength * min(1.0, edge)).toFloat()
            pixels[y * px + x] = RainScale.colorFor(v).copy(alpha = alpha).toArgb()
        }
    }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}

private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}
