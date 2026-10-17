package com.giwnltrema.chuva.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.giwnltrema.chuva.data.SavedPlace
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.giwnltrema.chuva.HomeState
import com.giwnltrema.chuva.data.DaySlot
import com.giwnltrema.chuva.data.Forecast
import com.giwnltrema.chuva.data.HourSlot
import com.giwnltrema.chuva.data.Intensity
import com.giwnltrema.chuva.data.NowcastSlot
import com.giwnltrema.chuva.data.RadarNow
import com.giwnltrema.chuva.data.mm
import com.giwnltrema.chuva.data.summarize
import com.giwnltrema.chuva.ui.theme.RainScale
import com.giwnltrema.chuva.ui.weatherIcon
import com.giwnltrema.chuva.ui.weatherLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val ptBR = Locale("pt", "BR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onRefresh: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenAlerts: () -> Unit,
    alertsOn: Boolean,
    onSelectPlace: (String?) -> Unit,
    onEditPlace: (SavedPlace?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = state.forecast
    PullToRefreshBox(
        isRefreshing = state.loading && f != null,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Header(state, f, onOpenAlerts, alertsOn) }
            // Os chips ficam sempre na tela, mesmo enquanto a previsão do lugar carrega.
            item {
                PlaceChips(
                    places = state.places,
                    selected = state.selected,
                    onSelect = onSelectPlace,
                    onEdit = onEditPlace,
                )
            }
            when {
                f == null && state.error != null -> item { ErrorState(state.error, onRefresh) }
                f == null -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    item { Hero(f, state.radar) }
                    if (f.nowcast.isNotEmpty()) item { NowcastCard(f, onOpenMap) }
                    item { HourlyCard(f.hourly.take(24), f) }
                    item { DailyCard(f.daily) }
                    item {
                        Text(
                            "Previsão: Open-Meteo · Radar: RainViewer · Mapa: OpenFreeMap © OpenMapTiles © OpenStreetMap",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: HomeState, f: Forecast?, onOpenAlerts: () -> Unit, alertsOn: Boolean) {
    val saved = state.places.find { it.id == state.selected }
    val subtitle = when {
        f == null -> "Carregando…"
        else -> {
            val time = DateTimeFormatter.ofPattern("HH:mm", ptBR).withZone(f.offset).format(f.fetchedAt)
            if (saved == null && state.place?.isFallback == true) "Sem permissão de localização · atualizado às $time"
            else "Atualizado às $time"
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(saved?.kind?.icon() ?: Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.place?.name ?: "—",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onOpenAlerts) {
            Icon(
                if (alertsOn) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                contentDescription = "Notificações",
            )
        }
    }
}

@Composable
private fun Hero(f: Forecast, radar: RadarNow?) {
    val summary = summarize(f, radar)
    // O código de tempo do modelo pode dizer "garoa" com o radar seco: o radar manda.
    val radarDry = radar != null && radar.isFresh(f.fetchedAt) && !radar.rainingHere
    val code = if (radarDry && f.current.weatherCode in RAIN_CODES) 3 else f.current.weatherCode
    val label = if (radarDry && f.current.weatherCode in RAIN_CODES) "Sem chuva no radar" else weatherLabel(code)
    val scheme = MaterialTheme.colorScheme
    val accent = when (summary.level) {
        Intensity.NONE -> scheme.primaryContainer
        Intensity.LIGHT, Intensity.MODERATE -> Color(0xFF2196F3)
        Intensity.HEAVY -> Color(0xFFFF9800)
        Intensity.VIOLENT -> Color(0xFFD500F9)
    }
    val onAccent = if (summary.level == Intensity.NONE) scheme.onPrimaryContainer else Color.White
    val brush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.75f).compositeOver(scheme.tertiary)))

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .padding(24.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    f.current.temperature?.let { "${it.roundToInt()}°" } ?: "—",
                    style = MaterialTheme.typography.displayLarge,
                    color = onAccent,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    weatherIcon(code, f.current.isDay),
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(56.dp),
                )
            }
            Text(
                buildString {
                    append(label)
                    f.current.apparent?.let { append(" · sensação ${it.roundToInt()}°") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onAccent.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(20.dp))
            Text(summary.headline, style = MaterialTheme.typography.headlineMedium, color = onAccent)
            Spacer(Modifier.height(4.dp))
            Text(summary.detail, style = MaterialTheme.typography.bodyLarge, color = onAccent.copy(alpha = 0.9f))
        }
    }
}

private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

@Composable
private fun SectionCard(title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun NowcastCard(f: Forecast, onOpenMap: () -> Unit) {
    val total = f.nowcast.sumOf { it.mm }
    SectionCard(
        title = "Próximas 3 horas · previsão",
        trailing = {
            Text(mm(total), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        },
    ) {
        NowcastChart(f.nowcast, Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Agora", "+1 h", "+2 h", "+3 h").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Map, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ver radar e mapa de chuva")
        }
    }
}

@Composable
private fun NowcastChart(slots: List<NowcastSlot>, modifier: Modifier) {
    val empty = MaterialTheme.colorScheme.outlineVariant
    // Taxa em mm/h para a cor seguir a mesma escala do mapa.
    val rates = slots.map { it.mm * 4 }
    val top = max(2.5, rates.maxOrNull() ?: 0.0)
    Canvas(modifier) {
        val n = max(rates.size, 1)
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val r = CornerRadius(6.dp.toPx())
        rates.forEachIndexed { i, rate ->
            val x = i * (w + gap)
            if (rate < 0.1) {
                drawRoundRect(empty, Offset(x, size.height - 4.dp.toPx()), Size(w, 4.dp.toPx()), r)
            } else {
                val h = max(6.dp.toPx(), (rate / top * size.height).toFloat())
                drawRoundRect(RainScale.colorFor(rate), Offset(x, size.height - h), Size(w, h), r)
            }
        }
    }
}

@Composable
private fun HourlyCard(hours: List<HourSlot>, f: Forecast) {
    val fmt = DateTimeFormatter.ofPattern("HH'h'", ptBR).withZone(f.offset)
    val top = max(2.5, hours.maxOfOrNull { it.mm } ?: 0.0)
    SectionCard(title = "Próximas 24 horas", trailing = {
        Text(mm(hours.sumOf { it.mm }), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(hours) { i, h -> HourColumn(if (i == 0) "Agora" else fmt.format(h.time), h, top) }
        }
    }
}

@Composable
private fun HourColumn(label: String, h: HourSlot, top: Double) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Icon(weatherIcon(h.weatherCode, h.isDay), null, Modifier.size(22.dp), tint = scheme.onSurface)
        Text(h.temperature?.let { "${it.roundToInt()}°" } ?: "", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(56.dp).width(14.dp), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (h.mm < 0.1) 0.05f else (h.mm / top).toFloat().coerceIn(0.1f, 1f))
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (h.mm < 0.1) scheme.outlineVariant else RainScale.colorFor(h.mm)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (h.mm < 0.1) "–" else String.format(ptBR, "%.1f", h.mm),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            h.probability?.let { "$it%" } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = if ((h.probability ?: 0) >= 50) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyCard(days: List<DaySlot>) {
    val fmt = DateTimeFormatter.ofPattern("EEE, d", ptBR)
    val today = days.firstOrNull()?.date ?: LocalDate.now()
    SectionCard(title = "Próximos 7 dias") {
        days.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            val label = when (d.date) {
                today -> "Hoje"
                today.plusDays(1) -> "Amanhã"
                else -> fmt.format(d.date).replaceFirstChar { it.uppercase() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1.2f))
                Icon(weatherIcon(d.weatherCode), null, Modifier.size(22.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Rounded.WaterDrop, null, Modifier.size(14.dp),
                        tint = if (d.mm >= 0.1) RainScale.colorFor(d.mm / 6) else MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(mm(d.mm), style = MaterialTheme.typography.labelLarge)
                        d.probability?.let {
                            Text("$it%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "${d.tMin?.roundToInt() ?: "–"}° / ${d.tMax?.roundToInt() ?: "–"}°",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Não deu para carregar a previsão", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Tentar de novo")
        }
    }
}
