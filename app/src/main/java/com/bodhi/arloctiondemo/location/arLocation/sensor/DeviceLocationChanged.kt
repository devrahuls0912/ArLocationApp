package com.bodhi.arloctiondemo.location.arLocation.sensor

import android.location.Location

interface DeviceLocationChanged {
    fun onChange(location: Location?)
}
