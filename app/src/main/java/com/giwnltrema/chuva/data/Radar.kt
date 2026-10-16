package com.giwnltrema.chuva.data

import java.time.Instant
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.tan

/** O que o radar mostra agora em volta de um ponto. */
data class RadarNow(
    val observedAt: Instant,
    /** Maior refletividade (dBZ) a até ~1,5 km do ponto; null = nada no radar. */
    val dbzHere: Int?,
    /** Distância até a chuva mais próxima (≥ [RAIN_DBZ]) num raio de [RADAR_RADIUS_KM]; null = nenhuma. */
    val nearestKm: Double?,
    /** Maior dBZ num raio de 25 km, para dizer se a chuva por perto é fraca ou forte. */
    val maxDbzNearby: Int?,
    /** Minutos até a chuva chegar, estimado pela aproximação entre dois quadros; null = não está vindo. */
    val etaMinutes: Int?,
) {
    val rainingHere get() = (dbzHere ?: Int.MIN_VALUE) >= RAIN_DBZ
    fun isFresh(now: Instant) = observedAt.isAfter(now.minusSeconds(40 * 60))
}

/** A partir de ~20 dBZ é chuva de verdade (~0,6 mm/h); abaixo é garoa fina, ruído ou "virga". */
const val RAIN_DBZ = 20
const val RADAR_RADIUS_KM = 60.0
const val RADAR_ZOOM = 7

/** Marshall–Palmer: Z = 200·R^1.6 → taxa em mm/h. */
fun dbzToRate(dbz: Int): Double = (10.0.pow(dbz / 10.0) / 200.0).pow(1 / 1.6)

/**
 * Paleta "Universal Blue" do RainViewer (a única do plano gratuito), de -10 a 65 dBZ,
 * em ARGB. Fonte: rainviewer.com/files/rainviewer_api_colors_table.csv
 */
object RadarPalette {
    private const val MIN_DBZ = -10
    private val colors = intArrayOf(
        0x14636159.toInt(), 0x1966635A.toInt(), 0x1E69665C.toInt(), 0x246C685D.toInt(), 0x296F6B5F.toInt(), 0x2E726E61.toInt(),
        0x34757062.toInt(), 0x39787364.toInt(), 0x3E7C7565.toInt(), 0x447F7867.toInt(), 0x49827B69.toInt(), 0x4E857D6A.toInt(),
        0x5488806C.toInt(), 0x598B826D.toInt(), 0x5E8E856F.toInt(), 0x64928871.toInt(), 0x6E9E9375.toInt(), 0x78AA9E79.toInt(),
        0x82B6A97E.toInt(), 0x8CC2B482.toInt(), 0x96CEC087.toInt(), 0xA0D2C48B.toInt(), 0xAAD6C88F.toInt(), 0xB4DACC93.toInt(),
        0xBEDED097.toInt(), 0xFF88DDEE.toInt(), 0xFF6CD1EB.toInt(), 0xFF51C5E8.toInt(), 0xFF36BAE5.toInt(), 0xFF1BAEE2.toInt(),
        0xFF00A3E0.toInt(), 0xFF009AD5.toInt(), 0xFF0091CA.toInt(), 0xFF0088BF.toInt(), 0xFF007FB4.toInt(), 0xFF0077AA.toInt(),
        0xFF0070A3.toInt(), 0xFF00699C.toInt(), 0xFF006295.toInt(), 0xFF005B8E.toInt(), 0xFF005588.toInt(), 0xFF005180.toInt(),
        0xFF004E78.toInt(), 0xFF004A70.toInt(), 0xFF004768.toInt(), 0xFFFFEE00.toInt(), 0xFFFFE000.toInt(), 0xFFFFD200.toInt(),
        0xFFFFC500.toInt(), 0xFFFFB700.toInt(), 0xFFFFAA00.toInt(), 0xFFFF9F00.toInt(), 0xFFFF9500.toInt(), 0xFFFF8B00.toInt(),
        0xFFFF8100.toInt(), 0xFFFF4400.toInt(), 0xFFF23600.toInt(), 0xFFE62800.toInt(), 0xFFD91B00.toInt(), 0xFFCD0D00.toInt(),
        0xFFC10000.toInt(), 0xFFA80000.toInt(), 0xFF8F0000.toInt(), 0xFF760000.toInt(), 0xFF5D0000.toInt(), 0xFFFFAAFF.toInt(),
        0xFFFF9FFF.toInt(), 0xFFFF95FF.toInt(), 0xFFFF8BFF.toInt(), 0xFFFF81FF.toInt(), 0xFFFF77FF.toInt(), 0xFFFF6CFF.toInt(),
        0xFFFF62FF.toInt(), 0xFFFF58FF.toInt(), 0xFFFF4EFF.toInt(), 0xFFFFFFFF.toInt(),
    )
    private val exact = HashMap<Int, Int>().apply { colors.forEachIndexed { i, c -> put(c, MIN_DBZ + i) } }

    /** dBZ de um pixel ARGB (não pré-multiplicado); null para transparente. */
    fun dbzOf(argb: Int): Int? {
        if (argb ushr 24 == 0) return null
        exact[argb]?.let { return it }
        // Tolerância para arredondamento na decodificação do PNG.
        var best = 0
        var bestD = Int.MAX_VALUE
        for (i in colors.indices) {
            val d = dist(argb, colors[i])
            if (d < bestD) { bestD = d; best = i }
        }
        return MIN_DBZ + best
    }

    private fun dist(a: Int, b: Int): Int {
        var sum = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val d = ((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)
            sum += d * d
        }
        return sum
    }
}

/** Coordenadas em pixels "globais" (Web Mercator) no zoom [RADAR_ZOOM], tiles de 256 px. */
data class GlobalPx(val x: Double, val y: Double)

fun toGlobalPx(lat: Double, lon: Double, zoom: Int = RADAR_ZOOM): GlobalPx {
    val n = 256.0 * (1 shl zoom)
    val x = (lon + 180.0) / 360.0 * n
    val y = (1 - asinh(tan(lat * PI / 180)) / PI) / 2 * n
    return GlobalPx(x, y)
}

/** Metros por pixel na latitude dada. */
fun metersPerPixel(lat: Double, zoom: Int = RADAR_ZOOM): Double =
    156_543.03392 * cos(lat * PI / 180) / (1 shl zoom)

data class FrameScan(val dbzHere: Int?, val nearestKm: Double?, val maxDbzNearby: Int?)

/** Leitor de um quadro: dBZ no pixel global (x, y) ou null se não houver eco. */
typealias FrameReader = (Int, Int) -> Int?

fun pixelKey(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

/** Janela de pixels em volta do ponto, até [RADAR_RADIUS_KM]. */
private class Window(lat: Double, lon: Double) {
    val c = toGlobalPx(lat, lon)
    val kmPerPx = metersPerPixel(lat) / 1000
    val r = (RADAR_RADIUS_KM / kmPerPx).toInt() + 1
    val size = 2 * r + 1
    val x0 = floor(c.x).toInt() - r
    val y0 = floor(c.y).toInt() - r

    fun km(i: Int, j: Int) = hypot(x0 + i + 0.5 - c.x, y0 + j + 0.5 - c.y) * kmPerPx

    /** dBZ em cada pixel da janela (Int.MIN_VALUE = sem eco / fora do raio). */
    fun read(frame: FrameReader): IntArray = IntArray(size * size) { idx ->
        val i = idx % size
        val j = idx / size
        if (km(i, j) > RADAR_RADIUS_KM) Int.MIN_VALUE else frame(x0 + i, y0 + j) ?: Int.MIN_VALUE
    }
}

/**
 * "Clutter": ecos parados que o radar vê em prédios, torres e morros (ex.: espigão da Paulista).
 * Um pixel é clutter se tem eco em pelo menos metade dos quadros (~2 h) enquanto a vizinhança
 * fica quase sempre seca — chuva de verdade se move ou cobre áreas grandes.
 * Devolve as chaves ([pixelKey]) dos pixels a ignorar, já com 1 pixel de margem.
 */
fun buildClutterMask(lat: Double, lon: Double, frames: List<FrameReader>): Set<Long> {
    if (frames.size < 6) return emptySet()
    val w = Window(lat, lon)
    val grids = frames.map { w.read(it) }
    val n = grids.size
    val persist = IntArray(w.size * w.size)
    for (g in grids) for (k in g.indices) if (g[k] >= 10) persist[k]++

    val base = ArrayList<Int>()
    for (k in persist.indices) {
        if (persist[k] * 2 < n) continue
        val i = k % w.size
        val j = k / w.size
        var total = 0
        var wet = 0
        for (dj in -6..6) for (di in -6..6) {
            val ii = i + di
            val jj = j + dj
            if (ii !in 0 until w.size || jj !in 0 until w.size) continue
            for (g in grids) {
                total++
                if (g[jj * w.size + ii] >= RAIN_DBZ) wet++
            }
        }
        if (total > 0 && wet.toDouble() / total < 0.35) base += k
    }

    val mask = HashSet<Long>()
    for (k in base) {
        val x = w.x0 + k % w.size
        val y = w.y0 + k / w.size
        for (dy in -1..1) for (dx in -1..1) mask += pixelKey(x + dx, y + dy)
    }
    return mask
}

/**
 * Varre um quadro do radar em volta do ponto, ignorando o [clutter] e pixels de chuva
 * isolados (exige pelo menos 2 vizinhos com chuva — chuva real vem em manchas).
 */
fun scanFrame(lat: Double, lon: Double, clutter: Set<Long>, frame: FrameReader): FrameScan {
    val w = Window(lat, lon)
    val g = w.read(frame)
    for (k in g.indices) {
        if (g[k] != Int.MIN_VALUE && pixelKey(w.x0 + k % w.size, w.y0 + k / w.size) in clutter) g[k] = Int.MIN_VALUE
    }
    fun rain(i: Int, j: Int) = i in 0 until w.size && j in 0 until w.size && g[j * w.size + i] >= RAIN_DBZ

    var here: Int? = null
    var nearest: Double? = null
    var maxNearby: Int? = null
    for (j in 0 until w.size) for (i in 0 until w.size) {
        val dbz = g[j * w.size + i]
        if (dbz == Int.MIN_VALUE) continue
        if (dbz >= RAIN_DBZ) {
            var neighbors = 0
            for (dj in -1..1) for (di in -1..1) if ((di != 0 || dj != 0) && rain(i + di, j + dj)) neighbors++
            if (neighbors < 2) continue
        }
        val km = w.km(i, j)
        if (km <= 1.5) here = maxOf(here ?: dbz, dbz)
        if (km <= 25) maxNearby = maxOf(maxNearby ?: dbz, dbz)
        if (dbz >= RAIN_DBZ && (nearest == null || km < nearest)) nearest = km
    }
    return FrameScan(here, nearest, maxNearby)
}

/**
 * Junta o quadro atual com um de ~30 min antes: se a chuva mais próxima
 * chegou mais perto, estima em quanto tempo ela alcança o ponto.
 */
fun radarNow(latest: FrameScan, latestAt: Instant, earlier: FrameScan?, earlierAt: Instant?): RadarNow {
    var eta: Int? = null
    val now = latest.nearestKm
    val before = earlier?.nearestKm
    if (now != null && before != null && earlierAt != null && now > 1.5) {
        val minutes = (latestAt.epochSecond - earlierAt.epochSecond) / 60.0
        val closed = before - now
        // Precisa ter andado pelo menos ~3 km para não confundir com chuva parada mudando de forma.
        if (minutes > 0 && closed >= 3.0) {
            val etaMin = now / (closed / minutes)
            if (etaMin <= 180) eta = etaMin.toInt()
        }
    }
    return RadarNow(latestAt, latest.dbzHere, latest.nearestKm, latest.maxDbzNearby, eta)
}

/** Guarda a máscara de clutter (o worker roda em processos diferentes, então fica em disco). */
interface ClutterCache {
    fun load(lat: Double, lon: Double, now: Instant): Set<Long>?
    fun save(lat: Double, lon: Double, now: Instant, mask: Set<Long>)
}
