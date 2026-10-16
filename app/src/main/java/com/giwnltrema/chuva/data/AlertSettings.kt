package com.giwnltrema.chuva.data

import android.content.Context
import java.time.LocalDate

data class AlertSettings(
    /** Avisar quando a chuva estiver para começar. */
    val rainAlerts: Boolean = true,
    /** Antecedência do aviso, em minutos. */
    val leadMinutes: Int = 30,
    /** Não avisar entre 22h e 7h. */
    val quietNight: Boolean = true,
    /** Notificação de manhã com o resumo do dia. */
    val morningSummary: Boolean = true,
) {
    val anyEnabled get() = rainAlerts || morningSummary
}

/** Preferências + estado dos alertas (SharedPreferences: simples e síncrono, bom para o worker). */
class AlertStore(context: Context) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    var settings: AlertSettings
        get() = AlertSettings(
            rainAlerts = prefs.getBoolean("rain", true),
            leadMinutes = prefs.getInt("lead", 30),
            quietNight = prefs.getBoolean("quiet", true),
            morningSummary = prefs.getBoolean("morning", true),
        )
        set(s) = prefs.edit()
            .putBoolean("rain", s.rainAlerts)
            .putInt("lead", s.leadMinutes)
            .putBoolean("quiet", s.quietNight)
            .putBoolean("morning", s.morningSummary)
            .apply()

    /** Última localização vista com o app aberto (fallback quando o GPS não responde em segundo plano). */
    var lastPlace: Place?
        get() {
            if (!prefs.contains("lat")) return null
            return Place(
                lat = prefs.getFloat("lat", 0f).toDouble(),
                lon = prefs.getFloat("lon", 0f).toDouble(),
                name = prefs.getString("name", null) ?: "sua região",
            )
        }
        set(p) {
            if (p == null || p.isFallback) return
            prefs.edit()
                .putFloat("lat", p.lat.toFloat())
                .putFloat("lon", p.lon.toFloat())
                .putString("name", p.name)
                .apply()
        }

    /** Já avisamos sobre o episódio de chuva atual? Volta a false quando a próxima hora fica seca. */
    var episodeNotified: Boolean
        get() = prefs.getBoolean("episode", false)
        set(v) = prefs.edit().putBoolean("episode", v).apply()

    var lastMorning: LocalDate?
        get() = prefs.getString("morningDate", null)?.let(LocalDate::parse)
        set(v) = prefs.edit().putString("morningDate", v?.toString()).apply()
}
