package com.giwnltrema.chuva.data

import java.time.Instant
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Cálculos que transformam o radar/previsão do mapa em frases e na linha do tempo.

/** Direção (de onde você está até o ponto) em português, 8 rumos. */
fun compass(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
    val a = toGlobalPx(fromLat, fromLon)
    val b = toGlobalPx(toLat, toLon)
    // y do Mercator cresce para o sul
    val deg = (atan2(b.x - a.x, a.y - b.y) * 180 / PI + 360) % 360
    val names = listOf("norte", "nordeste", "leste", "sudeste", "sul", "sudoeste", "oeste", "noroeste")
    return names[((deg + 22.5) / 45).toInt() % 8]
}

data class NearestRain(val km: Double, val direction: String, val dbz: Int)

/** Chuva mais próxima de (lat, lon) no quadro [frame] do radar; null se não houver na área. */
fun nearestRain(area: RadarArea, frame: Int, lat: Double, lon: Double): NearestRain? {
    val g = area.grids.getOrNull(frame) ?: return null
    val p = toGlobalPx(lat, lon)
    val kmPerPx = metersPerPixel(lat) / 1000
    var best = Double.MAX_VALUE
    var bi = -1
    var bj = -1
    for (j in 0 until area.size) for (i in 0 until area.size) {
        if (g[j * area.size + i] < RAIN_DBZ) continue
        val d = hypot(area.x0 + i + 0.5 - p.x, area.y0 + j + 0.5 - p.y)
        if (d < best) { best = d; bi = i; bj = j }
    }
    if (bi < 0) return null
    // Intensidade: o mais forte a até ~5 km da borda mais próxima.
    var dbz = Int.MIN_VALUE
    val r = (5 / kmPerPx).toInt()
    for (dj in -r..r) for (di in -r..r) {
        val i = bi + di
        val j = bj + dj
        if (i in 0 until area.size && j in 0 until area.size) dbz = max(dbz, g[j * area.size + i])
    }
    val gx = area.x0 + bi + 0.5
    val gy = area.y0 + bj + 0.5
    return NearestRain(best * kmPerPx, compass(lat, lon, pxToLatitude(gy), pxToLongitude(gx)), dbz)
}

/** Fração da área com chuva em cada quadro (0..1) — alimenta as barrinhas da linha do tempo. */
fun radarActivity(area: RadarArea): List<Double> = area.grids.map { g ->
    g.count { it >= RAIN_DBZ }.toDouble() / g.size
}

/** Maior chuva prevista (mm/h) na grade em cada hora. */
fun forecastActivity(grid: ForecastGrid): List<Double> = grid.values.map { rows -> rows.maxOf { it.max() } }

/** Primeira hora (a partir de agora) com chuva relevante na área, para o mapa já abrir nela. */
fun firstRainyHour(grid: ForecastGrid, now: Instant, minRate: Double = 1.0): Int? {
    val activity = forecastActivity(grid)
    return grid.times.indices.firstOrNull { grid.times[it].plusSeconds(3600).isAfter(now) && activity[it] >= minRate }
}

/** Valor da previsão em (lat, lon) na hora [t] por interpolação bilinear; null fora da grade. */
fun ForecastGrid.valueAt(lat: Double, lon: Double, t: Int): Double? {
    val v = values.getOrNull(t) ?: return null
    if (lat !in south..north || lon !in west..east) return null
    val gy = (north - lat) / (north - south) * (rows - 1)
    val gx = (lon - west) / (east - west) * (cols - 1)
    val r0 = min(floor(gy).toInt(), rows - 2)
    val c0 = min(floor(gx).toInt(), cols - 2)
    val fy = gy - r0
    val fx = gx - c0
    return v[r0][c0] * (1 - fx) * (1 - fy) + v[r0][c0 + 1] * fx * (1 - fy) +
        v[r0 + 1][c0] * (1 - fx) * fy + v[r0 + 1][c0 + 1] * fx * fy
}

/** dBZ do radar em (lat, lon) no quadro [frame]; null fora da área, MIN_VALUE sem chuva. */
fun RadarArea.dbzAt(lat: Double, lon: Double, frame: Int): Int? {
    val g = grids.getOrNull(frame) ?: return null
    val p = toGlobalPx(lat, lon)
    val i = floor(p.x).toInt() - x0
    val j = floor(p.y).toInt() - y0
    if (i !in 0 until size || j !in 0 until size) return null
    // pequena vizinhança (o radar tem ~1 km por pixel e o dedo não é tão preciso)
    var best = Int.MIN_VALUE
    for (dj in -1..1) for (di in -1..1) {
        val ii = i + di
        val jj = j + dj
        if (ii in 0 until size && jj in 0 until size) best = max(best, g[jj * size + ii])
    }
    return best
}
