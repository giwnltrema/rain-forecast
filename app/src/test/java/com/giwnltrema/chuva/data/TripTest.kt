package com.giwnltrema.chuva.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.floor

class TripTest {

    private val now = Instant.parse("2026-10-16T17:00:00Z") // 14h em SP
    private val sp = ZoneOffset.ofHours(-3)

    // --- movimento do radar ---

    private fun blob(size: Int, cx: Int, cy: Int, r: Int = 5, dbz: Int = 35) = IntArray(size * size) { k ->
        val i = k % size
        val j = k / size
        if ((i - cx) * (i - cx) + (j - cy) * (j - cy) <= r * r) dbz else Int.MIN_VALUE
    }

    @Test
    fun motionFollowsTheRain() {
        val size = 80
        // anda 9 px para leste e 3 para o sul em 30 min
        val m = estimateMotion(blob(size, 30, 40), blob(size, 39, 43), size, 30.0)!!
        assertEquals(0.3, m.dxPerMin, 1e-9)
        assertEquals(0.1, m.dyPerMin, 1e-9)
    }

    @Test
    fun noMotionWithoutRain() {
        val empty = IntArray(80 * 80) { Int.MIN_VALUE }
        assertNull(estimateMotion(empty, empty, 80, 30.0))
    }

    @Test
    fun nowcastMovesRainOntoTheRoute() {
        val lat = -23.5667
        val lon = -46.66
        val p = toGlobalPx(lat, lon)
        val size = 101
        val x0 = floor(p.x).toInt() - 50
        val y0 = floor(p.y).toInt() - 50
        // chuva 10 px a oeste do ponto, andando 1 px/2 min para leste → chega em ~20 min
        val area = RadarArea(
            x0, y0, 0.0, 0.0, 0.0, 0.0, size,
            times = listOf(now.minusSeconds(1800), now),
            grids = listOf(blob(size, 25, 50, r = 4), blob(size, 40, 50, r = 4)),
        )
        val caster = Nowcaster.from(area, now)
        assertNotNull(caster.motion)
        assertEquals(Int.MIN_VALUE, caster.dbzAt(lat, lon, 0.0)) // seco agora
        assertEquals(35, caster.dbzAt(lat, lon, 20.0)) // chuva chegando
        assertNull(caster.dbzAt(lat + 5, lon, 0.0)) // fora da área do radar
        // com o quadro 10 min atrasado, a chuva chega 10 min "antes" a partir de agora
        val late = Nowcaster.from(area, now.plusSeconds(600))
        assertEquals(35, late.dbzAt(lat, lon, 10.0))
    }

    // --- rota e plano ---

    private fun straightRoute(minutes: Int): Route {
        val n = minutes + 1
        return Route(
            lats = DoubleArray(n) { -23.56 - it * 0.005 },
            lons = DoubleArray(n) { -46.66 },
            seconds = DoubleArray(n) { it * 60.0 },
            meters = DoubleArray(n) { it * 550.0 },
        )
    }

    @Test
    fun samplingCoversTheTripAndAppliesTraffic() {
        val s = sampleRoute(straightRoute(20), traffic = 1.5, stepMin = 1.5)
        assertEquals(0.0, s.first().offsetMin, 1e-9)
        assertEquals(30.0, s.last().offsetMin, 1e-9) // 20 min x 1,5
        assertEquals(20, s.last().routeIndex)
        assertTrue(s.zipWithNext().all { (a, b) -> b.offsetMin - a.offsetMin >= 1.5 - 1e-9 || b == s.last() })
    }

    /** Chuva moderada entre 14h10 e 14h25 em qualquer lugar (modelo). */
    private val rainWindow: (Int, Instant) -> Double? = { _, at ->
        val m = (at.epochSecond - now.epochSecond) / 60
        if (m in 10 until 25) 4.0 else 0.0
    }

    @Test
    fun planUsesTimeOfPassage() {
        val samples = sampleRoute(straightRoute(20))
        val leaveNow = planTrip(samples, 0, now, radar = null, model = rainWindow)
        assertFalse(leaveNow.dry)
        // a chuva começa às 14h10: o primeiro trecho molhado é o que você passa nesse horário
        assertEquals(10.0, leaveNow.firstWet!!.sample.offsetMin, 1.5)
        val later = planTrip(samples, 30, now, radar = null, model = rainWindow)
        assertTrue(later.dry) // sai 14h30, chuva já passou
        assertEquals(3, bestPlan(listOf(leaveNow, planTrip(samples, 15, now, null, rainWindow), planTrip(samples, 20, now, null, rainWindow), later)))
    }

    @Test
    fun radarWinsOverModelInTheShortTerm() {
        val samples = sampleRoute(straightRoute(10))
        val dryRadar: (Double, Double, Double) -> Int? = { _, _, _ -> Int.MIN_VALUE }
        val plan = planTrip(samples, 0, now, dryRadar) { _, _ -> 5.0 } // modelo diz chuva, radar diz seco
        assertTrue(plan.dry)
        assertTrue(plan.points.all { it.source == RainSource.RADAR })
        // além do horizonte do radar, vale o modelo
        val late = planTrip(samples, 60, now, dryRadar) { _, _ -> 5.0 }
        assertTrue(late.points.all { it.source == RainSource.MODEL && it.wet })
    }

    @Test
    fun dontWaitForTinyGains() {
        val samples = sampleRoute(straightRoute(10))
        val a = planTrip(samples, 0, now, null) { _, _ -> 0.6 }
        val b = planTrip(samples, 15, now, null) { _, _ -> 0.55 }
        assertEquals(0, bestPlan(listOf(a, b)))
    }

    @Test
    fun summaries() {
        val samples = sampleRoute(straightRoute(20))
        val dry = planTrip(samples, 0, now, null) { _, _ -> 0.0 }
        assertEquals("Caminho seco", summarizeTrip(dry, zone = sp).headline)
        val wet = planTrip(samples, 0, now, null, rainWindow)
        val s = summarizeTrip(wet, "Pinheiros", sp)
        // trajeto de 20 min saindo 14h: pega a chuva das 14h10 até chegar (14h20)
        assertEquals("Chuva moderada em ~10 min do trajeto", s.headline)
        assertTrue(s.detail, s.detail.startsWith("A partir de ~14h1") && s.detail.contains("perto de Pinheiros"))
    }

    @Test
    fun missingDataIsNeverDry() {
        val samples = sampleRoute(straightRoute(10))
        val noData = planTrip(samples, 60, now, null) { _, _ -> null }
        assertTrue(noData.unknown)
        assertFalse(noData.dry)
        assertEquals("Sem previsão para esse horário", summarizeTrip(noData, zone = sp).headline)
        // e não pode ser escolhido como "melhor horário"
        val wet = planTrip(samples, 0, now, null) { _, _ -> 3.0 }
        assertEquals(0, bestPlan(listOf(wet, noData)))
    }

    // --- busca de endereço ---

    @Test
    fun parsesPhoton() {
        val body = """{"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{"name":"Shopping Ibirapuera","street":"Avenida Ibirapuera","housenumber":"3103",
             "district":"Moema","city":"São Paulo"},"geometry":{"type":"Point","coordinates":[-46.6668742,-23.6101927]}},
            {"type":"Feature","properties":{"street":"Rua Augusta","housenumber":"1500","district":"Consolação","city":"São Paulo"},
             "geometry":{"type":"Point","coordinates":[-46.66,-23.56]}}]}"""
        val list = Json { ignoreUnknownKeys = true }.decodeFromString<PhotonResponse>(body).features.mapNotNull { it.toDestination() }
        assertEquals("Shopping Ibirapuera", list[0].name)
        assertEquals("Avenida Ibirapuera, 3103 · Moema · São Paulo", list[0].detail)
        assertEquals(-23.6101927, list[0].lat, 1e-9)
        assertEquals("Rua Augusta, 1500", list[1].name)
        assertEquals("Consolação · São Paulo", list[1].detail)
    }

    @Test
    fun genericWordsAreDropped() {
        assertEquals("c6", withoutGenericWords("c6 bank"))
        assertEquals("Itaú Faria Lima", withoutGenericWords("agência Itaú Faria Lima"))
        assertEquals("nove de julho", withoutGenericWords("nove de julho"))
    }

    @Test
    fun resultsAreDedupedAndSortedByDistance() {
        val me = -23.585 to -46.68
        val far = Destination("C6", "Guarulhos", -23.425, -46.483)
        val near = Destination("C6", "Av. Nove de Julho · Jardim Paulista", -23.5669, -46.6586)
        val sameAsNear = Destination("C6", "Avenida Nove de Julho · Jardins", -23.5668, -46.6585)
        val list = rankResults(listOf(far, near, sameAsNear), "c6 bank", me.first, me.second)
        assertEquals(listOf(near, far), list)
    }

    @Test
    fun moreMatchingWordsBeatsDistance() {
        val me = -23.585 to -46.68
        val street1 = Destination("Avenida Nove de Julho", "Vila Olímpia · São Paulo", -23.590, -46.680)
        val street2 = Destination("Avenida Nove de Julho", "Vila Olímpia · São Paulo", -23.591, -46.681) // outro trecho
        val c6 = Destination("C6", "Avenida Nove de Julho · Jardim Paulista · São Paulo", -23.5669, -46.6586)
        val list = rankResults(listOf(street1, street2, c6), "nove de julho c6", me.first, me.second)
        assertEquals(listOf(c6, street1), list)
    }

    @Test
    fun parsesNominatim() {
        val body = """[{"name":"C6","lat":"-23.5668732","lon":"-46.6585552","address":{"amenity":"C6",
            "road":"Avenida Nove de Julho","suburb":"Jardins","city":"São Paulo","country":"Brasil"}}]"""
        val d = Json { ignoreUnknownKeys = true }.decodeFromString<List<NominatimPlace>>(body).single().toDestination()!!
        assertEquals("C6", d.name)
        assertEquals("Avenida Nove de Julho · Jardins · São Paulo", d.detail)
        assertEquals(-23.5668732, d.lat, 1e-9)
    }
}
