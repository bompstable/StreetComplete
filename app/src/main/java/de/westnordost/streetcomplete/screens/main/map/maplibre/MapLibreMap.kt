package de.westnordost.streetcomplete.screens.main.map.maplibre

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun MapLibreMap.awaitSetStyle(builder: Style.Builder): Style = suspendCoroutine { cont ->
    setStyle(builder) { cont.resume(it) }
}

fun Style.addLayersAbove(layers: List<Layer> , above: String) {
    for (layer in layers.asReversed()) {
        addLayerAbove(layer, above)
    }
}

fun Style.addLayers(layers: List<Layer>) {
    for (layer in layers) {
        addLayer(layer)
    }
}