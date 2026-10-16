package com.giwnltrema.chuva.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Busca de endereço (Photon/OSM), rota (OSRM do openstreetmap.de) e previsão de 15 em 15 min
 * nos pontos da rota (Open-Meteo). Todos gratuitos e sem chave.
 */
class TripRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun search(query: String, nearLat: Double, nearLon: Double): List<Destination> = withContext(Dispatchers.IO) {
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("lat", nearLat.fmt())
            .addQueryParameter("lon", nearLon.fmt())
            .addQueryParameter("limit", "7")
            .build().toString()
        json.decodeFromString<PhotonResponse>(get(url)).features.mapNotNull { it.toDestination() }
    }

    suspend fun route(fromLat: Double, fromLon: Double, to: Destination, mode: TravelMode): Route = withContext(Dispatchers.IO) {
        val url = "https://routing.openstreetmap.de/routed-${mode.profile}/route/v1/driving/" +
            "${fromLon.fmt()},${fromLat.fmt()};${to.lon.fmt()},${to.lat.fmt()}" +
            "?overview=full&geometries=geojson&annotations=duration,distance"
        val resp = json.decodeFromString<OsrmResponse>(get(url))
        val r = resp.routes.firstOrNull() ?: throw IOException("Não achei um caminho até lá")
        val coords = r.geometry.coordinates
        val durations = r.legs.flatMap { it.annotation.duration }
        val distances = r.legs.flatMap { it.annotation.distance }
        val n = coords.size
        val seconds = DoubleArray(n)
        val meters = DoubleArray(n)
        for (i in 1 until n) {
            seconds[i] = seconds[i - 1] + (durations.getOrNull(i - 1) ?: 0.0)
            meters[i] = meters[i - 1] + (distances.getOrNull(i - 1) ?: 0.0)
        }
        Route(
            lats = DoubleArray(n) { coords[it][1] },
            lons = DoubleArray(n) { coords[it][0] },
            seconds = seconds,
            meters = meters,
        )
    }

    /**
     * Precipitação de 15 em 15 min (próximas 6 h) para cada ponto; pontos muito próximos
     * compartilham a mesma consulta (o modelo tem resolução de alguns km).
     * Devolve uma função (índice do ponto, horário) → mm/h.
     */
    suspend fun modelAlong(samples: List<RouteSample>): (Int, Instant) -> Double? = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext { _, _ -> null }
        fun key(s: RouteSample) = (s.lat * 40).toInt() to (s.lon * 40).toInt() // ~2,5 km
        val unique = samples.map(::key).distinct()
        val first = unique.associateWith { k -> samples.first { key(it) == k } }
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${unique.joinToString(",") { first.getValue(it).lat.fmt() }}" +
            "&longitude=${unique.joinToString(",") { first.getValue(it).lon.fmt() }}" +
            "&minutely_15=precipitation&forecast_minutely_15=24&timeformat=unixtime"
        val body = get(url)
        val points: List<OmResponse> =
            if (unique.size == 1) listOf(json.decodeFromString<OmResponse>(body))
            else json.decodeFromString<List<OmResponse>>(body)
        val byKey = unique.zip(points).toMap()
        val result: (Int, Instant) -> Double? = { i, at ->
            byKey[key(samples[i])]?.minutely15?.let { m ->
                // Valor de um horário = acumulado dos 15 min que terminam nele.
                val idx = m.time.indexOfFirst { t -> at.epochSecond in (t - 900) until t }
                m.precipitation.getOrNull(idx)?.let { it * 4 }
            }
        }
        result
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "Chuva-Android/1.0 (app pessoal)").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} em ${request.url.host}")
            return resp.body?.string() ?: throw IOException("Resposta vazia")
        }
    }

    private fun Double.fmt() = String.format(Locale.US, "%.5f", this)
}

// --- Formatos das APIs ---

@Serializable
internal data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@Serializable
internal data class PhotonFeature(val geometry: PhotonGeometry, val properties: PhotonProps)

@Serializable
internal data class PhotonGeometry(val coordinates: List<Double>)

@Serializable
internal data class PhotonProps(
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val district: String? = null,
    val locality: String? = null,
    val city: String? = null,
    val state: String? = null,
)

internal fun PhotonFeature.toDestination(): Destination? {
    val p = properties
    val c = geometry.coordinates
    if (c.size < 2) return null
    val streetLine = listOfNotNull(p.street, p.housenumber).joinToString(", ").ifBlank { null }
    val name = p.name ?: streetLine ?: return null
    val detail = listOfNotNull(
        streetLine.takeIf { p.name != null },
        p.district ?: p.locality,
        p.city,
    ).distinct().joinToString(" · ")
    return Destination(name, detail, lat = c[1], lon = c[0])
}

@Serializable
internal data class OsrmResponse(val code: String, val routes: List<OsrmRoute> = emptyList())

@Serializable
internal data class OsrmRoute(val geometry: OsrmGeometry, val legs: List<OsrmLeg> = emptyList())

@Serializable
internal data class OsrmGeometry(val coordinates: List<List<Double>>)

@Serializable
internal data class OsrmLeg(val annotation: OsrmAnnotation = OsrmAnnotation())

@Serializable
internal data class OsrmAnnotation(val duration: List<Double> = emptyList(), val distance: List<Double> = emptyList())

/** Destinos recentes (os 6 últimos). */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var recents: List<Destination>
        get() = runCatching { json.decodeFromString<List<Destination>>(prefs.getString("recents", "[]")!!) }.getOrDefault(emptyList())
        private set(v) = prefs.edit().putString("recents", json.encodeToString(v)).apply()

    fun remember(d: Destination) {
        recents = (listOf(d) + recents.filterNot { it.name == d.name && metersBetween(it.lat, it.lon, d.lat, d.lon) < 200 }).take(6)
    }

    var mode: TravelMode
        get() = runCatching { TravelMode.valueOf(prefs.getString("mode", "CAR")!!) }.getOrDefault(TravelMode.CAR)
        set(v) = prefs.edit().putString("mode", v.name).apply()

    var heavyTraffic: Boolean
        get() = prefs.getBoolean("traffic", false)
        set(v) = prefs.edit().putBoolean("traffic", v).apply()
}
