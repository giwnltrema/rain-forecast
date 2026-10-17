package com.giwnltrema.chuva

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.giwnltrema.chuva.data.AlertSettings
import com.giwnltrema.chuva.data.AlertStore
import com.giwnltrema.chuva.data.Destination
import com.giwnltrema.chuva.data.Forecast
import com.giwnltrema.chuva.data.ForecastGrid
import com.giwnltrema.chuva.data.LocationProvider
import com.giwnltrema.chuva.data.Nowcaster
import com.giwnltrema.chuva.data.Place
import com.giwnltrema.chuva.data.RadarArea
import com.giwnltrema.chuva.data.RadarNow
import com.giwnltrema.chuva.data.Route
import com.giwnltrema.chuva.data.TravelMode
import com.giwnltrema.chuva.data.TripPlan
import com.giwnltrema.chuva.data.TripRepository
import com.giwnltrema.chuva.data.TripStore
import com.giwnltrema.chuva.data.WeatherRepository
import com.giwnltrema.chuva.data.bestPlan
import com.giwnltrema.chuva.data.distanceKm
import com.giwnltrema.chuva.data.planTrip
import com.giwnltrema.chuva.data.sampleRoute
import com.giwnltrema.chuva.notify.RainCheckWorker
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeState(
    val place: Place? = null,
    val forecast: Forecast? = null,
    /** Radar observado em volta de você (null = indisponível). */
    val radar: RadarNow? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class MapState(
    /** Radar já limpo (sem clutter) numa área de ~150 km em volta de você. */
    val radar: RadarArea? = null,
    val grid: ForecastGrid? = null,
    val loadingRadar: Boolean = false,
    val loadingGrid: Boolean = false,
    val error: String? = null,
)

data class TripState(
    /** De onde a busca/rota parte (para mostrar a distância dos resultados). */
    val origin: Pair<Double, Double>? = null,
    val query: String = "",
    val suggestions: List<Destination> = emptyList(),
    val recents: List<Destination> = emptyList(),
    val destination: Destination? = null,
    val mode: TravelMode = TravelMode.CAR,
    val heavyTraffic: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val route: Route? = null,
    /** Um plano por horário de saída (agora, +15, +30, +60 min). */
    val plans: List<TripPlan> = emptyList(),
    val best: Int = 0,
    val selected: Int = 0,
    /** Bairro onde a chuva começa no plano selecionado. */
    val wetPlaces: Map<Int, String> = emptyMap(),
)

val DEPARTURES = listOf(0, 15, 30, 60)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WeatherRepository()
    private val locator = LocationProvider(app)
    private val alertStore = AlertStore(app)
    private val tripRepo = TripRepository()
    private val tripStore = TripStore(app)

    private val _trip = MutableStateFlow(
        TripState(recents = tripStore.recents, mode = tripStore.mode, heavyTraffic = tripStore.heavyTraffic),
    )
    val trip: StateFlow<TripState> = _trip.asStateFlow()
    private var searchJob: Job? = null
    private var tripJob: Job? = null

    private val _alerts = MutableStateFlow(alertStore.settings)
    val alerts: StateFlow<AlertSettings> = _alerts.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _map = MutableStateFlow(MapState())
    val map: StateFlow<MapState> = _map.asStateFlow()

    private var homeJob: Job? = null
    private var radarFetchedAt: Instant? = null
    private var radarCenter: Pair<Double, Double>? = null

    /**
     * Abre rápido: mostra na hora a última previsão salva e atualiza por trás. Previsão, nome do
     * bairro e radar chegam cada um no seu tempo — a tela não espera o mais lento.
     */
    fun refresh() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            if (_home.value.forecast == null) {
                alertStore.loadForecast()?.let { cached ->
                    runCatching { repo.parseForecast(cached.body, cached.fetchedAt) }.onSuccess { f ->
                        _home.update { it.copy(place = cached.place, forecast = f) }
                    }
                }
            }
            _home.update { it.copy(loading = true, error = null) }
            try {
                val located = locator.currentPlace()
                // Nome provisório: o último conhecido se ainda estiver perto (< 1 km).
                val known = alertStore.lastPlace
                val place = if (!located.isFallback && known != null &&
                    distanceKm(known.lat, known.lon, located.lat, located.lon) < 1.0
                ) located.copy(name = known.name) else located
                _home.update { it.copy(place = place) }

                coroutineScope {
                    if (!place.isFallback) launch {
                        locator.reverseGeocode(place.lat, place.lon)?.let { name ->
                            val named = place.copy(name = name)
                            alertStore.lastPlace = named
                            _home.update { it.copy(place = named) }
                        } ?: run { alertStore.lastPlace = place }
                    }
                    launch {
                        val radar = repo.radarNow(place.lat, place.lon, clutter = alertStore)
                        _home.update { it.copy(radar = radar) }
                    }
                    val now = Instant.now()
                    val body = repo.forecastRaw(place.lat, place.lon)
                    val forecast = repo.parseForecast(body, now)
                    _home.update { it.copy(forecast = forecast, loading = false) }
                    alertStore.saveForecast(_home.value.place ?: place, body, now)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _home.update { it.copy(loading = false, error = e.friendly()) }
            }
        }
    }

    /** Recarrega o radar se tiver mais de 5 min (o RainViewer atualiza a cada 10). */
    fun loadRadar(lat: Double, lon: Double, force: Boolean = false) {
        val last = radarFetchedAt
        val center = radarCenter
        val sameArea = center != null && abs(center.first - lat) < 0.05 && abs(center.second - lon) < 0.05
        if (!force && sameArea && last != null && Duration.between(last, Instant.now()).toMinutes() < 5) return
        if (_map.value.loadingRadar) return
        viewModelScope.launch {
            _map.update { it.copy(loadingRadar = true, error = null) }
            runCatching { repo.radarArea(lat, lon) }
                .onSuccess { area ->
                    radarFetchedAt = Instant.now()
                    radarCenter = lat to lon
                    _map.update { it.copy(radar = area, loadingRadar = false) }
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

    // --- Trajeto ---

    fun onTripQuery(q: String) {
        _trip.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.trim().length < 3) {
            _trip.update { it.copy(suggestions = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // espera parar de digitar
            val near = _home.value.place ?: LocationProvider.SAO_PAULO
            _trip.update { it.copy(origin = near.lat to near.lon) }
            runCatching {
                tripRepo.search(q.trim(), near.lat, near.lon) { name -> locator.searchByName(name, near.lat, near.lon) }
            }
                .onSuccess { list -> _trip.update { it.copy(suggestions = list) } }
        }
    }

    fun chooseDestination(d: Destination) {
        tripStore.remember(d)
        _trip.update { it.copy(destination = d, query = d.name, suggestions = emptyList(), recents = tripStore.recents) }
        recomputeTrip()
    }

    fun setTravelMode(m: TravelMode) {
        tripStore.mode = m
        _trip.update { it.copy(mode = m) }
        recomputeTrip()
    }

    fun setHeavyTraffic(on: Boolean) {
        tripStore.heavyTraffic = on
        _trip.update { it.copy(heavyTraffic = on) }
        recomputeTrip()
    }

    fun selectDeparture(i: Int) = _trip.update { it.copy(selected = i) }

    fun clearTrip() {
        tripJob?.cancel()
        _trip.update { TripState(recents = it.recents, mode = it.mode, heavyTraffic = it.heavyTraffic) }
    }

    fun recomputeTrip() {
        val dest = _trip.value.destination ?: return
        tripJob?.cancel()
        tripJob = viewModelScope.launch {
            _trip.update { it.copy(loading = true, error = null) }
            try {
                val from = _home.value.place?.takeUnless { it.isFallback } ?: locator.currentPlace()
                val t = _trip.value
                val route = tripRepo.route(from.lat, from.lon, dest, t.mode)
                // Carro em trânsito pesado de SP: ~1,8x o tempo "de rua vazia" do OSRM.
                val traffic = if (t.heavyTraffic && t.mode == TravelMode.CAR) 1.8 else 1.0
                val samples = sampleRoute(route, traffic)
                coroutineScope {
                    val area = async { freshRadarArea(from.lat, from.lon) }
                    val model = async { runCatching { tripRepo.modelAlong(samples) }.getOrNull() }
                    val now = Instant.now()
                    val caster = area.await()?.let { withContext(Dispatchers.Default) { Nowcaster.from(it, now) } }
                    val modelFn = model.await() ?: { _, _ -> null }
                    val plans = DEPARTURES.map { d -> planTrip(samples, d, now, caster?.let { it::dbzAt }, modelFn) }
                    val best = bestPlan(plans)
                    _trip.update { it.copy(loading = false, route = route, plans = plans, best = best, selected = best, wetPlaces = emptyMap()) }
                    // Nome do bairro onde começa a chuva (um por plano com chuva).
                    plans.forEachIndexed { i, p ->
                        val w = p.firstWet ?: return@forEachIndexed
                        locator.reverseGeocode(w.sample.lat, w.sample.lon)?.substringBefore(",")?.let { name ->
                            _trip.update { it.copy(wetPlaces = it.wetPlaces + (i to name)) }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _trip.update { it.copy(loading = false, error = e.friendly()) }
            }
        }
    }

    /** Reaproveita o radar do mapa se for recente e cobrir a saída; senão baixa. */
    private suspend fun freshRadarArea(lat: Double, lon: Double): RadarArea? {
        val area = _map.value.radar
        val last = area?.times?.lastOrNull()
        val covers = area != null && lat in area.south..area.north && lon in area.west..area.east
        if (area != null && covers && last != null && Duration.between(last, Instant.now()).toMinutes() < 20) return area
        return runCatching { repo.radarArea(lat, lon) }.getOrNull()
            ?.also { fresh -> _map.update { it.copy(radar = fresh) }; radarFetchedAt = Instant.now(); radarCenter = lat to lon }
    }

    private fun Throwable.friendly(): String = when (this) {
        is java.net.UnknownHostException -> "Sem conexão com a internet"
        is java.net.SocketTimeoutException -> "O servidor demorou a responder"
        else -> message ?: "Erro inesperado"
    }
}
