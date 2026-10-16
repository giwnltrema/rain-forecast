package com.giwnltrema.chuva.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class RainAlertsTest {

    private val now = Instant.parse("2026-10-16T17:05:00Z") // 14h05 em SP
    private val q0 = Instant.parse("2026-10-16T17:00:00Z")

    private fun forecast(nowcast: List<Double>, hourly: List<Pair<Double, Int?>> = List(24) { 0.0 to 0 }, currentMm: Double = 0.0) =
        Forecast(
            current = Current(22.0, 23.0, currentMm, 3, true),
            nowcast = nowcast.mapIndexed { i, v -> NowcastSlot(q0.plusSeconds(i * 900L), v) },
            hourly = hourly.mapIndexed { i, (mm, p) -> HourSlot(q0.plusSeconds(i * 3600L), mm, p, 20.0 + i % 6, 3, true) },
            daily = emptyList(),
            offset = ZoneOffset.ofHours(-3),
            fetchedAt = now,
        )

    @Test
    fun noAlertWhenDry() {
        val f = forecast(List(12) { 0.0 })
        assertNull(rainAlert(f, "Pinheiros", 30, now))
        assertTrue(dryAhead(f, now))
    }

    @Test
    fun alertWithinLeadTime() {
        // começa às 14h30 → 25 min
        val f = forecast(listOf(0.0, 0.0, 0.6, 1.0, 0.2) + List(7) { 0.0 })
        val a = rainAlert(f, "Pinheiros", 30, now)!!
        assertEquals("Chuva em ~25 min em Pinheiros", a.title)
        assertEquals("Moderada · 1,8 mm na primeira hora", a.text)
        assertFalse(dryAhead(f, now))
    }

    @Test
    fun noAlertBeyondLeadTime() {
        // começa às 15h → 55 min: fora de 30 min, dentro de 1 h
        val f = forecast(List(4) { 0.0 } + listOf(0.5) + List(7) { 0.0 })
        assertNull(rainAlert(f, "Pinheiros", 30, now))
        assertEquals("Chuva em ~55 min em Pinheiros", rainAlert(f, "Pinheiros", 60, now)!!.title)
    }

    @Test
    fun rainingNow() {
        val f = forecast(listOf(0.4, 0.4) + List(10) { 0.0 }, currentMm = 1.2)
        assertEquals("Está chovendo em Pinheiros", rainAlert(f, "Pinheiros", 15, now)!!.title)
        assertFalse(dryAhead(f, now))
    }

    @Test
    fun morningRain() {
        val hourly = List(24) { i -> if (i in 2..3) 3.0 to 70 else 0.0 to 10 }
        val a = morningSummary(forecast(List(12) { 0.0 }, hourly), "Pinheiros", now)
        assertEquals("☔ Leve o guarda-chuva hoje", a.title)
        assertTrue(a.text, a.text.startsWith("Chuva a partir das 16h em Pinheiros (70%) · 6,0 mm no dia"))
    }

    @Test
    fun morningDry() {
        val a = morningSummary(forecast(List(12) { 0.0 }), "Pinheiros", now)
        assertEquals("Hoje sem chuva em Pinheiros", a.title)
    }
}
