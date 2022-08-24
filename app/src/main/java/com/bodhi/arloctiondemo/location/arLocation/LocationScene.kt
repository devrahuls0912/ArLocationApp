package com.bodhi.arloctiondemo.location.arLocation


import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bodhi.arloctiondemo.location.arLocation.rendering.LocationNode
import com.bodhi.arloctiondemo.location.arLocation.sensor.DeviceLocation
import com.bodhi.arloctiondemo.location.arLocation.sensor.DeviceLocationChanged
import com.bodhi.arloctiondemo.location.arLocation.sensor.DeviceOrientation
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.math.Vector3
import com.google.maps.android.SphericalUtil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class LocationScene(var context: Activity, mArSceneView: ArSceneView) {
    private val RENDER_DISTANCE = 15.0
    var mArSceneView: ArSceneView = mArSceneView
    var deviceLocation: DeviceLocation?
    var deviceOrientation: DeviceOrientation
    var destinationMarkerList = ArrayList<LocationMarker>()
    var navigationMarkerList = ArrayList<LocationMarker>()
    var arRnchorRefreshInterval = 1000 * 5 // 5 seconds
    var distanceLimit = 300
    private var offsetOverlapping = false
    private var removeOverlapping = false

    // Bearing adjustment. Can be set to calibrate with true north
    private var bearingAdjustment = 0
    private val TAG = "LocationScene"
    private var anchorsNeedRefresh = true
    private var minimalRefreshing = false
    private var refreshAnchorsAsLocationChanges = false
    private val mHandler = Handler(Looper.getMainLooper())
    var anchorRefreshTask: Runnable = object : Runnable {
        override fun run() {
//            anchorsNeedRefresh = true
            mHandler.postDelayed(this, arRnchorRefreshInterval.toLong())
        }
    }
    private val mSession: Session? = mArSceneView.session
    var locationChangedEvent: DeviceLocationChanged? = null
    fun minimalRefreshing(): Boolean {
        return minimalRefreshing
    }

    fun setMinimalRefreshing(minimalRefreshing: Boolean) {
        this.minimalRefreshing = minimalRefreshing
    }

    fun setRefreshAnchorsAsLocationChanges(refreshAnchorsAsLocationChanges: Boolean) {
        if (refreshAnchorsAsLocationChanges) {
            stopCalculationTask()
        } else {
            startCalculationTask()
        }
        refreshAnchors()
        this.refreshAnchorsAsLocationChanges = refreshAnchorsAsLocationChanges
    }

    fun clearMarkers() {
        for (lm in destinationMarkerList) {
            lm.anchorNode?.apply {
                anchor?.detach()
                isEnabled = false
            }
            lm.anchorNode = null
        }
        destinationMarkerList = ArrayList()
        clearNavigationMarkers()
    }

    fun clearNavigationMarkers() {
        for (lm in navigationMarkerList) {
            lm.anchorNode?.apply {
                anchor?.detach()
                isEnabled = false
            }
            lm.anchorNode = null
        }
        navigationMarkerList = ArrayList()
    }

    fun shouldOffsetOverlapping(): Boolean {
        return offsetOverlapping
    }

    fun shouldRemoveOverlapping(): Boolean {
        return removeOverlapping
    }

    fun setOffsetOverlapping(offsetOverlapping: Boolean) {
        this.offsetOverlapping = offsetOverlapping
    }

    fun setRemoveOverlapping(removeOverlapping: Boolean) {
        this.removeOverlapping = removeOverlapping
    }

    fun processFrame(frame: Frame) {
        refreshAnchorsIfRequired(frame)
    }

    fun refreshAnchors() {
        anchorsNeedRefresh = true
    }

    private fun refreshAnchorsIfRequired(frame: Frame) {
        if (!anchorsNeedRefresh) {
            return
        }
        anchorsNeedRefresh = false
        Log.i(TAG, "Refreshing anchors...")
        if (deviceLocation == null || deviceLocation!!.currentBestLocation == null) {
            Log.i(TAG, "Location not yet established.")
            return
        }

        destinationMarkerList.forEachIndexed { index, locationMarker ->
            processLocationMarker(index, locationMarker, frame)
        }
        navigationMarkerList.forEachIndexed { index, locationMarker ->
            processLocationMarker(index, locationMarker, frame)
        }
        // this is bad, you should feel bad
        System.gc()
    }

    private fun processLocationMarker(
        index: Int,
        locationMarker: LocationMarker,
        frame: Frame
    ) {
        try {
            if (locationMarker.isNavigation &&
                locationMarker.anchorNode != null
            ) {
                /* No need to calculate next */
                return
            }
            val markerDistance = SphericalUtil.computeDistanceBetween(
                LatLng(
                    deviceLocation!!.currentBestLocation!!.latitude,
                    deviceLocation!!.currentBestLocation!!.longitude
                ),
                LatLng(
                    locationMarker.location.latitude,
                    locationMarker.location.longitude
                )
            )
            if (markerDistance > locationMarker.onlyRenderWhenWithin) {
                // Don't render if this has been set and we are too far away.
                Log.i(
                    TAG,
                    "Not rendering. Marker distance: " + markerDistance +
                            " Max render distance: " + locationMarker.onlyRenderWhenWithin
                )
                return
            }
            val rotation = calculateBearing(locationMarker)

            // When pointing device upwards (camera towards sky)
            // the compass bearing can flip.
            // In experiments this seems to happen at pitch~=-25
            // if (deviceOrientation.pitch > -25)
            // rotation = rotation * Math.PI / 180;
            var renderDistance = markerDistance

            // Limit the distance of the Anchor within the scene.
            // Prevents rendering issues.
            if (renderDistance > distanceLimit &&
                !locationMarker.isNavigation
            )
                renderDistance = distanceLimit.toDouble()

            // Adjustment to add markers on horizon, instead of just directly in front of camera
            val newAnchor = calculateTranslation(
                markerDistance,
                renderDistance,
                rotation,
                frame,
                if (locationMarker.isNavigation)
                    -renderDistance
                else -renderDistance.coerceAtMost(RENDER_DISTANCE)
            )
            removeNodesBeforeCreateMarker(locationMarker)
            destinationMarkerList[index].anchorNode =
                createAnchorForLocation(newAnchor, locationMarker)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateTranslation(
        markerDistance: Double,
        renderDistance: Double,
        rotation: Double,
        frame: Frame,
        distanceAtMost: Double
    ): Anchor? {
        // Adjustment to add markers on horizon, instead of just directly in front of camera
        var heightAdjustment = 0.0
        // Math.round(renderDistance * (Math.tan(Math.toRadians(deviceOrientation.pitch)))) - 1.5F;

        // Raise distant markers for better illusion of distance
        // Hacky - but it works as a temporary measure
        val cappedRealDistance = if (markerDistance > 500) 500.0 else markerDistance
        if (renderDistance != markerDistance)
            heightAdjustment += (0.005f * (cappedRealDistance - renderDistance))
        // val apperantDistance = renderDistance / 2.0
        val z = distanceAtMost
        val rotationRadian = Math.toRadians(rotation)
        val zRotated = (z * cos(rotationRadian)).toFloat()
        val xRotated = (-(z * sin(rotationRadian))).toFloat()
        val y = frame.camera.displayOrientedPose.ty() + heightAdjustment.toFloat()

        // Don't immediately assign newly created anchor in-case of exceptions
        return mSession!!.createAnchor(
            frame.camera
                .displayOrientedPose
                .compose(
                    Pose.makeTranslation(
                        xRotated, y, zRotated
                    )
                )
                .extractTranslation()
        )
    }

    private fun removeNodesBeforeCreateMarker(locationMarker: LocationMarker) {
        if (locationMarker.anchorNode != null &&
            locationMarker.anchorNode!!.anchor != null
        ) {
            locationMarker.anchorNode!!.anchor!!.detach()
            locationMarker.anchorNode!!.anchor = null
            locationMarker.anchorNode!!.isEnabled = false
            locationMarker.anchorNode = null
        }
    }

    private fun createAnchorForLocation(
        newAnchor: Anchor?,
        locationMarker: LocationMarker
    ): LocationNode {
        return LocationNode(
            newAnchor, locationMarker, this
        ).apply {
            scalingMode = LocationMarker.ScalingMode.GRADUAL_FIXED_SIZE
            setParent(mArSceneView.scene)
            addChild(locationMarker.node)
            locationMarker.node.localPosition = Vector3.zero()
            if (locationMarker.renderEvent != null) {
                renderEvent = locationMarker.renderEvent
            }
            scaleModifier = locationMarker.scaleModifier
            scalingMode = locationMarker.scalingMode
            gradualScalingMaxScale = locationMarker.gradualScalingMaxScale
            gradualScalingMinScale = locationMarker.gradualScalingMinScale
            height = locationMarker.height
            if (minimalRefreshing)
                scaleAndRotate()
        }
    }

    private fun calculateBearing(locationMarker: LocationMarker): Double {
        val bearing = SphericalUtil.computeHeading(
            LatLng(
                deviceLocation!!.currentBestLocation!!.latitude,
                deviceLocation!!.currentBestLocation!!.longitude
            ),
            LatLng(
                locationMarker.location.latitude,
                locationMarker.location.longitude
            )
        )
        var markerBearing = bearing - deviceOrientation.orientation

        // Bearing adjustment can be set if you are trying to
        // correct the heading of north - setBearingAdjustment(10)
        markerBearing += bearingAdjustment + 360
        markerBearing %= 360
        return floor(markerBearing.toDouble())
    }

    fun getBearingAdjustment(): Int {
        return bearingAdjustment
    }

    fun setBearingAdjustment(i: Int) {
        bearingAdjustment = i
        anchorsNeedRefresh = true
    }

    fun resume() {
        deviceOrientation.resume()
        deviceLocation?.resume()
    }

    fun pause() {
        deviceOrientation.pause()
        deviceLocation?.pause()
    }

    fun startCalculationTask() {
        anchorRefreshTask.run()
    }

    fun stopCalculationTask() {
        mHandler.removeCallbacks(anchorRefreshTask)
    }

    init {
        startCalculationTask()
        deviceLocation = DeviceLocation(context, this)
        deviceOrientation = DeviceOrientation(context)
        deviceOrientation.resume()
        // test();
    }
}
