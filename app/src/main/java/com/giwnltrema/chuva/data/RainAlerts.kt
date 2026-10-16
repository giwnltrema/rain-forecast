package com.giwnltrema.chuva.data

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class Alert(val title: String, val text: String)

private val ptBR = Locale("pt", "BR")

private fun Forecast.rainingNow(now: Instant): Boolean {
    val first = nowcast.firstOrNull()
    return current.precipitation >= 0.1 ||
        (first != null && first.mm >= WET_15MIN && !first.time.isAfter(now))
}

/** Distância que a chuva percorre em [leadMinutes] (~30 km/h, típico em SP), mín. 5 km. */
fun alertRadiusKm(leadMinutes: Int) = maxOf(5.0, leadMinutes * 0.5)

/**
 * Alerta de chuva começando dentro de [leadMinutes] (ou já chovendo).
 * Com radar recente, só avisa o que o radar confirma (evita alarme falso do modelo).
 * null quando não há nada para avisar.
 */
fun rainAlert(
    f: Forecast,
    radar: RadarNow?,
    place: String,
    leadMinutes: Int,
    now: Instant = Instant.now(),
): Alert? {
    if (radar != null && radar.isFresh(now)) return radarAlert(f, radar, place, leadMinutes, now)
    return modelAlert(f, place, leadMinutes, now)
}

private fun radarAlert(f: Forecast, r: RadarNow, place: String, leadMinutes: Int, now: Instant): Alert? {
    if (r.rainingHere) {
        val level = Intensity.ofRate(dbzToRate(r.dbzHere!!))
        return Alert("Está chovendo em $place", "${level.label} segundo o radar")
    }
    val near = r.nearestKm ?: return null
    val radius = alertRadiusKm(leadMinutes)
    val eta = r.etaMinutes
    val coming = eta != null && eta <= leadMinutes + 10
    if (near > radius || !(coming || near <= radius / 2)) return null

    val level = Intensity.ofRate(dbzToRate(r.maxDbzNearby ?: RAIN_DBZ)).label.lowercase()
    val km = "~${near.roundToInt().coerceAtLeast(2)} km"
    val fmt = DateTimeFormatter.ofPattern("HH'h'mm", ptBR).withZone(f.offset)
    return if (coming) {
        Alert(
            "Chuva ${relative(now, now.plusSeconds(eta!! * 60L), fmt)} em $place",
            "Radar: chuva $level a $km, vindo na sua direção",
        )
    } else {
        Alert("Chuva a $km de $place", "Radar: chuva $level bem perto de você")
    }
}

private fun modelAlert(f: Forecast, place: String, leadMinutes: Int, now: Instant): Alert? {
    val fmt = DateTimeFormatter.ofPattern("HH'h'mm", ptBR).withZone(f.offset)

    if (f.rainingNow(now)) {
        val rate = maxOf(f.current.precipitation, (f.nowcast.firstOrNull()?.mm ?: 0.0) * 4)
        val nextHour = f.nowcast.take(4).sumOf { it.mm }
        return Alert(
            title = "Está chovendo em $place",
            text = "${Intensity.ofRate(rate).label} · ${mm(nextHour)} na próxima hora",
        )
    }

    val limit = now.plus(Duration.ofMinutes(leadMinutes.toLong()))
    val start = f.nowcast.firstOrNull { it.mm >= WET_15MIN && it.time.isBefore(limit) } ?: return null
    val window = f.nowcast.filter { !it.time.isBefore(start.time) }.take(4)
    val peak = window.maxOf { it.mm } * 4
    return Alert(
        title = "Chuva ${relative(now, start.time, fmt)} em $place",
        text = "${Intensity.ofRate(peak).label} · ${mm(window.sumOf { it.mm })} na primeira hora",
    )
}

/** Sem chuva por perto: usado para "rearmar" o alerta depois de um episódio de chuva. */
fun dryAhead(f: Forecast, radar: RadarNow?, leadMinutes: Int, now: Instant = Instant.now()): Boolean {
    if (radar != null && radar.isFresh(now)) {
        return !radar.rainingHere && (radar.nearestKm ?: Double.MAX_VALUE) > alertRadiusKm(leadMinutes)
    }
    if (f.rainingNow(now)) return false
    val limit = now.plus(Duration.ofMinutes(60))
    return f.nowcast.none { it.mm >= WET_15MIN && it.time.isBefore(limit) }
}

/** Resumo do dia, para a notificação da manhã (olha até ~22h). */
fun morningSummary(f: Forecast, place: String, now: Instant = Instant.now()): Alert {
    val fmt = DateTimeFormatter.ofPattern("HH'h'", ptBR).withZone(f.offset)
    val today = f.hourly.filter { it.time.plusSeconds(3600).isAfter(now) }.take(16)
    val total = today.sumOf { it.mm }
    val maxProb = today.mapNotNull { it.probability }.maxOrNull() ?: 0
    val first = today.firstOrNull { it.mm >= 0.2 || (it.probability ?: 0) >= 50 }
    val temps = today.mapNotNull { it.temperature }
    val tempText = if (temps.isEmpty()) "" else " · ${temps.min().toInt()}° a ${temps.max().toInt()}°"

    return if (first == null) {
        Alert(
            title = "Hoje sem chuva em $place",
            text = "Chance máxima de $maxProb%$tempText",
        )
    } else {
        Alert(
            title = "☔ Leve o guarda-chuva hoje",
            text = "Chuva a partir das ${fmt.format(first.time)} em $place ($maxProb%) · ${mm(total)} no dia$tempText",
        )
    }
}
