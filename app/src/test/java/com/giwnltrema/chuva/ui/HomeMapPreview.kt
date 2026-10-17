package com.giwnltrema.chuva.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.giwnltrema.chuva.HomeState
import com.giwnltrema.chuva.MapState
import com.giwnltrema.chuva.data.Current
import com.giwnltrema.chuva.data.DaySlot
import com.giwnltrema.chuva.data.Forecast
import com.giwnltrema.chuva.data.ForecastGrid
import com.giwnltrema.chuva.data.HourSlot
import com.giwnltrema.chuva.data.NowcastSlot
import com.giwnltrema.chuva.data.Place
import com.giwnltrema.chuva.data.PlaceKind
import com.giwnltrema.chuva.data.SavedPlace
import com.giwnltrema.chuva.ui.home.HomeScreen
import com.giwnltrema.chuva.ui.map.InsightCard
import com.giwnltrema.chuva.ui.map.Mode
import com.giwnltrema.chuva.ui.map.TimelineCard
import com.giwnltrema.chuva.ui.map.insight
import com.giwnltrema.chuva.ui.theme.ChuvaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class HomeMapPreview {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.now()
    private val places = listOf(
        SavedPlace("1", "Casa", PlaceKind.HOME, -23.59, -46.67),
        SavedPlace("2", "Pinheiros", PlaceKind.WORK, -23.56, -46.69),
    )

    private fun forecast() = Forecast(
        current = Current(22.0, 23.0, 0.0, 3, true),
        nowcast = (0 until 12).map { NowcastSlot(now.plusSeconds(it * 900L), if (it in 4..7) 0.6 else 0.0) },
        hourly = (0 until 24).map { HourSlot(now.plusSeconds(it * 3600L), if (it in 1..3) 2.0 else 0.0, if (it in 1..3) 80 else 10, 21.0, 3, true) },
        daily = (0 until 7).map { DaySlot(LocalDate.of(2026, 10, 17).plusDays(it.toLong()), it * 1.5, 30, 16.0, 25.0, 3) },
        offset = ZoneOffset.ofHours(-3),
        fetchedAt = now,
    )

    @Test fun previsaoComLugares() {
        compose.setContent {
            ChuvaTheme(darkTheme = true, dynamicColor = false) {
                Surface {
                    HomeScreen(
                        state = HomeState(place = Place(-23.56, -46.69, "Pinheiros"), forecast = forecast(), places = places, selected = "2"),
                        onRefresh = {}, onOpenMap = {}, onOpenAlerts = {}, alertsOn = true,
                        onSelectPlace = {}, onEditPlace = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/previews/4_previsao_lugares.png")
    }

    @Test fun previsaoSemLugares() {
        compose.setContent {
            ChuvaTheme(darkTheme = true, dynamicColor = false) {
                Surface {
                    HomeScreen(
                        state = HomeState(place = Place(-23.58, -46.68, "Itaim Bibi, São Paulo"), forecast = forecast()),
                        onRefresh = {}, onOpenMap = {}, onOpenAlerts = {}, alertsOn = true,
                        onSelectPlace = {}, onEditPlace = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/previews/5_previsao_sem_lugares.png")
    }

    @Test fun mapaCartoes() {
        val t0 = now.minusSeconds(1800)
        val hourly = listOf(0.0, 0.0, 0.3, 1.5, 4.0, 9.0, 6.0, 2.0, 0.5) + List(15) { 0.0 }
        val grid = ForecastGrid(
            -23.0, -24.0, -47.0, -46.0, 2, 2,
            times = hourly.indices.map { t0.plusSeconds(it * 3600L) },
            values = hourly.map { v -> arrayOf(doubleArrayOf(v, v * 0.5), doubleArrayOf(v * 0.3, 0.0)) },
        )
        val state = MapState(grid = grid)
        val me = Place(-23.58, -46.68, "Aqui")
        compose.setContent {
            ChuvaTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.fillMaxSize().padding(0.dp)) {
                    Surface(color = Color(0xFFEDE9E1), modifier = Modifier.fillMaxSize()) {}
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (t, sub) = insight(Mode.FORECAST, state, me, 4)
                        InsightCard(t, sub)
                        InsightCard("Neste ponto às 19h: 9,0 mm/h (moderada) · 9,0 mm na hora", null, onClose = {})
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                        TimelineCard(Mode.FORECAST, state, 4, hourly.size, false, {}, {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/previews/6_mapa_cartoes.png")
    }
}
