package com.giwnltrema.chuva.ui.trip

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.giwnltrema.chuva.data.RainSource
import com.giwnltrema.chuva.data.Route
import com.giwnltrema.chuva.data.SampleRain
import com.giwnltrema.chuva.data.TRIP_WET_RATE
import com.giwnltrema.chuva.data.TripPlan
import com.giwnltrema.chuva.ui.map.BASE_STYLE_URL
import com.giwnltrema.chuva.ui.map.rememberMapViewWithLifecycle
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Cores de "vou me molhar?": verde seco, amarelo/laranja/vermelho conforme a chuva. */
object TripColors {
    val dry = Color(0xFF34A853)
    val light = Color(0xFFFBBC04)
    val moderate = Color(0xFFFB8C00)
    val heavy = Color(0xFFE53935)
    val unknown = Color(0xFF9AA0A6)

    fun of(rate: Double) = when {
        rate < TRIP_WET_RATE -> dry
        rate < 2.5 -> light
        rate < 10 -> moderate
        else -> heavy
    }

    /** Cinza onde não há dado (nem radar nem modelo). */
    fun of(point: SampleRain) = if (point.source == RainSource.NONE) unknown else of(point.rate)
}

private fun Color.hex() = String.format("#%06X", toArgb() and 0xFFFFFF)

/** Mapa da rota, com cada trecho pintado pela chuva na hora em que você passa por ele. */
@Composable
fun TripMap(route: Route, plan: TripPlan, modifier: Modifier = Modifier) {
    val mapView = rememberMapViewWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val padding = with(LocalDensity.current) { 40.dp.roundToPx() }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
            }
            m.setStyle(Style.Builder().fromUri(BASE_STYLE_URL)) { s ->
                s.addSource(GeoJsonSource("route"))
                s.addSource(GeoJsonSource("ends"))
                s.addLayer(
                    LineLayer("route-casing", "route").withProperties(
                        lineColor("#FFFFFF"), lineWidth(10f),
                        lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    LineLayer("route", "route").withProperties(
                        lineColor(get("color")), lineWidth(6f),
                        lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    CircleLayer("ends", "ends").withProperties(
                        circleRadius(8f), circleColor(get("color")),
                        circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
                    ),
                )
                style = s
            }
            map = m
        }
    }

    LaunchedEffect(map, style, route, plan) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        val segments = plan.points.zipWithNext().map { (a, b) ->
            val idx = a.sample.routeIndex..b.sample.routeIndex
            Feature.fromGeometry(LineString.fromLngLats(idx.map { Point.fromLngLat(route.lons[it], route.lats[it]) })).apply {
                addStringProperty("color", TripColors.of(a).hex())
            }
        }
        s.getSourceAs<GeoJsonSource>("route")?.setGeoJson(FeatureCollection.fromFeatures(segments))
        val last = route.lats.lastIndex
        s.getSourceAs<GeoJsonSource>("ends")?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOf(
                    Feature.fromGeometry(Point.fromLngLat(route.lons[0], route.lats[0])).apply { addStringProperty("color", "#1E5BD8") },
                    Feature.fromGeometry(Point.fromLngLat(route.lons[last], route.lats[last])).apply { addStringProperty("color", "#202124") },
                ),
            ),
        )
        val bounds = runCatching {
            LatLngBounds.Builder().apply {
                for (i in route.lats.indices step maxOf(1, route.lats.size / 200)) include(LatLng(route.lats[i], route.lons[i]))
                include(LatLng(route.lats[last], route.lons[last]))
            }.build()
        }.getOrNull() ?: return@LaunchedEffect
        runCatching { m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding)) }
    }

    AndroidView(factory = { mapView }, modifier = modifier.fillMaxWidth().height(280.dp))
}
