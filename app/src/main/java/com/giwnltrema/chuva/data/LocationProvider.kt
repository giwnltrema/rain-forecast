package com.giwnltrema.chuva.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class LocationProvider(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    suspend fun currentPlace(): Place {
        if (!hasPermission()) return SAO_PAULO
        val location = runCatching {
            withTimeoutOrNull(10_000) {
                val cts = CancellationTokenSource()
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
            } ?: fused.lastLocation.await()
        }.getOrNull() ?: return SAO_PAULO.copy(name = "São Paulo (sem GPS)")

        val name = reverseGeocode(location.latitude, location.longitude) ?: "Sua localização"
        return Place(location.latitude, location.longitude, name)
    }

    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, Locale("pt", "BR")).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
                listOfNotNull(a.subLocality, a.locality ?: a.subAdminArea)
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { null }
            }
        }.getOrNull()
    }

    companion object {
        val SAO_PAULO = Place(-23.5505, -46.6333, "São Paulo", isFallback = true)
    }
}
