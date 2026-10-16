package com.giwnltrema.chuva.data

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Alert(val title: String, val text: String)

private val ptBR = Locale("pt", "BR")

private fun Forecast.rainingNow(now: Instant): Boolean {
    val first = nowcast.firstOrNull()
    return current.precipitation >= 0.1 ||
        (first != null && first.mm >= WET_15MIN && !first.time.isAfter(now))
}

/**
 * Alerta de chuva começando dentro de [leadMinutes] (ou já chovendo).
 * null quando não há nada para avisar.
 */
fun rainAlert(f: Forecast, place: String, leadMinutes: Int, now: Instant = Instant.now()): Alert? {
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

/** Próxima hora seca: usado para "rearmar" o alerta depois de um episódio de chuva. */
fun dryAhead(f: Forecast, now: Instant = Instant.now()): Boolean {
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
