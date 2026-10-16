package com.giwnltrema.chuva.notify

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.giwnltrema.chuva.data.AlertStore
import com.giwnltrema.chuva.data.Place
import com.giwnltrema.chuva.data.WeatherRepository
import com.giwnltrema.chuva.data.dryAhead
import com.giwnltrema.chuva.data.morningSummary
import com.giwnltrema.chuva.data.rainAlert
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Roda a cada ~15 min (mínimo do Android): busca a previsão de 15 em 15 min
 * e notifica se a chuva estiver para começar. Também manda o resumo da manhã.
 */
class RainCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = AlertStore(applicationContext)
        val settings = store.settings
        if (!settings.anyEnabled) return Result.success()

        val place = locate(store) ?: return Result.success()
        val forecast = runCatching { WeatherRepository().forecast(place.lat, place.lon) }
            .getOrElse { return Result.retry() }

        val now = Instant.now()
        val local = now.atOffset(forecast.offset)
        val localTime = local.toLocalTime()
        val today = local.toLocalDate()
        val night = localTime >= LocalTime.of(22, 0) || localTime < LocalTime.of(7, 0)

        if (settings.rainAlerts) {
            val alert = rainAlert(forecast, place.name, settings.leadMinutes, now)
            when {
                alert != null && !store.episodeNotified && !(settings.quietNight && night) -> {
                    Notifier.rain(applicationContext, alert)
                    store.episodeNotified = true
                }
                alert == null && dryAhead(forecast, now) -> store.episodeNotified = false
            }
        }

        if (settings.morningSummary &&
            localTime >= LocalTime.of(6, 30) && localTime < LocalTime.of(10, 0) &&
            store.lastMorning != today
        ) {
            Notifier.daily(applicationContext, morningSummary(forecast, place.name, now))
            store.lastMorning = today
        }
        return Result.success()
    }

    /**
     * Com "localização o tempo todo" pega a posição atual; sem ela, o Android não entrega
     * GPS em segundo plano e usamos a última posição vista com o app aberto.
     */
    @SuppressLint("MissingPermission")
    private suspend fun locate(store: AlertStore): Place? {
        val saved = store.lastPlace
        if (!hasBackgroundLocation(applicationContext)) return saved
        val fused = LocationServices.getFusedLocationProviderClient(applicationContext)
        val loc = runCatching {
            withTimeoutOrNull(20_000) {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
            } ?: fused.lastLocation.await()
        }.getOrNull() ?: return saved
        return Place(loc.latitude, loc.longitude, saved?.name ?: "sua região")
    }

    companion object {
        private const val NAME = "rain-check"

        fun hasBackgroundLocation(context: Context): Boolean {
            val fg = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return fg
            return fg && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

        /** Liga/desliga a checagem periódica conforme as preferências. */
        fun sync(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!AlertStore(context).settings.anyEnabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RainCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
