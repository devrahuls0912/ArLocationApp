package com.bodhi.arloctiondemo.location.arLocation.model

import com.bodhi.arloctiondemo.location.arLocation.LocationMarker
import com.google.android.gms.maps.model.LatLng


data class TrackerModel(
    val stepLocation: LatLng,
    var maneuver: Maneuver? = null,
    var duration: Double = 0.0,
    var distance: Double = 0.0,
    var visited: Boolean = false,
    var plotted: Boolean = false,
    var locationMarker: LocationMarker? = null
)
