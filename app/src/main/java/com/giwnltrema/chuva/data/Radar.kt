package com.giwnltrema.chuva.data

import java.time.Instant
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sinh
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
    /**
     * Minutos até a chuva chegar, contados a partir de [observedAt] (não de agora!),
     * estimado pela aproximação entre dois quadros; null = não está vindo.
     */
    val etaMinutes: Int?,
) {
    val rainingHere get() = (dbzHere ?: Int.MIN_VALUE) >= RAIN_DBZ

    /** Quando a chuva deve chegar (o radar tem atraso, então pode já ter passado). */
    val arrivesAt: Instant? get() = etaMinutes?.let { observedAt.plusSeconds(it * 60L) }
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

/**
 * Janela quadrada de pixels (zoom [RADAR_ZOOM]) em volta de um ponto.
 * Com [circular], pixels além de [radiusKm] ficam de fora.
 */
class RadarWindow(lat: Double, lon: Double, val radiusKm: Double = RADAR_RADIUS_KM, private val circular: Boolean = true) {
    val c = toGlobalPx(lat, lon)
    val kmPerPx = metersPerPixel(lat) / 1000
    val r = (radiusKm / kmPerPx).toInt() + 1
    val size = 2 * r + 1
    val x0 = floor(c.x).toInt() - r
    val y0 = floor(c.y).toInt() - r

    fun km(i: Int, j: Int) = hypot(x0 + i + 0.5 - c.x, y0 + j + 0.5 - c.y) * kmPerPx

    /** dBZ em cada pixel da janela (Int.MIN_VALUE = sem eco / fora do raio). */
    fun read(frame: FrameReader): IntArray = IntArray(size * size) { idx ->
        val i = idx % size
        val j = idx / size
        if (circular && km(i, j) > radiusKm) Int.MIN_VALUE else frame(x0 + i, y0 + j) ?: Int.MIN_VALUE
    }

    val north get() = pxToLat(y0.toDouble())
    val south get() = pxToLat((y0 + size).toDouble())
    val west get() = pxToLon(x0.toDouble())
    val east get() = pxToLon((x0 + size).toDouble())
}

private const val WORLD_PX = 256.0 * (1 shl RADAR_ZOOM)
private fun pxToLon(x: Double) = x / WORLD_PX * 360.0 - 180.0
private fun pxToLat(y: Double) = atan(sinh(PI * (1 - 2 * y / WORLD_PX))) * 180 / PI

/**
 * "Clutter": ecos parados que o radar vê em prédios, torres e morros (ex.: espigão da Paulista).
 * Um pixel é clutter se tem eco em pelo menos metade dos quadros (~2 h) enquanto a vizinhança
 * fica quase sempre seca — chuva de verdade se move ou cobre áreas grandes.
 * Devolve uma máscara da janela (true = ignorar), já com 1 pixel de margem.
 */
fun clutterMask(w: RadarWindow, grids: List<IntArray>): BooleanArray {
    val mask = BooleanArray(w.size * w.size)
    val n = grids.size
    if (n < 6) return mask
    val persist = IntArray(w.size * w.size)
    for (g in grids) for (k in g.indices) if (g[k] >= 10) persist[k]++

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
        if (total == 0 || wet.toDouble() / total >= 0.35) continue
        for (dj in -1..1) for (di in -1..1) {
            val ii = i + di
            val jj = j + dj
            if (ii in 0 until w.size && jj in 0 until w.size) mask[jj * w.size + ii] = true
        }
    }
    return mask
}

/** Versão com chaves globais ([pixelKey]), para guardar entre execuções. */
fun buildClutterMask(lat: Double, lon: Double, frames: List<FrameReader>): Set<Long> {
    val w = RadarWindow(lat, lon)
    val mask = clutterMask(w, frames.map { w.read(it) })
    val keys = HashSet<Long>()
    for (k in mask.indices) if (mask[k]) keys += pixelKey(w.x0 + k % w.size, w.y0 + k / w.size)
    return keys
}

/**
 * Tira do quadro o que não é chuva: clutter e pixels de chuva isolados
 * (exige pelo menos 2 vizinhos com chuva — chuva real vem em manchas).
 *
 * Um pixel de clutter não é simplesmente apagado: se no quadro atual estiver chovendo em volta
 * dele (pixels não-clutter num raio de ~3 km), a chuva também está em cima dele — e ele recebe
 * o valor típico da vizinhança. Sem isso, a chuva de verdade "some" justamente sobre os prédios.
 */
fun cleanGrid(w: RadarWindow, grid: IntArray, mask: (Int) -> Boolean): IntArray {
    val size = w.size
    val masked = BooleanArray(grid.size) { grid[it] != Int.MIN_VALUE && mask(it) }
    val g = grid.copyOf()
    for (k in g.indices) {
        if (!masked[k]) continue
        val i = k % size
        val j = k / size
        var total = 0
        val wet = ArrayList<Int>()
        for (dj in -3..3) for (di in -3..3) {
            val ii = i + di
            val jj = j + dj
            if (ii !in 0 until size || jj !in 0 until size) continue
            val kk = jj * size + ii
            if (mask(kk)) continue
            total++
            if (grid[kk] >= RAIN_DBZ) wet += grid[kk]
        }
        g[k] = if (total >= 6 && wet.size * 10 >= total * 4) wet.sorted()[wet.size / 2] else Int.MIN_VALUE
    }
    fun rain(i: Int, j: Int) = i in 0 until size && j in 0 until size && g[j * size + i] >= RAIN_DBZ
    val out = g.copyOf()
    for (j in 0 until size) for (i in 0 until size) {
        if (!rain(i, j)) continue
        var neighbors = 0
        for (dj in -1..1) for (di in -1..1) if ((di != 0 || dj != 0) && rain(i + di, j + dj)) neighbors++
        if (neighbors < 2) out[j * size + i] = Int.MIN_VALUE
    }
    return out
}

/** Radar limpo (sem clutter) numa área quadrada, pronto para desenhar no mapa. */
class RadarArea(
    /** Canto noroeste da janela em pixels globais (zoom [RADAR_ZOOM]). */
    val x0: Int,
    val y0: Int,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
    val size: Int,
    val times: List<Instant>,
    /** dBZ por pixel, linha 0 = norte; Int.MIN_VALUE = sem chuva. */
    val grids: List<IntArray>,
)

fun buildRadarArea(lat: Double, lon: Double, radiusKm: Double, frames: List<Pair<Instant, FrameReader>>): RadarArea {
    val w = RadarWindow(lat, lon, radiusKm, circular = false)
    val grids = frames.map { w.read(it.second) }
    val mask = clutterMask(w, grids)
    return RadarArea(
        x0 = w.x0, y0 = w.y0,
        north = w.north, south = w.south, west = w.west, east = w.east,
        size = w.size,
        times = frames.map { it.first },
        grids = grids.map { cleanGrid(w, it) { k -> mask[k] } },
    )
}

/** Varre um quadro do radar em volta do ponto, ignorando o [clutter] e pixels de chuva isolados. */
fun scanFrame(lat: Double, lon: Double, clutter: Set<Long>, frame: FrameReader): FrameScan {
    val w = RadarWindow(lat, lon)
    val g = cleanGrid(w, w.read(frame)) { k -> pixelKey(w.x0 + k % w.size, w.y0 + k / w.size) in clutter }

    var here: Int? = null
    var nearest: Double? = null
    for (j in 0 until w.size) for (i in 0 until w.size) {
        val dbz = g[j * w.size + i]
        if (dbz == Int.MIN_VALUE) continue
        val km = w.km(i, j)
        if (km <= 1.5) here = maxOf(here ?: dbz, dbz)
        if (dbz >= RAIN_DBZ && (nearest == null || km < nearest)) nearest = km
    }
    // Intensidade da chuva mais próxima: o mais forte a até 5 km além dela (não a pior célula da região).
    var nearby: Int? = null
    if (nearest != null) {
        for (j in 0 until w.size) for (i in 0 until w.size) {
            val dbz = g[j * w.size + i]
            if (dbz >= RAIN_DBZ && w.km(i, j) <= nearest + 5) nearby = maxOf(nearby ?: dbz, dbz)
        }
    }
    return FrameScan(here, nearest, nearby)
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

// --- Nowcast: para onde a chuva está andando ---

/** Velocidade da chuva em pixels de radar (~1 km) por minuto. */
data class Motion(val dxPerMin: Double, val dyPerMin: Double) {
    fun kmh(lat: Double) = hypot(dxPerMin, dyPerMin) * 60 * metersPerPixel(lat) / 1000
}

/**
 * Estima o deslocamento médio das áreas de chuva entre dois quadros ([minutes] de diferença):
 * testa cada deslocamento e fica com o que mais sobrepõe a chuva de antes com a de agora.
 * null quando há pouca chuva para dizer algo.
 */
fun estimateMotion(prev: IntArray, curr: IntArray, size: Int, minutes: Double, maxShift: Int = 24): Motion? {
    val rain = curr.indices.filter { curr[it] >= RAIN_DBZ }
    if (rain.size < 30 || minutes <= 0) return null
    var best = 0
    var bestDx = 0
    var bestDy = 0
    var bestDist = 0
    for (sy in -maxShift..maxShift) for (sx in -maxShift..maxShift) {
        var hits = 0
        for (k in rain) {
            val i = k % size - sx
            val j = k / size - sy
            if (i in 0 until size && j in 0 until size && prev[j * size + i] >= RAIN_DBZ) hits++
        }
        val dist = sx * sx + sy * sy
        // Em empate, prefere o menor deslocamento (chuva parada é o palpite mais seguro).
        if (hits > best || (hits == best && dist < bestDist)) {
            best = hits; bestDx = sx; bestDy = sy; bestDist = dist
        }
    }
    if (best < rain.size * 0.25) return null
    return Motion(bestDx / minutes, bestDy / minutes)
}

/**
 * Projeta o último quadro do radar no futuro, deslocando a chuva com [motion]
 * (ou mantendo parada se null). A incerteza cresce com o tempo: olhamos um raio maior.
 */
class Nowcaster(private val area: RadarArea, val motion: Motion?, now: Instant = Instant.now()) {
    private val latest = area.grids.lastOrNull()
    val observedAt: Instant? = area.times.lastOrNull()

    /** O radar chega com 10–20 min de atraso: "daqui a X min" é X + idade do último quadro. */
    private val ageMin = observedAt?.let { maxOf(0.0, (now.epochSecond - it.epochSecond) / 60.0) } ?: 0.0

    /** dBZ esperado em (lat, lon) daqui a [minutesFromNow] min; null fora da área do radar. */
    fun dbzAt(lat: Double, lon: Double, minutesFromNow: Double): Int? {
        val minutesAhead = minutesFromNow + ageMin
        val g = latest ?: return null
        val p = toGlobalPx(lat, lon)
        val vx = motion?.dxPerMin ?: 0.0
        val vy = motion?.dyPerMin ?: 0.0
        val cx = (p.x - area.x0 - vx * minutesAhead).toInt()
        val cy = (p.y - area.y0 - vy * minutesAhead).toInt()
        if (cx !in 0 until area.size || cy !in 0 until area.size) return null
        val r = 1 + (minutesAhead / 20).toInt()
        var best = Int.MIN_VALUE
        for (dy in -r..r) for (dx in -r..r) {
            if (dx * dx + dy * dy > r * r) continue
            val i = cx + dx
            val j = cy + dy
            if (i in 0 until area.size && j in 0 until area.size) best = maxOf(best, g[j * area.size + i])
        }
        return best
    }

    companion object {
        /** Usa o quadro de ~30 min antes do último para medir o movimento. */
        fun from(area: RadarArea, now: Instant = Instant.now()): Nowcaster {
            val n = area.times.size
            if (n < 2) return Nowcaster(area, null, now)
            val last = area.times[n - 1]
            val i = (0 until n - 1).lastOrNull { !area.times[it].isAfter(last.minusSeconds(25 * 60)) } ?: 0
            val minutes = (last.epochSecond - area.times[i].epochSecond) / 60.0
            return Nowcaster(area, estimateMotion(area.grids[i], area.grids[n - 1], area.size, minutes), now)
        }
    }
}
