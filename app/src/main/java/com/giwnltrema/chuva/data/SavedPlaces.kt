package com.giwnltrema.chuva.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class PlaceKind(val label: String) { HOME("Casa"), WORK("Trabalho"), OTHER("Outro") }

/** Lugar que você salvou para ver a previsão de lá (casa, trabalho…). */
@Serializable
data class SavedPlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
) {
    fun toPlace() = Place(lat, lon, name)
    fun toDestination() = Destination(name, kind.label, lat, lon)
}

internal val placesJson = Json { ignoreUnknownKeys = true }

internal fun encodePlaces(list: List<SavedPlace>) = placesJson.encodeToString(list)
internal fun decodePlaces(raw: String?): List<SavedPlace> =
    raw?.let { runCatching { placesJson.decodeFromString<List<SavedPlace>>(it) }.getOrNull() }.orEmpty()

/** Casa, trabalho etc. primeiro; depois na ordem em que foram criados. */
internal fun sortPlaces(list: List<SavedPlace>) = list.sortedBy { it.kind.ordinal }

class PlacesStore(context: Context) {
    private val prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)

    var places: List<SavedPlace>
        get() = sortPlaces(decodePlaces(prefs.getString("list", null)))
        private set(v) = prefs.edit().putString("list", encodePlaces(v)).apply()

    fun upsert(p: SavedPlace) {
        places = places.filterNot { it.id == p.id } + p
    }

    fun remove(id: String) {
        places = places.filterNot { it.id == id }
    }
}
