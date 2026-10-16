package com.giwnltrema.chuva.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/** Códigos WMO usados pelo Open-Meteo. */
fun weatherIcon(code: Int?, isDay: Boolean = true): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> Icons.Rounded.WbCloudy
    3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.Dehaze
    51, 53, 55, 56, 57 -> Icons.Rounded.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.Umbrella
    71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
    95, 96, 99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

fun weatherLabel(code: Int?): String = when (code) {
    0 -> "Céu limpo"
    1 -> "Predomínio de sol"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    51, 53, 55 -> "Garoa"
    56, 57 -> "Garoa congelante"
    61 -> "Chuva fraca"
    63 -> "Chuva moderada"
    65 -> "Chuva forte"
    66, 67 -> "Chuva congelante"
    71, 73, 75, 77 -> "Neve"
    80 -> "Pancadas fracas"
    81 -> "Pancadas de chuva"
    82 -> "Pancadas fortes"
    85, 86 -> "Pancadas de neve"
    95 -> "Trovoadas"
    96, 99 -> "Trovoadas com granizo"
    else -> "—"
}
