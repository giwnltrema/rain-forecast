package com.giwnltrema.chuva.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class Place(
    val lat: Double,
    val lon: Double,
    val name: String,
    /** true quando não temos permissão/GPS e caímos no centro de São Paulo. */
    val isFallback: Boolean = false,
)

data class Current(
    val temperature: Double?,
    val apparent: Double?,
    val precipitation: Double,
    val weatherCode: Int?,
    val isDay: Boolean,
)

/** Fatia de 15 minutos. [mm] é o acumulado no intervalo. */
data class NowcastSlot(val time: Instant, val mm: Double)

data class HourSlot(
    val time: Instant,
    val mm: Double,
    val probability: Int?,
    val temperature: Double?,
    val weatherCode: Int?,
    val isDay: Boolean,
)

data class DaySlot(
    val date: LocalDate,
    val mm: Double,
    val probability: Int?,
    val tMin: Double?,
    val tMax: Double?,
    val weatherCode: Int?,
)

data class Forecast(
    val current: Current,
    val nowcast: List<NowcastSlot>,
    val hourly: List<HourSlot>,
    val daily: List<DaySlot>,
    val offset: ZoneOffset,
    val fetchedAt: Instant,
)

data class RadarFrame(val time: Instant, val tileUrl: String)

/**
 * Grade regular de previsão em volta de um ponto.
 * values[t][row][col] em mm/h; row 0 é o norte.
 */
class ForecastGrid(
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
    val rows: Int,
    val cols: Int,
    val times: List<Instant>,
    val values: List<Array<DoubleArray>>,
)
