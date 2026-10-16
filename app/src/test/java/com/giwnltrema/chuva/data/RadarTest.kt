package com.giwnltrema.chuva.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.floor

class RadarTest {

    private val lat = -23.5667
    private val lon = -46.66
    private val c = toGlobalPx(lat, lon)
    private val cx = floor(c.x).toInt()
    private val cy = floor(c.y).toInt()
    private val kmPerPx = metersPerPixel(lat) / 1000

    /** Quadro com blocos de chuva: (dx, dy, tamanho, dBZ) relativos ao ponto. */
    private fun frame(vararg blocks: IntArray): FrameReader = { x, y ->
        blocks.firstOrNull { (dx, dy, size) -> x - cx in dx until dx + size && y - cy in dy until dy + size }?.get(3)
    }

    private fun block(dx: Int, dy: Int, size: Int, dbz: Int) = intArrayOf(dx, dy, size, dbz)

    @Test
    fun paletteDecodesExactColors() {
        assertEquals(10, RadarPalette.dbzOf(0x96CEC087.toInt()))
        assertEquals(23, RadarPalette.dbzOf(0xFF0088BF.toInt()))
        assertEquals(35, RadarPalette.dbzOf(0xFFFFEE00.toInt()))
        assertNull(RadarPalette.dbzOf(0))
        // cor levemente fora da paleta (arredondamento) → valor mais próximo
        assertEquals(23, RadarPalette.dbzOf(0xFF0189BE.toInt()))
    }

    @Test
    fun rateFromDbz() {
        assertEquals(0.65, dbzToRate(20), 0.05)
        assertEquals(2.7, dbzToRate(30), 0.1)
        assertEquals(11.5, dbzToRate(40), 0.5)
    }

    @Test
    fun staticClutterIsMaskedButMovingRainIsNot() {
        val clutter = block(-2, -2, 4, 30) // eco parado em cima do ponto (prédios)
        // chuva real vindo do oeste: 4 px por quadro
        val frames = (0 until 13).map { t -> frame(clutter, block(-40 + 4 * t, 5, 4, 32)) }
        val mask = buildClutterMask(lat, lon, frames)

        val raw = scanFrame(lat, lon, emptySet(), frames.last())
        assertTrue("sem máscara, o clutter parece chuva", (raw.dbzHere ?: 0) >= RAIN_DBZ)

        val scan = scanFrame(lat, lon, mask, frames.last())
        assertNull("clutter não é chuva aqui", scan.dbzHere)
        // a chuva real (blocos em dx 8..11, dy 5..8) continua sendo vista
        val nearest = scan.nearestKm!!
        assertEquals(kotlin.math.hypot(8.5 - (c.x - cx), 5.5 - (c.y - cy)) * kmPerPx, nearest, 0.6)
    }

    @Test
    fun realRainOverClutterIsNotErased() {
        // O caso do Itaim: eco de prédio parado a tarde toda e, no último quadro, chuva forte em volta.
        val clutter = block(-2, -2, 4, 30)
        val frames = (0 until 12).map { frame(clutter) } + frame(block(-2, -2, 4, 30), block(-30, -30, 60, 33))
        val mask = buildClutterMask(lat, lon, frames)
        // a máscara existe (o eco ficou parado)…
        assertTrue(mask.isNotEmpty())
        // …mas com chuva em volta, o ponto conta como chovendo
        val scan = scanFrame(lat, lon, mask, frames.last())
        assertTrue("dbzHere=${scan.dbzHere}", (scan.dbzHere ?: 0) >= RAIN_DBZ)
        // e com a vizinhança seca, continua sendo só eco
        assertNull(scanFrame(lat, lon, mask, frame(clutter)).dbzHere)
    }

    @Test
    fun radarDelayCountsTowardsArrival() {
        val observed = now.minusSeconds(15 * 60) // quadro de 15 min atrás
        val r = RadarNow(observed, null, 4.0, 40, etaMinutes = 15)
        assertEquals(now, r.arrivesAt) // "chega em 15 min" a partir da imagem = agora
        assertEquals("Chuva chegando a qualquer momento", summarize(wetModel, r, now).headline)
    }

    @Test
    fun isolatedPixelsAreIgnored() {
        val scan = scanFrame(lat, lon, emptySet(), frame(block(3, 3, 1, 35)))
        assertNull(scan.nearestKm)
    }

    @Test
    fun widespreadRainIsNotMistakenForClutter() {
        // chuva estratiforme cobrindo tudo por 2 h
        val frames = (0 until 13).map { frame(block(-60, -60, 120, 28)) }
        val mask = buildClutterMask(lat, lon, frames)
        assertTrue(scanFrame(lat, lon, mask, frames.last()).dbzHere!! >= RAIN_DBZ)
    }

    @Test
    fun approachingRainGivesEta() {
        val t0 = Instant.parse("2026-10-16T19:00:00Z")
        val t1 = t0.plusSeconds(30 * 60)
        val before = FrameScan(null, 20.0, 30)
        val after = FrameScan(null, 10.0, 30)
        assertEquals(30, radarNow(after, t1, before, t0).etaMinutes) // 10 km em 30 min → mais 30 min
        assertNull(radarNow(before, t1, after, t0).etaMinutes) // se afastando
        assertNull(radarNow(FrameScan(null, 10.0, 30), t1, FrameScan(null, 11.0, 30), t0).etaMinutes) // parada
    }

    // --- Radar x modelo na frase e nos alertas ---

    private val now = Instant.parse("2026-10-16T19:05:00Z")

    /** Modelo dizendo que está garoando agora (o caso do print). */
    private val wetModel = Forecast(
        current = Current(21.0, 22.0, 1.5, 53, true),
        nowcast = (0 until 12).map { NowcastSlot(Instant.parse("2026-10-16T19:00:00Z").plusSeconds(it * 900L), 0.4) },
        hourly = (0 until 24).map { HourSlot(Instant.parse("2026-10-16T19:00:00Z").plusSeconds(it * 3600L), 1.0, 95, 20.0, 53, true) },
        daily = emptyList(),
        offset = ZoneOffset.ofHours(-3),
        fetchedAt = now,
    )

    private fun radar(here: Int?, nearest: Double?, eta: Int? = null) =
        RadarNow(now.minusSeconds(300), here, nearest, if (nearest != null) 30 else null, eta)

    @Test
    fun radarOverridesModelWhenDry() {
        val s = summarize(wetModel, radar(null, null), now)
        assertEquals("Sem chuva por perto", s.headline)
        assertTrue(s.detail, s.detail.startsWith("Previsão: chuva"))
        assertNull(rainAlert(wetModel, radar(null, null), "Pinheiros", 30, now))
        assertTrue(dryAhead(wetModel, radar(null, null), 30, now))
    }

    @Test
    fun rainingPerRadar() {
        val s = summarize(wetModel, radar(33, 0.5), now)
        assertEquals("Chovendo agora", s.headline)
        assertEquals("Está chovendo em Pinheiros", rainAlert(wetModel, radar(33, 0.5), "Pinheiros", 30, now)!!.title)
    }

    @Test
    fun approachingPerRadar() {
        // radar observado há 5 min dizendo "chega em 25 min" → a partir de agora são ~20 min
        val s = summarize(wetModel, radar(null, 12.0, eta = 25), now)
        assertEquals("Chuva chegando em ~20 min", s.headline)
        val a = rainAlert(wetModel, radar(null, 12.0, eta = 25), "Pinheiros", 30, now)!!
        assertEquals("Chuva em ~20 min em Pinheiros", a.title)
        assertFalse(dryAhead(wetModel, radar(null, 12.0, eta = 25), 30, now))
    }

    @Test
    fun nearbyButNotComing() {
        assertEquals("Chuva a ~12 km", summarize(wetModel, radar(null, 12.0), now).headline)
        // 12 km parada, aviso de 30 min (raio 15 km, metade 7,5): não avisa
        assertNull(rainAlert(wetModel, radar(null, 12.0), "Pinheiros", 30, now))
        assertNotNull(rainAlert(wetModel, radar(null, 6.0), "Pinheiros", 30, now))
    }

    @Test
    fun staleRadarFallsBackToModel() {
        val old = RadarNow(now.minusSeconds(3 * 3600), null, null, null, null)
        assertEquals("Chovendo agora", summarize(wetModel, old, now).headline)
    }
}
