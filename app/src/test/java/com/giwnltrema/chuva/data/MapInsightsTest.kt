package com.giwnltrema.chuva.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.floor

class MapInsightsTest {

    private val lat = -23.5667
    private val lon = -46.66

    @Test
    fun compassDirections() {
        assertEquals("norte", compass(lat, lon, lat + 0.5, lon))
        assertEquals("sul", compass(lat, lon, lat - 0.5, lon))
        assertEquals("leste", compass(lat, lon, lat, lon + 0.5))
        assertEquals("oeste", compass(lat, lon, lat, lon - 0.5))
        assertEquals("noroeste", compass(lat, lon, lat + 0.4, lon - 0.4)) // Campinas fica a noroeste de SP
    }

    private fun area(vararg rainAt: Pair<Int, Int>): RadarArea {
        val p = toGlobalPx(lat, lon)
        val size = 101
        val x0 = floor(p.x).toInt() - 50
        val y0 = floor(p.y).toInt() - 50
        val g = IntArray(size * size) { Int.MIN_VALUE }
        for ((dx, dy) in rainAt) g[(50 + dy) * size + (50 + dx)] = 35
        return RadarArea(x0, y0, 0.0, 0.0, 0.0, 0.0, size, listOf(Instant.EPOCH), listOf(g))
    }

    @Test
    fun nearestRainHasDistanceDirectionAndIntensity() {
        val kmPerPx = metersPerPixel(lat) / 1000
        val n = nearestRain(area(-20 to -20, 40 to 40), 0, lat, lon)!!
        assertEquals(20 * 1.4142 * kmPerPx, n.km, 1.5)
        assertEquals("noroeste", n.direction)
        assertEquals(35, n.dbz)
        assertNull(nearestRain(area(), 0, lat, lon))
    }

    @Test
    fun radarPointLookup() {
        val a = area(0 to 0)
        assertEquals(35, a.dbzAt(lat, lon, 0))
        assertEquals(Int.MIN_VALUE, a.dbzAt(lat + 0.2, lon, 0))
        assertNull(a.dbzAt(lat + 5, lon, 0)) // fora da área
        assertEquals(1.0 / (101 * 101), radarActivity(a).single(), 1e-12)
    }

    private val t0 = Instant.parse("2026-10-17T15:00:00Z")

    private fun grid(vararg hourly: Double) = ForecastGrid(
        north = -23.0, south = -24.0, west = -47.0, east = -46.0, rows = 2, cols = 2,
        times = hourly.indices.map { t0.plusSeconds(it * 3600L) },
        values = hourly.map { v -> arrayOf(doubleArrayOf(0.0, v), doubleArrayOf(0.0, 0.0)) },
    )

    @Test
    fun forecastValueIsInterpolated() {
        val g = grid(4.0)
        assertEquals(4.0, g.valueAt(-23.0, -46.0, 0)!!, 1e-9) // canto nordeste
        assertEquals(1.0, g.valueAt(-23.5, -46.5, 0)!!, 1e-9) // centro: média dos 4 cantos
        assertNull(g.valueAt(-22.0, -46.5, 0))
    }

    @Test
    fun firstRainyHourSkipsPastAndLightRain() {
        val g = grid(5.0, 0.5, 0.0, 3.0, 8.0)
        assertEquals(listOf(5.0, 0.5, 0.0, 3.0, 8.0), forecastActivity(g))
        // às 16h10 a hora das 15h já passou; a das 16h tem só 0,5 mm/h → primeira relevante é 18h
        assertEquals(3, firstRainyHour(g, t0.plusSeconds(70 * 60)))
        assertNull(firstRainyHour(grid(0.0, 0.3), t0))
    }

    @Test
    fun savedPlacesRoundTripAndOrder() {
        val work = SavedPlace(name = "Pinheiros", kind = PlaceKind.WORK, lat = -23.56, lon = -46.69)
        val other = SavedPlace(name = "Academia", kind = PlaceKind.OTHER, lat = -23.58, lon = -46.68)
        val home = SavedPlace(name = "Casa", kind = PlaceKind.HOME, lat = -23.59, lon = -46.67)
        val back = decodePlaces(encodePlaces(listOf(other, work, home)))
        assertEquals(listOf(home, work, other), sortPlaces(back))
        assertTrue(decodePlaces("lixo").isEmpty())
        assertTrue(decodePlaces(null).isEmpty())
    }
}
