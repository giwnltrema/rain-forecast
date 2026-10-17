package com.giwnltrema.chuva.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class TravelMode(val label: String, val profile: String) {
    CAR("Carro", "car"),
    BIKE("Bike", "bike"),
    FOOT("A pé", "foot"),
}

@Serializable
data class Destination(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Rota com o tempo acumulado (s, sem trânsito) até cada ponto. */
class Route(
    val lats: DoubleArray,
    val lons: DoubleArray,
    val seconds: DoubleArray,
    val meters: DoubleArray,
) {
    val durationS get() = seconds.last()
    val distanceM get() = meters.last()
}

/** Ponto da rota onde checamos a chuva. [offsetMin] = minutos desde a saída (já com o trânsito). */
data class RouteSample(val lat: Double, val lon: Double, val offsetMin: Double, val km: Double, val routeIndex: Int)

enum class RainSource { RADAR, MODEL, NONE }

data class SampleRain(val sample: RouteSample, val at: Instant, val rate: Double, val source: RainSource) {
    val wet get() = rate >= TRIP_WET_RATE
}

/** A partir de ~0,5 mm/h já molha quem está a pé/de moto. */
const val TRIP_WET_RATE = 0.5

/** Até quantos minutos à frente confiamos no radar projetado; depois, modelo. */
const val RADAR_HORIZON_MIN = 45.0

data class TripPlan(
    val departureOffsetMin: Int,
    val departure: Instant,
    val arrival: Instant,
    val points: List<SampleRain>,
) {
    /** Minutos do trajeto debaixo de chuva. */
    val wetMinutes: Double
        get() = points.zipWithNext().sumOf { (a, b) -> if (a.wet) b.sample.offsetMin - a.sample.offsetMin else 0.0 }

    /** "Quanta chuva você pega": soma de mm/h × tempo, em mm. */
    val exposureMm: Double
        get() = points.zipWithNext().sumOf { (a, b) -> a.rate * (b.sample.offsetMin - a.sample.offsetMin) / 60 }

    val maxRate get() = points.maxOfOrNull { it.rate } ?: 0.0
    val firstWet get() = points.firstOrNull { it.wet }

    /** Sem radar nem modelo para boa parte do trajeto: não dá para afirmar nada. */
    val unknown get() = points.isEmpty() || points.count { it.source == RainSource.NONE } * 2 > points.size

    /** Seco de verdade: sem chuva E com dados (falta de dado nunca vira "seco"). */
    val dry get() = !unknown && points.none { it.wet }
}

/**
 * Escolhe os pontos de checagem: um a cada [stepMin] minutos de viagem (no mínimo a cada
 * ~1/30 do trajeto) mais a chegada. [traffic] multiplica os tempos (OSRM não conhece trânsito).
 */
fun sampleRoute(route: Route, traffic: Double = 1.0, stepMin: Double = 1.5): List<RouteSample> {
    val totalMin = route.durationS / 60 * traffic
    val step = max(stepMin, totalMin / 30)
    val out = ArrayList<RouteSample>()
    var next = 0.0
    for (i in route.seconds.indices) {
        val t = route.seconds[i] / 60 * traffic
        if (t >= next || i == route.seconds.lastIndex) {
            out += RouteSample(route.lats[i], route.lons[i], t, route.meters[i] / 1000, i)
            next = t + step
        }
    }
    return out
}

/**
 * Chuva em cada ponto na hora em que você passa por ele.
 * [radar]: dBZ projetado em (lat, lon, minutos à frente) ou null sem cobertura.
 * [model]: mm/h do modelo no ponto [i] na hora dada, ou null.
 */
fun planTrip(
    samples: List<RouteSample>,
    departureOffsetMin: Int,
    now: Instant,
    radar: ((Double, Double, Double) -> Int?)?,
    model: (Int, Instant) -> Double?,
): TripPlan {
    val departure = now.plusSeconds(departureOffsetMin * 60L)
    val points = samples.mapIndexed { i, s ->
        val at = departure.plusSeconds((s.offsetMin * 60).toLong())
        val ahead = (at.epochSecond - now.epochSecond) / 60.0
        val fromRadar = if (ahead <= RADAR_HORIZON_MIN) radar?.invoke(s.lat, s.lon, ahead) else null
        when {
            fromRadar != null -> SampleRain(s, at, if (fromRadar == Int.MIN_VALUE) 0.0 else dbzToRate(fromRadar), RainSource.RADAR)
            else -> model(i, at)?.let { SampleRain(s, at, it, RainSource.MODEL) } ?: SampleRain(s, at, 0.0, RainSource.NONE)
        }
    }
    val arrival = departure.plusSeconds(((samples.lastOrNull()?.offsetMin ?: 0.0) * 60).toLong())
    return TripPlan(departureOffsetMin, departure, arrival, points)
}

/** Melhor saída: a que pega menos chuva; em empate, a mais cedo. */
fun bestPlan(plans: List<TripPlan>): Int {
    val known = plans.indices.filter { !plans[it].unknown }
    if (known.isEmpty() || plans.first().unknown) return 0
    val first = plans.first()
    val best = known.minBy { plans[it].exposureMm }
    // Só vale esperar se a melhora for clara.
    val gain = first.exposureMm - plans[best].exposureMm
    return if (best != 0 && (plans[best].dry && !first.dry || gain >= 0.15 && gain >= first.exposureMm * 0.3)) best else 0
}

data class TripSummary(val headline: String, val detail: String, val intensity: Intensity)

private val ptBR = Locale("pt", "BR")

fun summarizeTrip(plan: TripPlan, wetPlace: String? = null, zone: ZoneId = ZoneId.systemDefault()): TripSummary {
    val minutes = (plan.points.lastOrNull()?.sample?.offsetMin ?: 0.0).roundToInt()
    val fmt = DateTimeFormatter.ofPattern("HH'h'mm", ptBR).withZone(zone)
    if (plan.unknown) {
        return TripSummary(
            "Sem previsão para esse horário",
            "Não consegui dados de chuva para o trajeto · ~$minutes min, chegada ~${fmt.format(plan.arrival)}",
            Intensity.NONE,
        )
    }
    if (plan.dry) {
        return TripSummary(
            "Caminho seco",
            "Sem chuva prevista no trajeto · ~$minutes min, chegada ~${fmt.format(plan.arrival)}",
            Intensity.NONE,
        )
    }
    val first = plan.firstWet!!
    val level = Intensity.ofRate(plan.maxRate)
    val wet = plan.wetMinutes.roundToInt().coerceAtLeast(1)
    val where = wetPlace?.let { " perto de $it" } ?: " no km ${first.sample.km.roundToInt()}"
    val source = if (first.source == RainSource.RADAR) "radar" else "previsão"
    val headline = if (wet >= minutes - 1) "Chuva ${level.label.lowercase()} o caminho todo"
    else "Chuva ${level.label.lowercase()} em ~$wet min do trajeto"
    return TripSummary(headline, "A partir de ~${fmt.format(first.at)}$where ($source)", level)
}

fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) = metersBetween(lat1, lon1, lat2, lon2) / 1000

/** Distância aproximada em metros (suficiente para trechos curtos). */
internal fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val k = 111_320.0
    val dx = (lon2 - lon1) * k * cos((lat1 + lat2) / 2 * PI / 180)
    val dy = (lat2 - lat1) * k
    return sqrt(dx * dx + dy * dy)
}
