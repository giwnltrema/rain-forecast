package com.giwnltrema.chuva.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class RainSummaryTest {

    private val now = Instant.parse("2026-10-16T17:05:00Z") // 14h05 em SP
    private val sp = ZoneOffset.ofHours(-3)

    private fun forecast(
        nowcast: List<Double>,
        hourly: List<Pair<Double, Int?>> = List(24) { 0.0 to 0 },
        currentMm: Double = 0.0,
    ): Forecast {
        val q0 = Instant.parse("2026-10-16T17:00:00Z")
        val h0 = Instant.parse("2026-10-16T17:00:00Z")
        return Forecast(
            current = Current(22.0, 23.0, currentMm, 3, true),
            nowcast = nowcast.mapIndexed { i, v -> NowcastSlot(q0.plusSeconds(i * 900L), v) },
            hourly = hourly.mapIndexed { i, (mm, p) -> HourSlot(h0.plusSeconds(i * 3600L), mm, p, 22.0, 3, true) },
            daily = emptyList(),
            offset = sp,
            fetchedAt = now,
        )
    }

    @Test
    fun dry() {
        val s = summarize(forecast(List(12) { 0.0 }), now = now)
        assertEquals("Sem chuva nas próximas 24 h", s.headline)
        assertEquals(Intensity.NONE, s.level)
    }

    @Test
    fun rainStartsSoon() {
        // começa no slot das 14h45 → ~40 min
        val s = summarize(forecast(listOf(0.0, 0.0, 0.0, 0.8, 1.2, 0.4) + List(6) { 0.0 }), now = now)
        assertEquals("Chuva em ~40 min", s.headline)
        assertEquals(Intensity.MODERATE, s.level)
    }

    @Test
    fun rainingNowAndStopping() {
        val s = summarize(forecast(listOf(0.5, 0.3, 0.0) + List(9) { 0.0 }, currentMm = 1.0), now = now)
        assertEquals("Chovendo agora", s.headline)
        assertTrue(s.detail, s.detail.startsWith("Deve parar em ~25 min"))
    }

    @Test
    fun rainLaterToday() {
        val hourly = List(24) { i -> if (i == 5) 3.0 to 80 else 0.0 to 10 }
        val s = summarize(forecast(List(12) { 0.0 }, hourly), now = now)
        assertEquals("Chuva às 19h00 (80%)", s.headline)
    }

    @Test
    fun mmFormatting() {
        assertEquals("0 mm", mm(0.0))
        assertEquals("<0,1 mm", mm(0.04))
        assertEquals("2,5 mm", mm(2.5))
        assertEquals("12 mm", mm(12.3))
    }
}
