package com.giwnltrema.chuva.ui.map

import android.graphics.Bitmap
import com.giwnltrema.chuva.data.Place
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterFadeDuration
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.PropertyFactory.rasterResampling
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.geojson.Point

private const val ME = "me"
const val RADAR = "radar"
const val FORECAST = "forecast"

/**
 * Mapa base do OpenFreeMap (gratuito, sem chave): "liberty" é colorido e claro,
 * no estilo Google/Waze — ruas, bairros e parques fáceis de reconhecer.
 */
const val BASE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

data class Bounds(val north: Double, val south: Double, val west: Double, val east: Double) {
    val quad
        get() = LatLngQuad(
            LatLng(north, west),
            LatLng(north, east),
            LatLng(south, east),
            LatLng(south, west),
        )
}

fun Style.addUserMarker(place: Place, color: Int) {
    addSource(GeoJsonSource(ME, Point.fromLngLat(place.lon, place.lat)))
    addLayer(
        CircleLayer("$ME-halo", ME).withProperties(circleRadius(26f), circleColor(color), circleOpacity(0.22f)),
    )
    addLayer(
        CircleLayer(ME, ME).withProperties(
            circleRadius(10f),
            circleColor(color),
            circleStrokeColor(android.graphics.Color.WHITE),
            circleStrokeWidth(4f),
        ),
    )
}

fun Style.moveUserMarker(place: Place) {
    getSourceAs<GeoJsonSource>(ME)?.setGeoJson(Point.fromLngLat(place.lon, place.lat))
}

/**
 * Coloca a chuva acima de ruas, prédios e parques, mas abaixo dos nomes
 * (ruas, bairros, cidades), para dar para ler onde está chovendo.
 */
private fun Style.addAboveRoads(layer: RasterLayer) {
    val lastShape = layers.lastOrNull { it !is SymbolLayer && !it.id.startsWith(ME) && it.id != RADAR && it.id != FORECAST }
    if (lastShape != null) addLayerAbove(layer, lastShape.id) else addLayerBelow(layer, "$ME-halo")
}

/** Cria ou atualiza uma camada de imagem (radar ou previsão). */
fun Style.setOverlay(id: String, bounds: Bounds, image: Bitmap) {
    val source = getSourceAs<ImageSource>(id)
    if (source == null) {
        addSource(ImageSource(id, bounds.quad, image))
        addAboveRoads(
            RasterLayer(id, id).withProperties(
                rasterOpacity(1f),
                rasterFadeDuration(0f),
                rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
            ),
        )
    } else {
        source.setCoordinates(bounds.quad)
        source.setImage(image)
    }
}

fun Style.showOverlay(id: String, visible: Boolean) {
    getLayer(id)?.setProperties(visibility(if (visible) Property.VISIBLE else Property.NONE))
}
