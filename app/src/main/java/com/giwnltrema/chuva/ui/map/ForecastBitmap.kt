package com.giwnltrema.chuva.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.giwnltrema.chuva.data.dbzToRate
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

            val a = rainAlpha(v)
            if (a <= 0f) continue
            val edge = min(min(x, y), min(px - 1 - x, px - 1 - y)) / fadeBand
            pixels[y * px + x] = RainScale.colorFor(v).copy(alpha = a * min(1.0, edge).toFloat()).toArgb()
        }
    }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}

/**
 * Pixels de radar (dBZ, ~1 km cada) → imagem suave com a mesma escala de cor da previsão.
 * Converte para mm/h, desfoca de leve (as bordas deixam de ser "quadradinhos")
 * e desenha em [scale]x a resolução com interpolação bilinear.
 */
fun renderRadarGrid(grid: IntArray, size: Int, scale: Int = 2): Bitmap {
    var rate = DoubleArray(grid.size) { k -> if (grid[k] == Int.MIN_VALUE) 0.0 else dbzToRate(grid[k]) }
    repeat(2) { rate = boxBlur(rate, size) }

    val out = size * scale
    val pixels = IntArray(out * out)
    for (y in 0 until out) {
        val gy = ((y + 0.5) / scale - 0.5).coerceIn(0.0, size - 1.0)
        val r0 = min(floor(gy).toInt(), size - 2)
        val fy = gy - r0
        for (x in 0 until out) {
            val gx = ((x + 0.5) / scale - 0.5).coerceIn(0.0, size - 1.0)
            val c0 = min(floor(gx).toInt(), size - 2)
            val fx = gx - c0
            val v = rate[r0 * size + c0] * (1 - fx) * (1 - fy) +
                rate[r0 * size + c0 + 1] * fx * (1 - fy) +
                rate[(r0 + 1) * size + c0] * (1 - fx) * fy +
                rate[(r0 + 1) * size + c0 + 1] * fx * fy
            val a = rainAlpha(v)
            if (a > 0f) pixels[y * out + x] = RainScale.colorFor(v).copy(alpha = a).toArgb()
        }
    }
    return Bitmap.createBitmap(pixels, out, out, Bitmap.Config.ARGB_8888)
}

/** Média 3x3 (separável). Duas passadas ≈ desfoque gaussiano leve. */
private fun boxBlur(src: DoubleArray, size: Int): DoubleArray {
    val tmp = DoubleArray(src.size)
    for (y in 0 until size) for (x in 0 until size) {
        var sum = 0.0
        var n = 0
        for (dx in -1..1) {
            val xx = x + dx
            if (xx in 0 until size) { sum += src[y * size + xx]; n++ }
        }
        tmp[y * size + x] = sum / n
    }
    val out = DoubleArray(src.size)
    for (y in 0 until size) for (x in 0 until size) {
        var sum = 0.0
        var n = 0
        for (dy in -1..1) {
            val yy = y + dy
            if (yy in 0 until size) { sum += tmp[yy * size + x]; n++ }
        }
        out[y * size + x] = sum / n
    }
    return out
}

/**
 * Transparência por intensidade (mm/h): garoa quase some, chuva de verdade fica bem visível.
 * É o que deixa claro "onde vai chover de fato".
 */
fun rainAlpha(mmPerHour: Double): Float = when {
    mmPerHour < 0.2 -> 0f
    mmPerHour < 1.0 -> (0.18 + 0.27 * smoothstep(0.2, 1.0, mmPerHour)).toFloat()
    mmPerHour < 5.0 -> (0.45 + 0.3 * smoothstep(1.0, 5.0, mmPerHour)).toFloat()
    else -> 0.85f
}

private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}
