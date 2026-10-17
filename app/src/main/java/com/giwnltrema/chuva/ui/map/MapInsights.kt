package com.giwnltrema.chuva.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.giwnltrema.chuva.MapState
import com.giwnltrema.chuva.data.Intensity
import com.giwnltrema.chuva.data.Place
import com.giwnltrema.chuva.data.dbzAt
import com.giwnltrema.chuva.data.dbzToRate
import com.giwnltrema.chuva.data.firstRainyHour
import com.giwnltrema.chuva.data.forecastActivity
import com.giwnltrema.chuva.data.mm
import com.giwnltrema.chuva.data.nearestRain
import com.giwnltrema.chuva.data.valueAt
import com.giwnltrema.chuva.ui.theme.RainScale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val hh = DateTimeFormatter.ofPattern("HH'h'").withZone(ZoneId.systemDefault())
private val ptBR = Locale("pt", "BR")

private fun rateText(rate: Double) = String.format(ptBR, "%.1f mm/h", rate)

/** Frase principal do mapa: o que está acontecendo (radar) ou vai acontecer (previsão). */
internal fun insight(mode: Mode, state: MapState, me: Place, index: Int): Pair<String, String?> = when (mode) {
    Mode.RADAR -> {
        val area = state.radar
        if (area == null) "Carregando radar…" to null
        else {
            val at = area.times.getOrNull(index)
            val n = nearestRain(area, index, me.lat, me.lon)
            val sub = at?.let { "Radar das ${hhmm.format(it)} · toque no mapa para ver um ponto" }
            when {
                n == null -> "Sem chuva num raio de ~150 km" to sub
                n.km < 2 -> "Chovendo onde você está · ${Intensity.ofRate(dbzToRate(n.dbz)).label.lowercase()}" to sub
                else -> "Chuva mais próxima: ~${n.km.roundToInt()} km a ${n.direction} · " +
                    Intensity.ofRate(dbzToRate(n.dbz)).label.lowercase() to sub
            }
        }
    }
    Mode.FORECAST -> {
        val grid = state.grid
        if (grid == null) "Carregando previsão…" to null
        else {
            val first = firstRainyHour(grid, Instant.now())
            val peak = forecastActivity(grid).maxOrNull() ?: 0.0
            val headline = if (first == null) "Sem chuva prevista na área nas próximas 24 h"
            else "Chuva na área a partir das ${hh.format(grid.times[first])} · até ${rateText(peak)}"
            val here = grid.valueAt(me.lat, me.lon, index)
            val t = grid.times.getOrNull(index)
            val sub = if (here != null && t != null) {
                if (here < 0.2) "Onde você está às ${hh.format(t)}: sem chuva"
                else "Onde você está às ${hh.format(t)}: ${rateText(here)} (${Intensity.ofRate(here).label.lowercase()})"
            } else null
            headline to sub
        }
    }
}

/** Texto ao tocar num ponto do mapa. */
internal fun pointInfo(mode: Mode, state: MapState, index: Int, lat: Double, lon: Double): String = when (mode) {
    Mode.RADAR -> {
        val area = state.radar
        val dbz = area?.dbzAt(lat, lon, index)
        val at = area?.times?.getOrNull(index)?.let { " às ${hhmm.format(it)}" }.orEmpty()
        when {
            area == null -> "Radar ainda carregando"
            dbz == null -> "Fora da área do radar — toque em \"Atualizar\" com o mapa aqui"
            dbz == Int.MIN_VALUE || dbzToRate(dbz) < 0.2 -> "Neste ponto$at: sem chuva"
            else -> dbzToRate(dbz).let { "Neste ponto$at: chuva ${Intensity.ofRate(it).label.lowercase()} (~${rateText(it)})" }
        }
    }
    Mode.FORECAST -> {
        val grid = state.grid
        val v = grid?.valueAt(lat, lon, index)
        val at = grid?.times?.getOrNull(index)?.let { " às ${hh.format(it)}" }.orEmpty()
        when {
            grid == null -> "Previsão ainda carregando"
            v == null -> "Fora da área da previsão — toque em \"Prever nesta área\""
            v < 0.2 -> "Neste ponto$at: sem chuva prevista"
            else -> "Neste ponto$at: ${rateText(v)} (${Intensity.ofRate(v).label.lowercase()}) · ${mm(v)} na hora"
        }
    }
}

@Composable
internal fun InsightCard(title: String, subtitle: String?, onClose: (() -> Unit)? = null) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fechar", Modifier.size(18.dp)) }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

/**
 * Linha do tempo em barrinhas: a altura mostra quanta chuva há na área em cada quadro,
 * então dá para ver de cara quando chove — e tocar/arrastar para ir até lá.
 */
@Composable
internal fun ActivityTimeline(
    values: List<Double>,
    fullScale: Double,
    index: Int,
    onSeek: (Int) -> Unit,
    startLabel: String,
    endLabel: String,
) {
    val n = values.size
    if (n == 0) return
    val scheme = MaterialTheme.colorScheme
    val top = max(fullScale, values.max())
    fun seek(x: Float, width: Int) = onSeek((x / width * n).toInt().coerceIn(0, n - 1))
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(n) { detectTapGestures { seek(it.x, size.width) } }
                .pointerInput(n) { detectHorizontalDragGestures { change, _ -> seek(change.position.x, size.width) } },
        ) {
            val slot = size.width / n
            val gap = (slot * 0.25f).coerceAtMost(4.dp.toPx())
            val w = slot - gap
            values.forEachIndexed { i, v ->
                val x = i * slot + gap / 2
                val h = if (v <= 0.0) 3.dp.toPx() else max(4.dp.toPx(), (v / top * size.height).toFloat())
                val color = when {
                    i == index -> scheme.primary
                    v <= 0.0 -> scheme.outlineVariant
                    fullScale > 1 -> RainScale.colorFor(v)
                    else -> scheme.secondary
                }
                drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
            }
            // marcador do quadro escolhido
            val cx = index * slot + slot / 2
            drawLine(scheme.primary, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}
