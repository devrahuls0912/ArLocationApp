package com.bodhi.arloctiondemo.location.arLocation.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.bodhi.arloctiondemo.location.arLocation.LocationScene
import com.google.android.gms.location.*

class DeviceLocation(
    private val context: Context,
    private val locationScene: LocationScene
) : LocationCallback() {
    @JvmField
    var currentBestLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @SuppressLint("MissingPermission")
    fun renewCurrentLocation(updatedLocation: (location: Location?) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener {
            updatedLocation(it)
        }
    }

    override fun onLocationResult(locationResult: LocationResult) {
        val lastlocation = locationResult.lastLocation
        lastlocation ?: return
        Log.d(
            TAG,
            "(" + lastlocation.latitude +
                    "," + lastlocation.longitude + ")"
        )
        filterAndAddLocation(lastlocation)
    }

    private fun filterAndAddLocation(location: Location) {
        Log.d(TAG, "Location quality is good enough.")
        currentBestLocation = location
        locationEvents()
    }

    private fun locationEvents() {
        if (locationScene.locationChangedEvent != null) {
            locationScene.locationChangedEvent?.onChange(currentBestLocation)
        }
        // if (locationScene.refreshAnchorsAsLocationChanges()) {
        //     // locationScene.refreshAnchors()
        // }
    }

    fun pause() {
        fusedLocationClient.removeLocationUpdates(this)
    }

    fun resume() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            fusedLocationClient.requestLocationUpdates(
                LocationRequest.create().apply {
                    interval = 5000
                    fastestInterval = 5000
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                },
                this,
                Looper.getMainLooper()
            )
        }
    }

    companion object {
        private val TAG = DeviceLocation::class.java.simpleName
        private const val TWO_MINUTES = 1000 * 60 * 2
    }

    init {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }
    }
}
