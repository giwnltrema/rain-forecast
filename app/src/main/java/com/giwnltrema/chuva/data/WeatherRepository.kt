package com.giwnltrema.chuva.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fontes gratuitas e sem chave de API:
 *  - Open-Meteo: previsão pontual (15 min, horária, diária) e grade para o mapa.
 *  - RainViewer: imagens de radar das últimas ~2 h.
 */
class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    /** PNG → pixels ARGB 256x256 não pré-multiplicados (Android: BitmapFactory). */
    private val decodeArgb: (ByteArray) -> IntArray? = ::decodePng,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun forecast(lat: Double, lon: Double): Forecast = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${lat.fmt()}&longitude=${lon.fmt()}" +
            "&current=temperature_2m,apparent_temperature,precipitation,weather_code,is_day" +
            "&minutely_15=precipitation&forecast_minutely_15=12" +
            "&hourly=precipitation,precipitation_probability,temperature_2m,weather_code,is_day" +
            "&forecast_hours=48" +
            "&daily=precipitation_sum,precipitation_probability_max,temperature_2m_min,temperature_2m_max,weather_code" +
            "&forecast_days=7&timezone=auto&timeformat=unixtime"
        json.decodeFromString<OmResponse>(get(url)).toForecast()
    }

    suspend fun radarFrames(): List<RadarFrame> = withContext(Dispatchers.IO) {
        val maps = json.decodeFromString<RvMaps>(get("https://api.rainviewer.com/public/weather-maps.json"))
        (maps.radar.past + maps.radar.nowcast).map { RadarFrame(Instant.ofEpochSecond(it.time), maps.host + it.path) }
    }

    /**
     * Lê o radar observado em volta do ponto: se está chovendo ali, a que distância
     * está a chuva mais próxima e se ela está se aproximando. null se o radar falhar.
     * [clutter] guarda a máscara de ecos falsos entre chamadas (calculá-la baixa ~2 h de quadros).
     */
    suspend fun radarNow(
        lat: Double,
        lon: Double,
        clutter: ClutterCache? = null,
        frames: List<RadarFrame>? = null,
    ): RadarNow? = withContext(Dispatchers.IO) {
        runCatching {
            val now = Instant.now()
            val past = (frames ?: radarFrames()).filter { !it.time.isAfter(now) }
            val latest = past.lastOrNull() ?: return@runCatching null
            val earlier = past.lastOrNull { !it.time.isAfter(latest.time.minusSeconds(25 * 60)) }
            val readers = past.associateWith { reader(it) }

            val mask = clutter?.load(lat, lon, now)
                ?: buildClutterMask(lat, lon, past.map { readers.getValue(it) })
                    .also { clutter?.save(lat, lon, now, it) }

            val latestScan = scanFrame(lat, lon, mask, readers.getValue(latest))
            val earlierScan = earlier?.let { runCatching { scanFrame(lat, lon, mask, readers.getValue(it)) }.getOrNull() }
            radarNow(latestScan, latest.time, earlierScan, earlier?.time)
        }.getOrNull()
    }

    /** Radar limpo (sem clutter) numa área de ~[radiusKm] em volta do ponto, para o mapa. */
    suspend fun radarArea(lat: Double, lon: Double, radiusKm: Double = 150.0): RadarArea = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val frames = radarFrames().filter { !it.time.isAfter(now) }
        // Baixa todos os tiles de todos os quadros em paralelo (~13 quadros x até 4 tiles).
        val w = RadarWindow(lat, lon, radiusKm, circular = false)
        val tileCoords = buildList {
            for (ty in Math.floorDiv(w.y0, 256)..Math.floorDiv(w.y0 + w.size - 1, 256))
                for (tx in Math.floorDiv(w.x0, 256)..Math.floorDiv(w.x0 + w.size - 1, 256)) add(tx to ty)
        }
        val readers = coroutineScope {
            frames.map { frame ->
                async {
                    val tiles = HashMap<Long, IntArray?>()
                    tileCoords.map { (tx, ty) -> async { pixelKey(tx, ty) to fetchTile(frame, tx, ty) } }
                        .awaitAll().forEach { (k, v) -> tiles[k] = v }
                    frame.time to reader(frame, tiles)
                }
            }.awaitAll()
        }
        buildRadarArea(lat, lon, radiusKm, readers)
    }

    /**
     * Tile sem suavização (0_0), para as cores baterem exatamente com a paleta.
     * Erro de rede propaga (não pode virar "sem chuva"); 404 = fora da cobertura = null.
     */
    private fun fetchTile(frame: RadarFrame, tx: Int, ty: Int): IntArray? =
        getBytes("${frame.base}/256/$RADAR_ZOOM/$tx/$ty/2/0_0.png")?.let(decodeArgb)

    /** Lê pixels de um quadro, baixando cada tile uma vez só. */
    private fun reader(frame: RadarFrame, tiles: HashMap<Long, IntArray?> = HashMap()): FrameReader {
        return { x, y ->
            val tx = Math.floorDiv(x, 256)
            val ty = Math.floorDiv(y, 256)
            val pixels = tiles.getOrPut(pixelKey(tx, ty)) { fetchTile(frame, tx, ty) }
            pixels?.let { RadarPalette.dbzOf(it[Math.floorMod(y, 256) * 256 + Math.floorMod(x, 256)]) }
        }
    }

    private fun getBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).header("User-Agent", "Chuva-Android/1.0").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.bytes()
        }
    }

    /**
     * Previsão horária de chuva numa grade [size]x[size] centrada em (lat, lon),
     * com [step] graus entre os pontos (~9 km para 0.08°).
     */
    suspend fun forecastGrid(
        lat: Double,
        lon: Double,
        size: Int = 15,
        step: Double = 0.08,
        hours: Int = 24,
    ): ForecastGrid = withContext(Dispatchers.IO) {
        val half = (size - 1) / 2.0
        val lats = List(size) { r -> lat + (half - r) * step }
        val lons = List(size) { c -> lon + (c - half) * step }
        val qLat = ArrayList<String>(size * size)
        val qLon = ArrayList<String>(size * size)
        for (r in 0 until size) for (c in 0 until size) {
            qLat += lats[r].fmt()
            qLon += lons[c].fmt()
        }
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${qLat.joinToString(",")}&longitude=${qLon.joinToString(",")}" +
            "&hourly=precipitation&forecast_hours=$hours&timeformat=unixtime"
        val points = json.decodeFromString<List<OmResponse>>(get(url))
        require(points.size == size * size) { "Resposta incompleta da grade (${points.size})" }

        val times = points.first().hourly?.time.orEmpty().map { Instant.ofEpochSecond(it - 3600) }
        val values = times.indices.map { t ->
            Array(size) { r ->
                DoubleArray(size) { c ->
                    points[r * size + c].hourly?.precipitation?.getOrNull(t) ?: 0.0
                }
            }
        }
        ForecastGrid(
            north = lats.first(), south = lats.last(),
            west = lons.first(), east = lons.last(),
            rows = size, cols = size, times = times, values = values,
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "Chuva-Android/1.0").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} em ${request.url.host}")
            return resp.body?.string() ?: throw IOException("Resposta vazia")
        }
    }

    private fun Double.fmt() = String.format(Locale.US, "%.4f", this)
}

/**
 * No Open-Meteo, a precipitação de um horário T é o acumulado do intervalo que *termina* em T.
 * Aqui guardamos o *início* do intervalo, que é como a gente lê ("chuva das 14h às 15h").
 */
internal fun OmResponse.toForecast(now: Instant = Instant.now()): Forecast {
    val offset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
    val c = current
    val m = minutely15
    val h = hourly
    val d = daily

    val nowcast = m?.time.orEmpty().mapIndexed { i, t ->
        NowcastSlot(Instant.ofEpochSecond(t - 15 * 60), m!!.precipitation.getOrNull(i) ?: 0.0)
    }.filter { it.time.plusSeconds(15 * 60) > now }

    val hours = h?.time.orEmpty().mapIndexed { i, t ->
        HourSlot(
            time = Instant.ofEpochSecond(t - 3600),
            mm = h!!.precipitation.getOrNull(i) ?: 0.0,
            probability = h.probability.getOrNull(i),
            temperature = h.temperature.getOrNull(i),
            weatherCode = h.weatherCode.getOrNull(i),
            isDay = (h.isDay.getOrNull(i) ?: 1) == 1,
        )
    }.filter { it.time.plusSeconds(3600) > now }

    val days = d?.time.orEmpty().mapIndexed { i, t ->
        DaySlot(
            // Com timezone=auto, o "time" diário é meia-noite local.
            date = LocalDateTime.ofEpochSecond(t, 0, offset).toLocalDate(),
            mm = d!!.precipitation.getOrNull(i) ?: 0.0,
            probability = d.probability.getOrNull(i),
            tMin = d.tMin.getOrNull(i),
            tMax = d.tMax.getOrNull(i),
            weatherCode = d.weatherCode.getOrNull(i),
        )
    }

    return Forecast(
        current = Current(
            temperature = c?.temperature,
            apparent = c?.apparent,
            precipitation = c?.precipitation ?: 0.0,
            weatherCode = c?.weatherCode,
            isDay = (c?.isDay ?: 1) == 1,
        ),
        nowcast = nowcast,
        hourly = hours,
        daily = days,
        offset = offset,
        fetchedAt = now,
    )
}

private fun decodePng(bytes: ByteArray): IntArray? {
    val opts = BitmapFactory.Options().apply {
        inPremultiplied = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    if (bmp.width != 256 || bmp.height != 256) return null
    return IntArray(256 * 256).also { bmp.getPixels(it, 0, 256, 0, 0, 256, 256); bmp.recycle() }
}
