package com.giwnltrema.chuva.data

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Frase principal da tela inicial: "vai chover?" e quando. */
data class RainSummary(
    val headline: String,
    val detail: String,
    val level: Intensity,
)

enum class Intensity(val label: String) {
    NONE("Sem chuva"),
    LIGHT("Fraca"),
    MODERATE("Moderada"),
    HEAVY("Forte"),
    VIOLENT("Muito forte");

    companion object {
        /** Classificação usual por taxa em mm/h. */
        fun ofRate(mmPerHour: Double): Intensity = when {
            mmPerHour < 0.1 -> NONE
            mmPerHour < 2.5 -> LIGHT
            mmPerHour < 10.0 -> MODERATE
            mmPerHour < 50.0 -> HEAVY
            else -> VIOLENT
        }
    }
}

internal const val WET_15MIN = 0.05 // mm em 15 min (~0,2 mm/h)
private const val WET_HOUR = 0.2
private const val LIKELY_PCT = 50

fun summarize(f: Forecast, now: Instant = Instant.now()): RainSummary {
    val hourFmt = DateTimeFormatter.ofPattern("HH'h'mm", Locale("pt", "BR")).withZone(f.offset)
    val slots = f.nowcast

    val first = slots.firstOrNull()
    val rainingNow = f.current.precipitation >= 0.1 || (first != null && first.mm >= WET_15MIN && !first.time.isAfter(now))

    if (rainingNow) {
        val rate = max(f.current.precipitation, (first?.mm ?: 0.0) * 4)
        val stop = slots.firstOrNull { it.time.isAfter(now) && it.mm < WET_15MIN }
        val total = slots.take(4).sumOf { it.mm }
        val detail = "${Intensity.ofRate(rate).label} · ${mm(total)} na próxima hora"
        return if (stop != null) {
            RainSummary("Chovendo agora", "Deve parar ${relative(now, stop.time, hourFmt)} · $detail", Intensity.ofRate(rate))
        } else {
            RainSummary("Chovendo agora", "Continua pelas próximas horas · $detail", Intensity.ofRate(rate))
        }
    }

    slots.firstOrNull { it.mm >= WET_15MIN }?.let { start ->
        val peak = slots.maxOf { it.mm } * 4
        val total = slots.sumOf { it.mm }
        return RainSummary(
            headline = "Chuva ${relative(now, start.time, hourFmt)}",
            detail = "${Intensity.ofRate(peak).label} · ${mm(total)} nas próximas 3 h",
            level = Intensity.ofRate(peak),
        )
    }

    val next24 = f.hourly.take(24)
    next24.firstOrNull { it.mm >= WET_HOUR || (it.probability ?: 0) >= LIKELY_PCT }?.let { h ->
        val total = next24.sumOf { it.mm }
        val prob = h.probability?.let { " ($it%)" }.orEmpty()
        return RainSummary(
            headline = "Chuva ${relative(now, h.time, hourFmt)}$prob",
            detail = "${Intensity.ofRate(h.mm).label} · ${mm(total)} previstos em 24 h",
            level = Intensity.ofRate(h.mm),
        )
    }

    val maxProb = next24.mapNotNull { it.probability }.maxOrNull() ?: 0
    return RainSummary(
        headline = "Sem chuva nas próximas 24 h",
        detail = if (maxProb > 0) "Chance máxima de $maxProb%" else "Pode deixar o guarda-chuva em casa",
        level = Intensity.NONE,
    )
}

internal fun relative(now: Instant, at: Instant, fmt: DateTimeFormatter): String {
    val minutes = Duration.between(now, at).toMinutes()
    return when {
        minutes <= 5 -> "a qualquer momento"
        minutes < 60 -> "em ~${(minutes / 5.0).roundToInt() * 5} min"
        minutes < 180 -> {
            val h = minutes / 60
            val m = ((minutes % 60) / 15.0).roundToInt() * 15
            if (m == 0 || m == 60) "em ~${h + m / 60} h" else "em ~${h}h${m}"
        }
        else -> "às ${fmt.format(at)}"
    }
}

fun mm(value: Double): String = when {
    value <= 0.0 -> "0 mm"
    value < 0.1 -> "<0,1 mm"
    value < 10 -> String.format(Locale("pt", "BR"), "%.1f mm", value)
    else -> String.format(Locale("pt", "BR"), "%.0f mm", value)
}

