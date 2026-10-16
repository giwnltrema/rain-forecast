package com.giwnltrema.chuva.ui.map

import android.graphics.Bitmap
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.giwnltrema.chuva.HomeState
import com.giwnltrema.chuva.MapState
import com.giwnltrema.chuva.data.LocationProvider
import com.giwnltrema.chuva.ui.theme.RainScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Mode { RADAR, FORECAST }

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val hourOnly = DateTimeFormatter.ofPattern("HH'h'").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    home: HomeState,
    state: MapState,
    onLoadRadar: (Double, Double, Boolean) -> Unit,
    onLoadGrid: (Double, Double) -> Unit,
) {
    val place = home.place ?: LocationProvider.SAO_PAULO
    val markerColor = MaterialTheme.colorScheme.primary.toArgb()
    val statusBarPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    val density = LocalDensity.current.density

    var mode by rememberSaveable { mutableStateOf(Mode.RADAR) }
    var playing by remember { mutableStateOf(false) }
    var radarIndex by remember { mutableIntStateOf(0) }
    var forecastIndex by remember { mutableIntStateOf(0) }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var centeredOn by remember { mutableStateOf<LatLng?>(null) }

    val mapView = rememberMapViewWithLifecycle()

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
                attributionGravity = Gravity.TOP or Gravity.END
                setAttributionMargins(0, statusBarPx + (12 * density).toInt(), (12 * density).toInt(), 0)
            }
            m.setMaxZoomPreference(13.0)
            m.cameraPosition = CameraPosition.Builder().target(LatLng(place.lat, place.lon)).zoom(9.5).build()
            centeredOn = LatLng(place.lat, place.lon)
            m.setStyle(Style.Builder().fromUri(BASE_STYLE_URL)) { s ->
                s.addUserMarker(place, markerColor)
                style = s
            }
            map = m
        }
    }

    // A localização pode chegar depois do mapa abrir.
    LaunchedEffect(map, style, place) {
        val m = map ?: return@LaunchedEffect
        style?.moveUserMarker(place)
        val target = LatLng(place.lat, place.lon)
        if (centeredOn != target) {
            m.animateCamera(CameraUpdateFactory.newLatLng(target))
            centeredOn = target
        }
    }

    LaunchedEffect(place) { onLoadRadar(place.lat, place.lon, false) }

    LaunchedEffect(mode) {
        playing = false
        if (mode == Mode.FORECAST && state.grid == null && !state.loadingGrid) onLoadGrid(place.lat, place.lon)
    }

    // --- Radar ---
    // Todos os quadros já desenhados de antemão: a animação só troca a imagem.
    var radarImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(state.radar) {
        val area = state.radar
        radarImages = if (area == null) emptyList()
        else withContext(Dispatchers.Default) { area.grids.map { renderRadarGrid(it, area.size) } }
    }
    LaunchedEffect(state.radar) {
        radarIndex = ((state.radar?.times?.size ?: 0) - 1).coerceAtLeast(0)
    }
    LaunchedEffect(style, radarImages, radarIndex) {
        val s = style ?: return@LaunchedEffect
        val area = state.radar ?: return@LaunchedEffect
        val image = radarImages.getOrNull(radarIndex) ?: return@LaunchedEffect
        s.setOverlay(RADAR, Bounds(area.north, area.south, area.west, area.east), image)
        s.showOverlay(RADAR, mode == Mode.RADAR)
    }

    // --- Previsão ---
    LaunchedEffect(state.grid) {
        val grid = state.grid ?: return@LaunchedEffect
        val now = Instant.now()
        forecastIndex = grid.times.indexOfFirst { it.plusSeconds(3600).isAfter(now) }.coerceAtLeast(0)
    }
    LaunchedEffect(style, state.grid, forecastIndex) {
        val s = style ?: return@LaunchedEffect
        val grid = state.grid ?: return@LaunchedEffect
        val values = grid.values.getOrNull(forecastIndex) ?: return@LaunchedEffect
        val bitmap = withContext(Dispatchers.Default) { renderRainGrid(values) }
        s.setOverlay(FORECAST, Bounds(grid.north, grid.south, grid.west, grid.east), bitmap)
        s.showOverlay(FORECAST, mode == Mode.FORECAST)
    }
    LaunchedEffect(style, mode) {
        style?.showOverlay(RADAR, mode == Mode.RADAR)
        style?.showOverlay(FORECAST, mode == Mode.FORECAST)
    }

    val frameCount = (if (mode == Mode.RADAR) state.radar?.times?.size else state.grid?.times?.size) ?: 0

    // Animação: avança um quadro a cada ~0,6 s e pausa um pouco no final.
    LaunchedEffect(playing, mode, frameCount) {
        if (!playing || frameCount < 2) return@LaunchedEffect
        while (true) {
            val current = if (mode == Mode.RADAR) radarIndex else forecastIndex
            delay(if (current == frameCount - 1) 1500 else 600)
            val next = (current + 1) % frameCount
            if (mode == Mode.RADAR) radarIndex = next else forecastIndex = next
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.padding(4.dp)) {
                    SegmentedButton(
                        selected = mode == Mode.RADAR,
                        onClick = { mode = Mode.RADAR },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Rounded.Radar, null) },
                        label = { Text("Radar") },
                    )
                    SegmentedButton(
                        selected = mode == Mode.FORECAST,
                        onClick = { mode = Mode.FORECAST },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Rounded.Schedule, null) },
                        label = { Text("Previsão") },
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mode == Mode.FORECAST) {
                    SuggestionChip(
                        onClick = {
                            val target = map?.cameraPosition?.target ?: return@SuggestionChip
                            onLoadGrid(target.latitude, target.longitude)
                        },
                        icon = { Icon(Icons.Rounded.Refresh, null) },
                        label = { Text("Prever nesta área") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                } else {
                    SuggestionChip(
                        onClick = {
                            val target = map?.cameraPosition?.target ?: LatLng(place.lat, place.lon)
                            onLoadRadar(target.latitude, target.longitude, true)
                        },
                        icon = { Icon(Icons.Rounded.Refresh, null) },
                        label = { Text("Atualizar") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
                SmallFloatingActionButton(
                    onClick = {
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 9.0))
                    },
                ) { Icon(Icons.Rounded.MyLocation, contentDescription = "Minha localização") }
            }
            Spacer(Modifier.height(8.dp))
            TimelineCard(
                mode = mode,
                state = state,
                index = if (mode == Mode.RADAR) radarIndex else forecastIndex,
                count = frameCount,
                playing = playing,
                onPlayToggle = { playing = !playing },
                onSeek = {
                    playing = false
                    if (mode == Mode.RADAR) radarIndex = it else forecastIndex = it
                },
            )
        }
    }
}

@Composable
private fun TimelineCard(
    mode: Mode,
    state: MapState,
    index: Int,
    count: Int,
    playing: Boolean,
    onPlayToggle: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    val loading = if (mode == Mode.RADAR) state.loadingRadar else state.loadingGrid
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onPlayToggle, enabled = count > 1) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pausar" else "Animar",
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(timeLabel(mode, state, index), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (mode == Mode.RADAR) "Radar observado · últimas 2 h · ecos de prédios filtrados"
                        else "Modelo numérico · mm/h · próximas 24 h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (count > 1) {
                Slider(
                    value = index.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..(count - 1).toFloat(),
                    steps = (count - 2).coerceAtLeast(0),
                )
            }
            Legend()
            state.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun timeLabel(mode: Mode, state: MapState, index: Int): String {
    val now = Instant.now()
    return when (mode) {
        Mode.RADAR -> {
            val t = state.radar?.times?.getOrNull(index) ?: return "Carregando radar…"
            val min = Duration.between(t, now).toMinutes()
            if (min < 10) "Agora · ${clock.format(t)}" else "Há $min min · ${clock.format(t)}"
        }
        Mode.FORECAST -> {
            val t = state.grid?.times?.getOrNull(index) ?: return "Carregando previsão…"
            val end = t.plusSeconds(3600)
            val prefix = if (!t.isAfter(now) && end.isAfter(now)) "Agora" else {
                val h = Duration.between(now, t).toMinutes().let { (it + 59) / 60 }
                "Em ${h} h"
            }
            "$prefix · ${hourOnly.format(t)}–${hourOnly.format(end)}"
        }
    }
}

@Composable
private fun Legend() {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(RainScale.stops.map { it.second })),
        )
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("fraca", "moderada", "forte", "muito forte").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** MapView do MapLibre amarrado ao ciclo de vida da tela. */
@Composable
internal fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}
