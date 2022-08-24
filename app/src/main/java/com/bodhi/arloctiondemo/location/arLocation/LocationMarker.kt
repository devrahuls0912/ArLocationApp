package com.bodhi.arloctiondemo.location.arLocation

import com.bodhi.arloctiondemo.location.arLocation.rendering.LocationNode
import com.bodhi.arloctiondemo.location.arLocation.rendering.LocationNodeRender
import com.google.android.gms.maps.model.LatLng
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node

data class LocationMarker(
    // Location in real-world terms
    var location: LatLng,
    var node: Node,
    var isNavigation: Boolean = false,
    // Location in AR terms
    var anchorNode: LocationNode? = null,
    var navigationNode: AnchorNode? = null,
    // Called on each frame if not null
    var renderEvent: LocationNodeRender? = null,
    var scaleModifier: Float = 1f,
    var height: Float = 0f,
    var onlyRenderWhenWithin: Int = Int.MAX_VALUE,
    var scalingMode: ScalingMode = ScalingMode.FIXED_SIZE_ON_SCREEN,
    var gradualScalingMinScale: Float = 0.8f,
    var gradualScalingMaxScale: Float = 1.4f,
) {

    enum class ScalingMode {
        FIXED_SIZE_ON_SCREEN, NO_SCALING, GRADUAL_TO_MAX_RENDER_DISTANCE, GRADUAL_FIXED_SIZE
    }
}
