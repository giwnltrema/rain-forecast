package com.giwnltrema.chuva.ui.map

import android.graphics.Bitmap
import com.giwnltrema.chuva.data.ForecastGrid
import com.giwnltrema.chuva.data.Place
import com.giwnltrema.chuva.data.RadarFrame
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
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Point

private const val LABELS = "labels"
private const val ME = "me"
private const val FORECAST = "forecast"
const val RADAR_OPACITY = 0.8f

/**
 * Mapa base raster da CARTO, com os nomes de ruas/cidades numa camada separada
 * por cima da chuva, para continuarem legíveis.
 */
fun baseStyleJson(dark: Boolean): String {
    val base = if (dark) "dark_nolabels" else "rastertiles/voyager_nolabels"
    val labels = if (dark) "dark_only_labels" else "rastertiles/voyager_only_labels"
    fun tiles(path: String) = listOf("a", "b", "c", "d")
        .joinToString(",") { "\"https://$it.basemaps.cartocdn.com/$path/{z}/{x}/{y}@2x.png\"" }
    val attribution = "© <a href=\\\"https://www.openstreetmap.org/copyright\\\">OpenStreetMap</a> © <a href=\\\"https://carto.com/attributions\\\">CARTO</a> · Radar <a href=\\\"https://www.rainviewer.com\\\">RainViewer</a> · Previsão <a href=\\\"https://open-meteo.com\\\">Open-Meteo</a>"
    return """
    {
      "version": 8,
      "sources": {
        "base": { "type": "raster", "tiles": [${tiles(base)}], "tileSize": 256, "maxzoom": 20, "attribution": "$attribution" },
        "$LABELS": { "type": "raster", "tiles": [${tiles(labels)}], "tileSize": 256, "maxzoom": 20 }
      },
      "layers": [
        { "id": "base", "type": "raster", "source": "base" },
        { "id": "$LABELS", "type": "raster", "source": "$LABELS" }
      ]
    }
    """.trimIndent()
}

fun Style.addUserMarker(place: Place, color: Int) {
    addSource(GeoJsonSource(ME, Point.fromLngLat(place.lon, place.lat)))
    addLayer(
        CircleLayer("$ME-halo", ME).withProperties(circleRadius(18f), circleColor(color), circleOpacity(0.18f)),
    )
    addLayer(
        CircleLayer(ME, ME).withProperties(
            circleRadius(7f),
            circleColor(color),
            circleStrokeColor(android.graphics.Color.WHITE),
            circleStrokeWidth(3f),
        ),
    )
}

fun Style.moveUserMarker(place: Place) {
    getSourceAs<GeoJsonSource>(ME)?.setGeoJson(Point.fromLngLat(place.lon, place.lat))
}

/** Uma camada por quadro do radar; trocar de quadro é só mudar a opacidade (animação fluida). */
fun Style.setRadarFrames(frames: List<RadarFrame>) {
    layers.filter { it.id.startsWith("radar-") }.forEach { removeLayer(it) }
    sources.filter { it.id.startsWith("radar-") }.forEach { removeSource(it) }
    frames.forEachIndexed { i, frame ->
        // O RainViewer gratuito só serve até o zoom 7; acima disso o MapLibre amplia (overzoom).
        val tiles = TileSet("2.2.0", frame.tileUrl).apply { maxZoom = 7f }
        addSource(RasterSource("radar-$i", tiles, 256))
        addLayerBelow(
            RasterLayer("radar-$i", "radar-$i").withProperties(
                rasterOpacity(0f),
                rasterFadeDuration(0f),
                rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
            ),
            LABELS,
        )
    }
}

fun Style.showRadarFrame(index: Int?) {
    layers.filter { it.id.startsWith("radar-") }.forEach { layer ->
        val i = layer.id.removePrefix("radar-").toIntOrNull()
        layer.setProperties(rasterOpacity(if (i == index) RADAR_OPACITY else 0f))
    }
}

fun Style.setForecastImage(grid: ForecastGrid, image: Bitmap) {
    val quad = LatLngQuad(
        LatLng(grid.north, grid.west),
        LatLng(grid.north, grid.east),
        LatLng(grid.south, grid.east),
        LatLng(grid.south, grid.west),
    )
    val source = getSourceAs<ImageSource>(FORECAST)
    if (source == null) {
        addSource(ImageSource(FORECAST, quad, image))
        addLayerBelow(
            RasterLayer(FORECAST, FORECAST).withProperties(
                rasterOpacity(0.85f),
                rasterFadeDuration(0f),
                rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
            ),
            LABELS,
        )
    } else {
        source.setCoordinates(quad)
        source.setImage(image)
    }
}

fun Style.showForecast(visible: Boolean) {
    getLayer(FORECAST)?.setProperties(visibility(if (visible) Property.VISIBLE else Property.NONE))
}
