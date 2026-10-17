package com.giwnltrema.chuva.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /**
     * Busca o destino em até três fontes e junta tudo, ordenado pela distância até você:
     *  1. Photon (OpenStreetMap) restrito a ~150 km de você — sem resultados de outros países;
     *     se vier pouco, tenta de novo sem palavras genéricas ("c6 bank" → "c6");
     *  2. Nominatim, mais tolerante com palavras a mais (só quando o Photon trouxe pouco);
     *  3. [deviceGeocoder]: o geocodificador do próprio Android (normalmente o do Google),
     *     bom com nomes de empresas que o OSM não tem.
     * Sem nada na região, procura no Brasil todo.
     */
    suspend fun search(
        query: String,
        nearLat: Double,
        nearLon: Double,
        deviceGeocoder: (suspend (String) -> List<Destination>)? = null,
    ): List<Destination> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val region = Box(nearLon - 1.4, nearLat - 1.4, nearLon + 1.4, nearLat + 1.4)
        coroutineScope {
            val device = async { deviceGeocoder?.let { g -> runCatching { g(q) }.getOrNull() }.orEmpty() }
            val found = ArrayList(runCatching { photon(q, nearLat, nearLon, region) }.getOrDefault(emptyList()))
            val simpler = withoutGenericWords(q)
            if (found.size < 3 && simpler != q && simpler.length >= 2) {
                found += runCatching { photon(simpler, nearLat, nearLon, region) }.getOrDefault(emptyList())
            }
            if (found.size < 3) found += runCatching { nominatim(q, region) }.getOrDefault(emptyList())
            found += device.await()
            if (found.isEmpty()) found += runCatching { photon(q, nearLat, nearLon, BRAZIL) }.getOrDefault(emptyList())
            rankResults(found, q, nearLat, nearLon)
        }
    }

    private fun photon(q: String, nearLat: Double, nearLon: Double, box: Box): List<Destination> {
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("lat", nearLat.fmt())
            .addQueryParameter("lon", nearLon.fmt())
            .addQueryParameter("bbox", box.csv())
            .addQueryParameter("limit", "8")
            .build().toString()
        return json.decodeFromString<PhotonResponse>(get(url)).features.mapNotNull { it.toDestination() }
    }

    /** Regra de uso do Nominatim: no máximo 1 requisição por segundo. */
    @Volatile private var lastNominatim = 0L

    private fun nominatim(q: String, box: Box): List<Destination> {
        val now = System.currentTimeMillis()
        if (now - lastNominatim < 1100) return emptyList()
        lastNominatim = now
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("countrycodes", "br")
            .addQueryParameter("viewbox", "${box.west.fmt()},${box.north.fmt()},${box.east.fmt()},${box.south.fmt()}")
            .addQueryParameter("bounded", "1")
            .addQueryParameter("limit", "6")
            .build().toString()
        return json.decodeFromString<List<NominatimPlace>>(get(url)).mapNotNull { it.toDestination() }
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

internal data class Box(val west: Double, val south: Double, val east: Double, val north: Double) {
    fun csv() = listOf(west, south, east, north).joinToString(",") { String.format(Locale.US, "%.4f", it) }
}

private val BRAZIL = Box(-74.0, -34.0, -34.0, 5.5)

/** Palavras que atrapalham a busca no OSM (o C6 Bank está cadastrado como "C6"). */
private val GENERIC_WORDS = setOf(
    "bank", "banco", "agencia", "agência", "loja", "empresa", "escritorio", "escritório",
    "sede", "predio", "prédio", "edificio", "edifício", "unidade", "filial", "matriz",
)

internal fun withoutGenericWords(q: String): String =
    q.split(Regex("\\s+")).filterNot { it.lowercase() in GENERIC_WORDS }.joinToString(" ")

/** Palavras que não ajudam a decidir relevância ("av" ≈ "avenida", "de"…). */
private val FILLER = setOf("de", "da", "do", "das", "dos", "e", "a", "o", "av", "avenida", "r", "rua", "al", "alameda", "praca", "praça")

private fun normalize(s: String) =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

private fun tokens(s: String) =
    normalize(s).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in FILLER }.toSet()

/**
 * Ordena os resultados: primeiro os que batem com mais palavras do que você digitou
 * (no nome ou no endereço), depois os mais perto. Junta repetidos — mesmo lugar a menos de
 * 150 m, ou trechos da mesma rua no mesmo bairro (fica o mais perto).
 */
internal fun rankResults(list: List<Destination>, query: String, lat: Double, lon: Double): List<Destination> {
    val wanted = tokens(withoutGenericWords(query)).ifEmpty { tokens(query) }
    fun score(d: Destination) = tokens(d.name + " " + d.detail).count { it in wanted }
    fun dist(d: Destination) = metersBetween(lat, lon, d.lat, d.lon)
    val out = ArrayList<Destination>()
    for (d in list.sortedBy(::dist)) {
        val dup = out.any {
            (metersBetween(it.lat, it.lon, d.lat, d.lon) < 150 &&
                (it.name.equals(d.name, ignoreCase = true) || it.detail == d.detail)) ||
                (it.name.equals(d.name, ignoreCase = true) && it.detail == d.detail)
        }
        if (!dup) out += d
    }
    return out.sortedWith(compareByDescending<Destination> { score(it) }.thenBy { dist(it) }).take(8)
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
internal data class NominatimPlace(
    val name: String? = null,
    val lat: String,
    val lon: String,
    val address: Map<String, String> = emptyMap(),
)

internal fun NominatimPlace.toDestination(): Destination? {
    val a = address
    val street = listOfNotNull(a["road"], a["house_number"]).joinToString(", ").ifBlank { null }
    val title = name?.ifBlank { null } ?: street ?: return null
    val detail = listOfNotNull(
        street.takeIf { title != street },
        a["suburb"] ?: a["neighbourhood"] ?: a["city_district"],
        a["city"] ?: a["town"],
    ).distinct().joinToString(" · ")
    return Destination(title, detail, lat.toDoubleOrNull() ?: return null, lon.toDoubleOrNull() ?: return null)
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
