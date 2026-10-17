package com.giwnltrema.chuva.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.giwnltrema.chuva.TripState
import com.giwnltrema.chuva.data.Destination
import com.giwnltrema.chuva.data.Route
import com.giwnltrema.chuva.data.TravelMode
import com.giwnltrema.chuva.data.planTrip
import com.giwnltrema.chuva.data.sampleRoute
import com.giwnltrema.chuva.ui.theme.ChuvaTheme
import com.giwnltrema.chuva.ui.trip.Departures
import com.giwnltrema.chuva.ui.trip.Legend
import com.giwnltrema.chuva.ui.trip.ModeRow
import com.giwnltrema.chuva.ui.trip.Summary
import com.giwnltrema.chuva.ui.trip.Timeline
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** Gera imagens da tela de Trajeto em tamanhos diferentes (não é teste de comportamento). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TripLayoutPreview {
    @get:Rule val compose = createComposeRule()

    private fun state(): TripState {
        val n = 33
        val route = Route(DoubleArray(n) { -23.58 - it * 0.003 }, DoubleArray(n) { -46.68 }, DoubleArray(n) { it * 60.0 }, DoubleArray(n) { it * 400.0 })
        val samples = sampleRoute(route)
        val now = Instant.parse("2026-10-17T14:44:00Z")
        val rain: (Int, Instant) -> Double? = { _, at -> if ((at.epochSecond - now.epochSecond) / 60 in 10..20) 3.0 else 0.0 }
        val plans = listOf(0, 15, 30, 60).map { planTrip(samples, it, now, null, rain) }
        return TripState(
            query = "Rua Alexandre Gantus", destination = Destination("Rua Alexandre Gantus", "Santo André", -23.66, -46.52),
            mode = TravelMode.CAR, heavyTraffic = true, route = route, plans = plans, best = 3, selected = 0,
            wetPlaces = mapOf(0 to "Vila Mariana"),
        )
    }

    private fun shoot(name: String) {
        val s = state()
        compose.setContent {
            ChuvaTheme(darkTheme = true, dynamicColor = false) {
                Surface { Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModeRow(s, {}, {})
                    Summary(s, s.plans[0])
                    Departures(s) {}
                    Timeline(s.plans[0])
                    Legend()
                } }
            }
        }
        compose.onRoot().captureRoboImage("build/previews/$name.png")
    }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun estreito() = shoot("1_estreito_360dp")
    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi") fun normal() = shoot("2_normal_411dp")
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun fonteGrande() {
        RuntimeEnvironment.setFontScale(1.3f)
        shoot("3_estreito_fonte_grande")
    }
}
