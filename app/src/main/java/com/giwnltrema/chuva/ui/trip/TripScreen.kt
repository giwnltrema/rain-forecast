package com.giwnltrema.chuva.ui.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.giwnltrema.chuva.DEPARTURES
import com.giwnltrema.chuva.TripState
import com.giwnltrema.chuva.data.Destination
import com.giwnltrema.chuva.data.Intensity
import com.giwnltrema.chuva.data.RADAR_HORIZON_MIN
import com.giwnltrema.chuva.data.TravelMode
import com.giwnltrema.chuva.data.TripPlan
import com.giwnltrema.chuva.data.summarizeTrip
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val clock = DateTimeFormatter.ofPattern("HH'h'mm").withZone(ZoneId.systemDefault())

@Composable
fun TripScreen(
    state: TripState,
    onQuery: (String) -> Unit,
    onChoose: (Destination) -> Unit,
    onMode: (TravelMode) -> Unit,
    onTraffic: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text("Vai chover no caminho?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                placeholder = { Text("Para onde você vai?") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close, "Limpar",
                            Modifier.clip(CircleShape).clickable { onClear() }.padding(8.dp),
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.suggestions.firstOrNull()?.let { focus.clearFocus(); onChoose(it) } }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { ModeRow(state, onMode, onTraffic) }

        when {
            state.suggestions.isNotEmpty() -> items(state.suggestions) { d ->
                PlaceRow(d, Icons.Rounded.Place) { focus.clearFocus(); onChoose(d) }
            }
            state.destination == null -> {
                if (state.recents.isEmpty()) {
                    item { EmptyHint() }
                } else {
                    item { Text("Recentes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.recents) { d -> PlaceRow(d, Icons.Rounded.History) { focus.clearFocus(); onChoose(d) } }
                }
            }
            state.loading -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Traçando a rota e olhando o radar…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error != null -> item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Não deu para calcular", style = MaterialTheme.typography.titleMedium)
                    Text(state.error, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("Tentar de novo") }
                }
            }
            state.route != null && state.plans.isNotEmpty() -> {
                val plan = state.plans[state.selected]
                item { Summary(state, plan) }
                item {
                    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        TripMap(state.route, plan)
                    }
                }
                item { Legend() }
                item { Departures(state, onSelect) }
                item { Timeline(plan) }
                item {
                    Text(
                        "Até ~${RADAR_HORIZON_MIN.roundToInt()} min à frente: radar real projetado pelo movimento da chuva. " +
                            "Depois: previsão do modelo. " +
                            if (state.mode == TravelMode.CAR && !state.heavyTraffic) "Tempo de viagem sem trânsito." else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRow(state: TripState, onMode: (TravelMode) -> Unit, onTraffic: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TravelMode.entries.forEach { m ->
            FilterChip(
                selected = state.mode == m,
                onClick = { onMode(m) },
                label = { Text(m.label) },
                leadingIcon = {
                    Icon(
                        when (m) {
                            TravelMode.CAR -> Icons.Rounded.DirectionsCar
                            TravelMode.BIKE -> Icons.Rounded.DirectionsBike
                            TravelMode.FOOT -> Icons.Rounded.DirectionsWalk
                        },
                        null, Modifier.size(18.dp),
                    )
                },
            )
        }
        if (state.mode == TravelMode.CAR) {
            FilterChip(
                selected = state.heavyTraffic,
                onClick = { onTraffic(!state.heavyTraffic) },
                label = { Text("Trânsito") },
                leadingIcon = { Icon(Icons.Rounded.Traffic, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun PlaceRow(d: Destination, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { if (d.detail.isNotBlank()) Text(d.detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    )
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Umbrella, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Digite o endereço de destino e eu vejo se vai chover em algum trecho, " +
                "na hora em que você passar por ele — e qual o melhor horário para sair.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Summary(state: TripState, plan: TripPlan) {
    val s = summarizeTrip(plan, state.wetPlaces[state.selected])
    val bg = when {
        plan.unknown -> TripColors.unknown
        else -> when (s.intensity) {
            Intensity.NONE -> TripColors.dry
            Intensity.LIGHT -> TripColors.light
            Intensity.MODERATE -> TripColors.moderate
            else -> TripColors.heavy
        }
    }
    val fg = if (s.intensity == Intensity.LIGHT) Color(0xFF3B2F00) else Color.White
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).padding(20.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        plan.unknown -> Icons.Rounded.HelpOutline
                        plan.dry -> Icons.Rounded.WbSunny
                        else -> Icons.Rounded.Umbrella
                    },
                    null, tint = fg, modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(s.headline, style = MaterialTheme.typography.headlineSmall, color = fg, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(s.detail, style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.92f))
            state.destination?.let {
                Text("Para ${it.name}", style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f))
            }
            val best = state.plans[state.best]
            if (state.best != 0 && state.selected == 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "💡 Saindo às ${clock.format(best.departure)} " +
                        (if (best.dry) "o caminho fica seco" else "você pega menos chuva"),
                    style = MaterialTheme.typography.titleSmall,
                    color = fg,
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(TripColors.dry to "Seco", TripColors.light to "Fraca", TripColors.moderate to "Moderada", TripColors.heavy to "Forte")
            .forEach { (c, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
    }
}

@Composable
private fun Departures(state: TripState, onSelect: (Int) -> Unit) {
    Column {
        Text("Quando sair", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.plans.forEachIndexed { i, p ->
                val selected = i == state.selected
                Card(
                    onClick = { onSelect(i) },
                    shape = RoundedCornerShape(18.dp),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val d = DEPARTURES[i]
                        Text(
                            when (d) { 0 -> "Agora"; 60 -> "+1 h"; else -> "+$d min" },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(clock.format(p.departure), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Icon(
                            when {
                                p.unknown -> Icons.Rounded.HelpOutline
                                p.dry -> Icons.Rounded.WbSunny
                                else -> Icons.Rounded.Umbrella
                            },
                            null,
                            tint = if (p.unknown) TripColors.unknown else TripColors.of(p.maxRate),
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            when {
                                p.unknown -> "sem dados"
                                p.dry -> "Seco"
                                else -> "${p.wetMinutes.roundToInt().coerceAtLeast(1)} min"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (i == state.best && state.best != 0) {
                            Text("melhor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** Barra do tempo de viagem, pintada pela chuva em cada trecho. */
@Composable
private fun Timeline(plan: TripPlan) {
    val total = plan.points.lastOrNull()?.sample?.offsetMin ?: return
    if (total <= 0) return
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
            plan.points.zipWithNext().forEach { (a, b) ->
                val x0 = (a.sample.offsetMin / total * size.width).toFloat()
                val x1 = (b.sample.offsetMin / total * size.width).toFloat()
                drawRect(TripColors.of(a), Offset(x0, 0f), Size(x1 - x0 + 1f, size.height))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saída ${clock.format(plan.departure)}", style = MaterialTheme.typography.labelSmall)
            Text("~${total.roundToInt()} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Chegada ${clock.format(plan.arrival)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
