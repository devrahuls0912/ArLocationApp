package com.bodhi.arloctiondemo.location.ar.view

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bodhi.arloctiondemo.R
import com.bodhi.arloctiondemo.data.MallItemResponse
import com.bodhi.arloctiondemo.data.ShopItems
import com.bodhi.arloctiondemo.location.Utils
import com.bodhi.arloctiondemo.location.Utils.matchLocation
import com.bodhi.arloctiondemo.location.ar.location.getScaleModifierBasedOnRealDistance
import com.bodhi.arloctiondemo.location.ar.location.showDistance
import com.bodhi.arloctiondemo.location.arLocation.LocationMarker
import com.bodhi.arloctiondemo.location.arLocation.LocationScene
import com.bodhi.arloctiondemo.location.arLocation.model.AnchorReferences
import com.bodhi.arloctiondemo.location.arLocation.model.TrackerModel
import com.bodhi.arloctiondemo.location.arLocation.rendering.LocationNode
import com.bodhi.arloctiondemo.location.arLocation.rendering.LocationNodeRender
import com.bodhi.arloctiondemo.location.arLocation.sensor.DeviceLocationChanged
import com.bodhi.arloctiondemo.mapShopItemImageDrawable
import com.bodhi.arloctiondemo.readAssetsFile
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.gson.Gson
import com.google.maps.android.SphericalUtil
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.math.BigDecimal
import java.math.RoundingMode


const val INVALID_MARKER_SCALE_MODIFIER = -1F
const val INITIAL_MARKER_SCALE_MODIFIER = 0.5f

abstract class BaseLocationActivity : AppCompatActivity() {
    private var locationScene: LocationScene? = null
    private var trackerList: MutableList<TrackerModel> = mutableListOf()
    private var areAllMarkersLoaded = false
    private var selectedDestination: LatLng? = null
    var arCoreInstallRequested = false
    private lateinit var arSceneView: ArSceneView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Utils.checkIsARSupportedDeviceOrFinish(this)) {
            return
        }
    }

    lateinit var selectedItems: Array<Int>
    private val filteredShopList: List<ShopItems> by lazy {
        createShopList().filter {
            selectedItems.toList().contains(it.itemCode)
        }
    }
    private val shopList: List<ShopItems> by lazy {
        createShopList()
    }
    private val anchorList: AnchorReferences by lazy {
        createAnchorList()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        Dexter.withActivity(this@BaseLocationActivity)
            .withPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if ((report?.grantedPermissionResponses?.size ?: 0) > 1) {
                        setupSession()
                    } else {
                        showPermissionRequiredError()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showPermissionRequiredError()
                }
            }).check()
    }

    abstract fun setupSession()
    abstract fun changeViewForDestination()
    fun initialiseLocationScene(sceneview: ArSceneView) {
        arSceneView = sceneview
        if (locationScene == null) {
            locationScene = LocationScene(this, arSceneView).apply {
                setMinimalRefreshing(true)
                setOffsetOverlapping(true)
                arRnchorRefreshInterval = 5000
            }
        }

        try {
            attachScreenUpdateListener()
            resumeArElementsTask()
        } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, "Unable to get camera", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (trackerList.isEmpty()) {
            locationScene?.clearMarkers()
            trackerList.clear()
            renderVenues(filteredShopList)
        }
    }

    private fun showPermissionRequiredError() {
        AlertDialog.Builder(
            this@BaseLocationActivity,
            androidx.appcompat.R.style.AlertDialog_AppCompat_Light
        )
            .setTitle("Permission Error")
            .setMessage(
                "Please allow Location and Camera permission from settings to " +
                        "use this feature."
            )
            .setPositiveButton(
                "Ok"
            ) { dialog, which ->
                dialog.dismiss()
                this@BaseLocationActivity.finish()
            }.create().show()
    }

    private fun resumeArElementsTask() {
        lifecycleScope.launchWhenResumed {
            locationScene?.resume()
            arSceneView.resume()
        }
    }

    private fun renderVenues(shopList: List<ShopItems>) {
        setupAndRenderVenuesMarkers(shopList)
    }

    private fun setupAndRenderVenuesMarkers(shopList: List<ShopItems>) {
        shopList.forEachIndexed { index, venue ->
            plotDestination(venue)
            if (index == shopList.size - 1) {
                areAllMarkersLoaded = true
            }
        }
    }

    private fun plotDestination(venue: ShopItems) {
        ViewRenderable.builder()
            .setView(
                this,
                R.layout.location_layout_renderable
            )
            .build()
            .thenAccept {
                try {
                    val venueMarker = LocationMarker(
                        LatLng(
                            venue.locaton[0],
                            venue.locaton[1]
                        ),
                        setVenueNode(
                            venue, it
                        )
                    )
                    attachMarkerToScene(
                        venueMarker,
                        it.view
                    )
                } catch (ex: Exception) {
                }
            }
    }

    private fun attachMarkerToScene(
        locationMarker: LocationMarker,
        layoutRendarable: View
    ) {
        locationMarker.scalingMode = LocationMarker.ScalingMode.FIXED_SIZE_ON_SCREEN
        locationMarker.scaleModifier = INITIAL_MARKER_SCALE_MODIFIER

        locationScene?.destinationMarkerList?.add(locationMarker)
        locationMarker.anchorNode?.isEnabled = true

        locationScene?.refreshAnchors()
        layoutRendarable.findViewById<View>(R.id.pinContainer).visibility = View.VISIBLE

        locationMarker.renderEvent = object : LocationNodeRender {
            override fun render(node: LocationNode?) {
                node?.let {
                    layoutRendarable.findViewById<TextView>(
                        R.id.distance
                    ).text = showDistance(it.distance)
                    computeNewScaleModifierBasedOnDistance(locationMarker, node.distance)
                }
            }
        }
    }

    private fun attachScreenUpdateListener() {
        arSceneView.scene.addOnUpdateListener {
            if (!areAllMarkersLoaded) {
                return@addOnUpdateListener
            }
            locationScene?.destinationMarkerList?.forEach { locationMarker ->
                locationMarker.height = 0.5f
            }
            locationScene?.navigationMarkerList?.forEach { locationMarker ->
                locationMarker.height = 0.1f
            }

            val frame = arSceneView.arFrame ?: return@addOnUpdateListener
            if (frame.camera.trackingState == TrackingState.TRACKING) {
                locationScene?.processFrame(frame)
            }
        }
    }

    override fun onDestroy() {
        try {
            locationScene?.destinationMarkerList?.forEach {
                detachMarker(it)
            }
            locationScene?.navigationMarkerList?.forEach {
                detachMarker(it)
            }
            arSceneView.pauseAsync { }
            locationScene?.stopCalculationTask()
            locationScene?.clearNavigationMarkers()
            locationScene?.clearMarkers()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setVenueNode(
        venue: ShopItems,
        viewRenderable: ViewRenderable
    ): Node {
        val node = Node()
        node.renderable = viewRenderable

        val nodeLayout = viewRenderable.view
        nodeLayout.findViewById<TextView>(R.id.name).text = venue.itemName
        nodeLayout.findViewById<View>(R.id.pinContainer).visibility = View.GONE
        mapShopItemImageDrawable(this@BaseLocationActivity, venue.itemCode)?.let {
            nodeLayout.findViewById<ImageView>(R.id.categoryIcon).setImageDrawable(it)
        }
        nodeLayout.setOnTouchListener { _, _ ->
            /* remove other locations before entering into navigation mode */
            if (locationScene?.destinationMarkerList?.isNotEmpty() == true) {
                locationScene?.destinationMarkerList?.removeIf {
                    detachMarker(it)
                    it.location.matchLocation(
                        LatLng(
                            venue.locaton[0],
                            venue.locaton[1]
                        )
                    ).not()
                }
                locationScene?.refreshAnchors()
                /* now query route information */
//                startNavigation(
//                    LatLng(
//                        venue.locaton[0],
//                        venue.locaton[1]
//                    )
//                )
                populateRoute(venue.itemCode)
            }
            false
        }
        return node
    }

    private fun startNavigation(destination: LatLng) {
        trackerList.forEach {
            it.locationMarker?.let {
                detachMarker(it)
            }
        }
        trackerList.clear()
        locationScene?.clearNavigationMarkers()
        selectedDestination = destination
        changeViewForDestination()
        locationScene?.deviceLocation?.currentBestLocation?.let { loc ->
            val distance = SphericalUtil.computeDistanceBetween(
                LatLng(loc.latitude, loc.longitude),
                destination
            )
            if (distance > 1 && distance < 50) {
                trackerList.add(
                    TrackerModel(
                        stepLocation = LatLng(loc.latitude, loc.longitude),
                        distance = distance
                    )
                )
                for (i in 1 until distance.toInt()) {
                    val step = SphericalUtil.interpolate(
                        trackerList.first().stepLocation,
                        destination,
                        i.toDouble()
                    )
                    trackerList.add(
                        0,
                        TrackerModel(
                            stepLocation = step,
                            distance = SphericalUtil.computeDistanceBetween(
                                step,
                                destination
                            )
                        )
                    )
                }
                trackerList.sortBy {
                    SphericalUtil.computeDistanceBetween(
                        it.stepLocation,
                        destination
                    )
                }
//                plotSteps() // for 3rd party navigation
                locationScene?.deviceLocation?.renewCurrentLocation {
                    if (it != null) {
                        updateRouting(LatLng(it.latitude, it.longitude))
                    }
                }
            } else {
                Toast.makeText(
                    this,
                    "Please click again once within 50m radius.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun computeNewScaleModifierBasedOnDistance(
        locationMarker: LocationMarker,
        distance: Double
    ) {
        val scaleModifier = getScaleModifierBasedOnRealDistance(distance)
        return if (scaleModifier == INVALID_MARKER_SCALE_MODIFIER) {
            detachMarker(locationMarker)
        } else {
            locationMarker.scaleModifier = scaleModifier
        }
    }

    private fun detachMarker(locationMarker: LocationMarker) {
        locationMarker.anchorNode?.let {
            arSceneView.scene.removeChild(it)
        }
        locationMarker.navigationNode?.anchor?.detach()
        locationMarker.anchorNode?.anchor?.detach()
        locationMarker.anchorNode?.setParent(null)
        locationMarker.anchorNode?.isEnabled = false
        locationMarker.anchorNode = null
    }

    private fun createShopList(): List<ShopItems> {
        val mallItemList =
            Gson().fromJson(
                this.assets.readAssetsFile("itemlist.json"),
                MallItemResponse::class.java
            )
        return mallItemList.mallItems
    }

    private fun createAnchorList(): AnchorReferences {
        val anchorReference =
            Gson().fromJson(
                this.assets.readAssetsFile("grocery.json"),
                AnchorReferences::class.java
            )
        return anchorReference
    }

    abstract fun giveDirectionSuggestion(
        suggestion: String,
        distance: String
    )

    fun populateRoute(qrLocationCode: Int) {
        val filteredList = shopList.filter {
            it.itemCode == qrLocationCode
        }
        if (filteredList.isNotEmpty()) {
            filteredList.first().route?.let { route ->
                /* 1st clear everything fro screen  */
                trackerList.forEach {
                    it.locationMarker?.let {
                        detachMarker(it)
                    }
                }
                trackerList.clear()
                locationScene?.destinationMarkerList?.forEach {
                    detachMarker(it)
                }
                locationScene?.destinationMarkerList?.clear()
                locationScene?.navigationMarkerList?.forEach {
                    detachMarker(it)
                }
                locationScene?.navigationMarkerList?.clear()
                locationScene?.refreshAnchors()
                selectedDestination = null
                val children: List<Node> = ArrayList(arSceneView.scene.children)
                for (node in children) {
                    if (node is AnchorNode) {
                        node.anchor?.detach()
                    }
                }

                if (trackerList.isEmpty()) {
                    locationScene?.clearMarkers()
                    trackerList.clear()
                }
                // Now populating new one --

                selectedDestination = LatLng(
                    route.last().loc.first(),
                    route.last().loc.last()
                )
                //Plot destination marker
                shopList.firstOrNull {
                    it.locaton.first() == route.last().loc.first() &&
                            it.locaton.last() == route.last().loc.last()
                }?.let {
                    plotDestination(it)
                }

                changeViewForDestination()
                trackerList.addAll(
                    route.map {
                        TrackerModel(
                            LatLng(
                                it.loc.first(),
                                it.loc.last()
                            )
                        )
                    }
                )
                locationScene?.deviceLocation?.currentBestLocation?.let { loc ->
                    val distance = SphericalUtil.computeDistanceBetween(
                        LatLng(loc.latitude, loc.longitude),
                        selectedDestination
                    )
                    giveDirectionSuggestion("", showDistance(distance))

                    updateRouting(LatLng(loc.latitude, loc.longitude))
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateRouting(destination: LatLng) {
        /* Take the 1st step intersection plot  once plotted added it to tracker*/

        val unPlottedList = trackerList.filter {
            !it.plotted &&
                    !it.visited
        }
        if (unPlottedList.isEmpty())
            return

        val tracker = unPlottedList.last() // take the 1st step
        // check distance between two node
        giveDirectionSuggestion(
            "Please follow the route.. ",
            "Continue walking ${
                BigDecimal(tracker.distance).setScale(
                    2,
                    RoundingMode.HALF_DOWN
                ).toDouble()
            } meters"

        )

        unPlottedList.forEachIndexed { index, intersection ->
            plotNavigationNodeOnLocation(destination, intersection.stepLocation)
            locationScene?.refreshAnchors()
        }
        startLocationUpdates()
    }

    fun exitView() {
        trackerList.clear()
        selectedDestination = null
        locationScene?.locationChangedEvent = null
        locationScene?.clearMarkers()
    }

    fun recalibrate() {
        locationScene?.destinationMarkerList?.forEach {
            detachMarker(it)
        }
        locationScene?.destinationMarkerList?.clear()
        locationScene?.navigationMarkerList?.forEach {
            detachMarker(it)
        }
        locationScene?.navigationMarkerList?.clear()
        locationScene?.refreshAnchors()
        selectedDestination = null
        val children: List<Node> = ArrayList(arSceneView.scene.children)
        for (node in children) {
            if (node is AnchorNode) {
                node.anchor?.detach()
            }
        }

        if (trackerList.isEmpty()) {
            locationScene?.clearMarkers()
            trackerList.clear()
            renderVenues(filteredShopList)
        }
    }

    override fun onPause() {
        super.onPause()
        locationScene?.pause()
    }

    private fun plotNavigationNodeOnLocation(destination: LatLng, location: LatLng) {
//        ViewRenderable.builder()
//            .setView(
//                this@BaseLocationActivity,
//                R.layout.arrow_renderable
//            )
//            .build().thenAccept {
//                try {
//                    val venueMarker = LocationMarker(
//                        location,
//                        Node().apply {
//                            renderable = it
//                        },
//                        true
//                    )
//                    venueMarker.scalingMode = LocationMarker.ScalingMode.GRADUAL_FIXED_SIZE
//                    venueMarker.scaleModifier = INITIAL_MARKER_SCALE_MODIFIER
//
//                    val trackIndex = trackerList.indexOfFirst {
//                        it.stepLocation == location
//                    }
//                    if (trackIndex >= 0) {
//                        trackerList[trackIndex].locationMarker = venueMarker
//                        trackerList[trackIndex].plotted = true
//                    }
//
//                    locationScene?.navigationMarkerList?.add(venueMarker)
//                    venueMarker.anchorNode?.isEnabled = true
//                    venueMarker.anchorNode?.distance = SphericalUtil.computeDistanceBetween(
//                        destination,
//                        location
//                    )
//                } catch (ex: Exception) {
//                    ex.printStackTrace()
//                }
//            }
        MaterialFactory.makeOpaqueWithColor(
            this,
            Color().apply {
                set(255F, 255F, 255F, 255F)
            }
        )
            .thenAccept { material: Material? ->
                val pathRenderable =
                    ShapeFactory.makeSphere(
                        0.03f,
                        Vector3(
                            0.0f,
                            0.15f,
                            0.0f
                        ),
                        material
                    )
                try {
                    val venueMarker = LocationMarker(
                        location,
                        Node().apply {
                            renderable = pathRenderable
                            localScale = Vector3(0.1f, 0.1f, 0.1f)
                        },
                        false
                    )
                    venueMarker.scalingMode = LocationMarker.ScalingMode.GRADUAL_FIXED_SIZE
                    venueMarker.scaleModifier = INITIAL_MARKER_SCALE_MODIFIER

                    val trackIndex = trackerList.indexOfFirst {
                        it.stepLocation == location
                    }
                    if (trackIndex >= 0) {
                        trackerList[trackIndex].locationMarker = venueMarker
                        trackerList[trackIndex].plotted = true
                    }

                    locationScene?.navigationMarkerList?.add(venueMarker)
                    venueMarker.anchorNode?.isEnabled = true
                    venueMarker.anchorNode?.distance = SphericalUtil.computeDistanceBetween(
                        destination,
                        location
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        locationScene?.locationChangedEvent = object : DeviceLocationChanged {
            override fun onChange(location: Location?) {
                location?.let { currentLocation ->
                    if (trackerList.isNotEmpty()) {
                        val filteredList = trackerList.filter {
                            it.plotted &&
                                    !it.visited
                        }
                        // get the distance between step last intersection co-ordinate
                        // and current location

                        if (filteredList.isNotEmpty() &&
                            SphericalUtil.computeDistanceBetween(
                                LatLng(
                                    currentLocation.latitude,
                                    currentLocation.longitude
                                ),
                                filteredList.last().stepLocation
                            ) < 0.5
                        ) {
                            filteredList.forEach { trackerModel ->
                                // update already plotted intersection as visited node
                                trackerList.find {
                                    it.stepLocation == trackerModel.stepLocation
                                }?.let {
                                    it.visited = true
                                    it.locationMarker?.let { detachMarker(it) }
                                    it.locationMarker = null
                                }
                            }
                            // now plot the next step
//                            plotSteps()
                        }
                    }
                }
            }
        }
    }
}