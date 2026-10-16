package com.giwnltrema.chuva

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.giwnltrema.chuva.data.AlertSettings
import com.giwnltrema.chuva.data.AlertStore
import com.giwnltrema.chuva.data.Forecast
import com.giwnltrema.chuva.data.ForecastGrid
import com.giwnltrema.chuva.data.LocationProvider
import com.giwnltrema.chuva.data.Place
import com.giwnltrema.chuva.data.RadarFrame
import com.giwnltrema.chuva.data.RadarNow
import com.giwnltrema.chuva.data.WeatherRepository
import com.giwnltrema.chuva.notify.RainCheckWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class HomeState(
    val place: Place? = null,
    val forecast: Forecast? = null,
    /** Radar observado em volta de você (null = indisponível). */
    val radar: RadarNow? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class MapState(
    val radar: List<RadarFrame> = emptyList(),
    val grid: ForecastGrid? = null,
    val loadingRadar: Boolean = false,
    val loadingGrid: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WeatherRepository()
    private val locator = LocationProvider(app)
    private val alertStore = AlertStore(app)

    private val _alerts = MutableStateFlow(alertStore.settings)
    val alerts: StateFlow<AlertSettings> = _alerts.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _map = MutableStateFlow(MapState())
    val map: StateFlow<MapState> = _map.asStateFlow()

    private var homeJob: Job? = null
    private var radarFetchedAt: Instant? = null

    fun refresh() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _home.update { it.copy(loading = true, error = null) }
            try {
                val place = locator.currentPlace()
                alertStore.lastPlace = place
                _home.update { it.copy(place = place) }
                coroutineScope {
                    val radar = async { repo.radarNow(place.lat, place.lon, clutter = alertStore) }
                    val forecast = repo.forecast(place.lat, place.lon)
                    _home.update { it.copy(forecast = forecast, radar = radar.await(), loading = false) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _home.update { it.copy(loading = false, error = e.friendly()) }
            }
        }
    }

    /** Recarrega o radar se tiver mais de 5 min (o RainViewer atualiza a cada 10). */
    fun loadRadar(force: Boolean = false) {
        val last = radarFetchedAt
        if (!force && last != null && Duration.between(last, Instant.now()).toMinutes() < 5) return
        viewModelScope.launch {
            _map.update { it.copy(loadingRadar = true, error = null) }
            runCatching { repo.radarFrames() }
                .onSuccess { frames ->
                    radarFetchedAt = Instant.now()
                    _map.update { it.copy(radar = frames, loadingRadar = false) }
                }
                .onFailure { e -> _map.update { it.copy(loadingRadar = false, error = e.friendly()) } }
        }
    }

    fun loadGrid(lat: Double, lon: Double) {
        viewModelScope.launch {
            _map.update { it.copy(loadingGrid = true, error = null) }
            runCatching { repo.forecastGrid(lat, lon) }
                .onSuccess { grid -> _map.update { it.copy(grid = grid, loadingGrid = false) } }
                .onFailure { e -> _map.update { it.copy(loadingGrid = false, error = e.friendly()) } }
        }
    }

    fun updateAlerts(settings: AlertSettings) {
        alertStore.settings = settings
        _alerts.value = settings
        RainCheckWorker.sync(getApplication())
    }

    private fun Throwable.friendly(): String = when (this) {
        is java.net.UnknownHostException -> "Sem conexão com a internet"
        is java.net.SocketTimeoutException -> "O servidor demorou a responder"
        else -> message ?: "Erro inesperado"
    }
}
