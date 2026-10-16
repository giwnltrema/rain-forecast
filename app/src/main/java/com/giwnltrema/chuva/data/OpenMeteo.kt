package com.giwnltrema.chuva.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Formato da resposta de https://api.open-meteo.com/v1/forecast (timeformat=unixtime).

@Serializable
internal data class OmResponse(
    val latitude: Double,
    val longitude: Double,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: OmCurrent? = null,
    @SerialName("minutely_15") val minutely15: OmSeries? = null,
    val hourly: OmHourly? = null,
    val daily: OmDaily? = null,
)

@Serializable
internal data class OmCurrent(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparent: Double? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("is_day") val isDay: Int? = null,
)

@Serializable
internal data class OmSeries(
    val time: List<Long>,
    val precipitation: List<Double?> = emptyList(),
)

@Serializable
internal data class OmHourly(
    val time: List<Long>,
    val precipitation: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val probability: List<Int?> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
)

@Serializable
internal data class OmDaily(
    val time: List<Long>,
    @SerialName("precipitation_sum") val precipitation: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val probability: List<Int?> = emptyList(),
    @SerialName("temperature_2m_min") val tMin: List<Double?> = emptyList(),
    @SerialName("temperature_2m_max") val tMax: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)

@Serializable
internal data class RvMaps(
    val host: String,
    val radar: RvRadar,
)

@Serializable
internal data class RvRadar(
    val past: List<RvFrame> = emptyList(),
    val nowcast: List<RvFrame> = emptyList(),
)

@Serializable
internal data class RvFrame(val time: Long, val path: String)
