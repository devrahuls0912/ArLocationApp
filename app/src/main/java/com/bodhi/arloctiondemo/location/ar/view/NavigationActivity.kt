package com.bodhi.arloctiondemo.location.ar.view

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bodhi.arloctiondemo.R
import com.bodhi.arloctiondemo.data.LegNav
import com.bodhi.arloctiondemo.data.MallItemResponse
import com.bodhi.arloctiondemo.data.QRScanCode
import com.bodhi.arloctiondemo.data.ShopItems
import com.bodhi.arloctiondemo.databinding.NavigationArActivityBinding
import com.bodhi.arloctiondemo.location.Utils
import com.bodhi.arloctiondemo.location.ar.location.handleSessionException
import com.bodhi.arloctiondemo.location.ar.location.setupSession
import com.bodhi.arloctiondemo.location.arLocation.rendering.CompassMarker
import com.bodhi.arloctiondemo.location.arLocation.sensor.DeviceOrientation
import com.bodhi.arloctiondemo.mapShopItemImageDrawable
import com.bodhi.arloctiondemo.readAssetsFile
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.launch
import kotlin.math.*

enum class AnchorType {
    INFO,
    DESTINATION,
    RIGHT_TURN,
    LEFT_TURN,
    UP_DIRECTION,
    FORWARD_RIGHT,
    FORWARD_LEFT,
    BACK_RIGHT,
    BACK_LEFT,
    DOWN_DIRECTION
}

class NavigationActivity : AppCompatActivity() {
    private val binding: NavigationArActivityBinding by lazy {
        NavigationArActivityBinding.inflate(
            LayoutInflater.from(this)
        )
    }

    var arAnchorRefreshInterval = 1000 * 5
    var arCoreInstallRequested = false
    private var anchorNodeList: ArrayList<CompassMarker> = arrayListOf()
    private val arSceneView: ArSceneView by lazy {
        binding.arSceneView
    }
    private val mHandler = Handler(Looper.getMainLooper())
    private var anchorsNeedRefresh = true
    private var shouldProcessFrame = true
    private val selectedItemId: Int by lazy {
        intent.getIntExtra("listSelected", 1)
    }
    var anchorRefreshTask: Runnable = object : Runnable {
        override fun run() {
            anchorsNeedRefresh = true
            mHandler.postDelayed(this, arAnchorRefreshInterval.toLong())
        }
    }
    private val shopList: List<ShopItems> by lazy {
        createShopList()
    }
    private val qrOptions by lazy {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
    }
    private lateinit var deviceOrientation: DeviceOrientation
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (!Utils.checkIsARSupportedDeviceOrFinish(this)) {
            return
        }
        binding.arSceneView.planeRenderer.isEnabled = false
        binding.arSceneView.scene.camera.nearClipPlane = 0.2f
        binding.arSceneView.scene.camera.farClipPlane = 500f
        deviceOrientation = DeviceOrientation(this)
        binding.qrCode.setOnClickListener {
            shouldProcessFrame = true
        }
    }

    override fun onResume() {
        super.onResume()
        anchorRefreshTask.run()
        deviceOrientation.resume()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        Dexter.withActivity(this@NavigationActivity)
            .withPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if ((report?.grantedPermissionResponses?.size ?: 0) > 1) {
                        lifecycleScope.launch {
                            initialiseSession()
                        }

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

    private fun initialiseSession() {
        if (arSceneView.session == null) {
            try {
                val session = setupSession(this, arCoreInstallRequested)
                if (session == null) {
                    arCoreInstallRequested = true
                    return
                } else {
                    arSceneView.setupSession(session)
                }
            } catch (e: UnavailableException) {
                handleSessionException(this, e)
            }
        }
        arSceneView.resume()

        arSceneView.scene?.addOnUpdateListener { frameTime ->
            frameTime?.let {
                val frame = arSceneView.arFrame ?: return@addOnUpdateListener
                if (frame.camera.trackingState == TrackingState.TRACKING) {
                    if (shouldProcessFrame) {
                        shouldProcessFrame = false
                        processArFrameDelegate()
                    }
                }
            }
        }

    }

    private fun processArFrameDelegate() {
        try {
            arSceneView.arFrame?.acquireCameraImage()?.let { bmp ->
                val inputImage = InputImage.fromMediaImage(bmp, 0)
                val barCodeResult = BarcodeScanning.getClient(qrOptions).process(
                    inputImage
                )
                while (!barCodeResult.isComplete) {
                    Handler(Looper.getMainLooper()).postDelayed({}, 100)
                }
                val barCode = barCodeResult.result
                if (barCode != null && barCodeResult.isSuccessful) {

                    if (barCode.isNotEmpty() &&
                        barCode.first().displayValue?.contains("navigation") == true
                    ) {

                        /* Successful bar code read.
                        Let's remove previous anchors and refresh a gc to free some memory. */
                        /*-------------- Clean up -------------------*/
                        if (anchorNodeList.isNotEmpty()) {
                            anchorNodeList.forEach {
                                it.isEnabled = false
                                it.anchor?.detach()
                                it.anchor = null

                                arSceneView.scene?.removeChild(it)
                            }
                        }
                        anchorNodeList.clear()
                        val myLocationList = arSceneView.scene?.children?.filter {
                            it.name == "MyLocationNode"
                        }
                        if (!myLocationList.isNullOrEmpty()) {
                            myLocationList.forEach {
                                it.isEnabled = false
                                arSceneView.scene?.removeChild(it)
                            }
                        }
                        try {
                            System.gc()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        /* Wait for sometimes */
                        Handler(Looper.getMainLooper()).postDelayed({}, 200)
                        /*-------------- End Clean up -------------------*/
                        Toast.makeText(
                            this@NavigationActivity,
                            "QR code compatible trying to plot next location.",
                            Toast.LENGTH_SHORT
                        ).show()
                        barCode.first().displayValue?.let {
                            val qrScanCode = Gson().fromJson(
                                it,
                                QRScanCode::class.java
                            )
                            if (qrScanCode.navigation.isNotEmpty()) {
                                qrScanCode.navigation.find {
                                    it.itemCode == selectedItemId
                                }?.let { qr ->
                                    shopList.find { shopItem ->
                                        shopItem.itemCode == qr.itemCode
                                    }?.let { shop ->
                                        shop.path?.find {
                                            it.pathId == qr.legId
                                        }?.let { nathNav ->
                                            nathNav.legs.forEachIndexed { index, legNav ->
                                                createPrimaryMarker(
                                                    legNav,
                                                    shop.itemName,
                                                    shop.itemCode
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    bmp.close()
                } else {
                    bmp.close()
                    shouldProcessFrame = true
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createPrimaryMarker(
        legNav: LegNav,
        itemName: String,
        itemCode: Int
    ) {
        try {
            arSceneView.arFrame?.camera?.displayOrientedPose?.let { displayPose ->
                val completableRenderable = when (AnchorType.valueOf(legNav.type)) {
                    AnchorType.INFO,
                    AnchorType.DESTINATION -> ViewRenderable.builder().setView(
                        this,
                        R.layout.location_layout_renderable
                    )
                        .build()
                    else -> null
                }
                if (completableRenderable != null) {
                    completableRenderable.thenAccept {
                        it?.let {
                            if (AnchorType.valueOf(legNav.type) == AnchorType.DESTINATION) {
                                it.view?.findViewById<TextView>(R.id.name)?.text = itemName
                                mapShopItemImageDrawable(
                                    this@NavigationActivity,
                                    itemCode
                                )?.let { dr ->
                                    it.view?.findViewById<ImageView>(R.id.categoryIcon)
                                        ?.setImageDrawable(dr)
                                }
                            } else if (AnchorType.valueOf(legNav.type) == AnchorType.INFO) {
                                it.view?.findViewById<TextView>(R.id.name)?.text =
                                    "${legNav.info} to find $itemName"
                            }

                            displayPose.createCompassMarkerPose(
                                legNav,
                                it
                            )
                        }
                    }
                } else {
                    ModelRenderable.builder().setSource(
                        this,
                        Uri.parse("tinker.sfb")
                    ).build().thenAccept {
                        it?.let {
                            displayPose.createCompassMarkerPose(
                                legNav,
                                it
                            )
                        }
                    }
                }
            }

        } catch (ex: Exception) {
            Log.i("tag", ex.toString())
        }
    }

    private fun detachMarker(node: AnchorNode) {
        node.isEnabled = false
        node.anchor?.detach()
        node.anchor = null
        arSceneView.scene?.removeChild(node)
    }

    override fun onDestroy() {
        try {
            mHandler.removeCallbacks(anchorRefreshTask)
            deviceOrientation.pause()
            anchorNodeList.forEach {
                detachMarker(it)
            }
            anchorNodeList.clear()
            arSceneView.pauseAsync { }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
/*
    private fun AugmentedImage.createCompassMarkerPose(
        legNav: LegNav,
        renderView: Renderable
    ) {
        val translatedPose = when (AnchorType.valueOf(legNav.type)) {
            AnchorType.DESTINATION -> this.centerPose.compose(
                Pose.makeTranslation(
                    this.centerPose.tx(),
                    legNav.height.toFloat(),
                    this.centerPose.tz()
                )
            ).extractTranslation()
            AnchorType.INFO -> this.centerPose.compose(
                Pose.makeTranslation(
                    this.centerPose.tx(),
                    legNav.height.toFloat(),
                    this.centerPose.tz()
                )
            ).extractTranslation()
            AnchorType.LEFT_TURN -> this.centerPose.compose(
                Pose.makeTranslation(
                    (this.centerPose.tx() - legNav.distance).toFloat(),
                    legNav.height.toFloat(),
                    this.centerPose.tz()
                )
            ).extractTranslation()
            AnchorType.RIGHT_TURN -> this.centerPose.compose(
                Pose.makeTranslation(
                    (this.centerPose.tx() + legNav.distance).toFloat(),
                    legNav.height.toFloat(),
                    this.centerPose.tz()
                )
            ).extractTranslation()
            AnchorType.UP_DIRECTION -> this.centerPose.compose(
                Pose.makeTranslation(
                    (this.centerPose.tx()),
                    legNav.height.toFloat() + legNav.distance.toFloat(),
                    this.centerPose.tz()
                )
            ).extractTranslation()
            AnchorType.FORWARD_LEFT -> this.centerPose.compose(
                Pose.makeTranslation(
                    (this.centerPose.tx() - legNav.distance).toFloat(),
                    legNav.height.toFloat() + legNav.depth.toFloat(),
                    this.centerPose.tz() + legNav.depth.toFloat()
                )
            ).extractTranslation()
            AnchorType.BACK_LEFT -> this.centerPose.compose(
                Pose.makeTranslation(
                    (this.centerPose.tx() - legNav.distance).toFloat(),
                    legNav.height.toFloat() - legNav.depth.toFloat(),
                    this.centerPose.tz() + legNav.depth.toFloat()
                )
            ).extractTranslation()
            AnchorType.FORWARD_RIGHT -> this.centerPose.compose(
                Pose.makeTranslation(
                    (this.centerPose.tx() + legNav.distance).toFloat(),
                    legNav.height.toFloat() + legNav.depth.toFloat(),
                    this.centerPose.tz() + legNav.depth.toFloat()
                )
            ).extractTranslation()
            AnchorType.BACK_RIGHT -> this.centerPose.compose(
                Pose.makeTranslation(
                    (this.centerPose.tx() + legNav.distance).toFloat(),
                    legNav.height.toFloat() - legNav.depth.toFloat(),
                    this.centerPose.tz() - legNav.depth.toFloat()
                )
            ).extractTranslation()
            else -> this.centerPose.compose(
                Pose.makeTranslation(
                    (this.centerPose.tx()),
                    legNav.height.toFloat() - legNav.distance.toFloat() + legNav.depth.toFloat(),
                    this.centerPose.tz() + legNav.depth.toFloat()
                )
            ).extractTranslation()
        }

        val primaryAnchor = this.createAnchor(translatedPose)
        val compassMarker = CompassMarker(
            primaryAnchor,
            legNav.distance,
            isPath = renderView is ModelRenderable,
            needRefresh = {
                anchorsNeedRefresh
            },
            refreshDone = {
                anchorsNeedRefresh = false
            }
        ).apply {
            name = "MyLocationNode"
            addChild(
                Node().apply {
                    renderable = renderView
                    if (renderView is ModelRenderable) {
                        calculateLocalHeading(AnchorType.valueOf(legNav.type))?.let {
                            localRotation = it
                        }
                        localScale = Vector3(0.015f, 0.015f, 0.015f)

                    }
                })
            setParent(arSceneView.scene)
        }
        anchorNodeList.add(compassMarker)
    }*/

    private fun Pose.createCompassMarkerPose(
        legNav: LegNav,
        renderView: Renderable
    ) {
        if (renderView is ModelRenderable) {
            // Just need to position path from last node
            // get from list
//            anchorNodeList.find { !it.isPath }?.let {
            this.let { pose ->
                val translatedPose = when (AnchorType.valueOf(legNav.type)) {
                    AnchorType.LEFT_TURN -> pose.compose(
                        Pose.makeTranslation(
                            (pose.tx() - legNav.distance).toFloat(),
                            legNav.height.toFloat(),
                            pose.tz() - 0.5f
                        )
                    ).extractTranslation()
                    AnchorType.RIGHT_TURN -> pose.compose(
                        Pose.makeTranslation(
                            (pose.tx() + legNav.distance).toFloat(),
                            legNav.height.toFloat(),
                            pose.tz() + legNav.depth.toFloat() - 0.5f
                        )
                    ).extractTranslation()
                    AnchorType.UP_DIRECTION -> pose.compose(
                        Pose.makeTranslation(
                            (pose.tx()),
                            legNav.height.toFloat() + legNav.distance.toFloat(),
                            pose.tz() - 0.5f
                        )
                    ).extractTranslation()
                    AnchorType.FORWARD_LEFT -> pose.compose(
                        Pose.makeTranslation(
                            (pose.tx() - legNav.distance).toFloat(),
                            legNav.height.toFloat() + legNav.depth.toFloat(),
                            pose.tz() + legNav.depth.toFloat() - 0.5f
                        )
                    ).extractTranslation()
                    AnchorType.BACK_LEFT -> pose.compose(
                        Pose.makeTranslation(
                            (pose.tx() - legNav.distance).toFloat(),
                            legNav.height.toFloat() - legNav.depth.toFloat(),
                            pose.tz() + legNav.depth.toFloat() - 0.5f
                        )
                    ).extractTranslation()
                    AnchorType.FORWARD_RIGHT -> pose.compose(
                        Pose.makeTranslation(
                            (pose.tx() + legNav.distance).toFloat(),
                            legNav.height.toFloat(),
                            pose.tz() + legNav.depth.toFloat() - 0.5f
                        )
                    ).extractTranslation()
                    AnchorType.BACK_RIGHT -> pose.compose(
                        Pose.makeTranslation(
                            (pose.tx() + legNav.distance).toFloat(),
                            legNav.height.toFloat(),
                            pose.tz() + legNav.depth.toFloat()
                        )
                    ).extractTranslation()
                    else -> pose.compose(
                        Pose.makeTranslation(
                            (pose.tx()),
                            legNav.height.toFloat() - legNav.depth.toFloat(),
                            pose.tz() + legNav.depth.toFloat()
                        )
                    ).extractTranslation()
                }
                createAndAttachMarker(translatedPose, legNav, renderView)
            }
//            }

        } else {
            var markerBearing = legNav.angle - deviceOrientation.orientation

            // Bearing adjustment can be set if you are trying to
            // correct the heading of north - setBearingAdjustment(10)
            markerBearing += 10 + 360
            markerBearing %= 360
            val rotation = floor(markerBearing)
            Log.d(
                "MarkerBearing", "currentDegree " + deviceOrientation.orientation
                        + " bearing " + legNav.angle + " markerBearing " + markerBearing
                        + " rotation " + rotation + " distance " + legNav.distance
            )
            val rotationRadian = Math.toRadians(rotation)
            val zRotated = (legNav.distance * cos(rotationRadian)).toFloat()
            val xRotated = (-(legNav.distance * sin(rotationRadian))).toFloat()
            val y = legNav.height.toFloat() // fixed height
            val translatedPose = this.compose(
                Pose.makeTranslation(
                    xRotated,
                    y,
                    zRotated - 0.5f
                )
            ).extractTranslation()
            createAndAttachMarker(translatedPose, legNav, renderView)
        }


    }

    private fun createAndAttachMarker(
        translatedPose: Pose,
        legNav: LegNav,
        renderView: Renderable
    ) {
        arSceneView.session?.createAnchor(translatedPose)?.let { primaryAnchor ->
            val compassMarker = CompassMarker(
                primaryAnchor,
                legNav.distance,
                isPath = renderView is ModelRenderable,
                needRefresh = {
                    anchorsNeedRefresh
                },
                refreshDone = {
                    anchorsNeedRefresh = false
                }
            ).apply {
                name = "MyLocationNode"
                addChild(
                    Node().apply {
                        renderable = renderView
                        if (renderView is ModelRenderable) {
                            calculateLocalHeading(AnchorType.valueOf(legNav.type))?.let {
                                localRotation = it
                            }
                            localScale = Vector3(0.015f, 0.015f, 0.015f)

                        }
                    })
                setParent(arSceneView.scene)
            }
            anchorNodeList.add(compassMarker)
        }

    }

    private fun calculateLocalHeading(anchorClass: AnchorType): Quaternion? {
        return when (anchorClass) {
            AnchorType.LEFT_TURN -> {
                Quaternion.lookRotation(
                    Vector3.back(),
                    Vector3.up()
                )
            }
//            AnchorType.RIGHT_TURN -> {
//                Quaternion.lookRotation(
//                    Vector3.forward(),
//                    Vector3.up()
//                )
//            }
            AnchorType.UP_DIRECTION -> {
                Quaternion.lookRotation(
                    Vector3.forward(),
                    Vector3.left()
                )
            }
            AnchorType.DOWN_DIRECTION -> {
                Quaternion.lookRotation(
                    Vector3.forward(),
                    Vector3.right()
                )
            }
            AnchorType.FORWARD_RIGHT, AnchorType.FORWARD_LEFT -> {
                Quaternion.lookRotation(
                    Vector3.right(),
                    Vector3.up()
                )
            }
            AnchorType.BACK_RIGHT, AnchorType.BACK_LEFT -> {
                Quaternion.lookRotation(
                    Vector3.left(),
                    Vector3.up()
                )
            }
            else -> null
        }
    }

    private fun calculateDistance(x: Float, y: Float, z: Float): Float {
        return sqrt(x.pow(2) + y.pow(2) + z.pow(2))
    }

    private fun calculateDistance(objectPose0: Vector3, objectPose1: Pose): Float {
        return calculateDistance(
            objectPose0.x - objectPose1.tx(),
            objectPose0.y - objectPose1.ty(),
            objectPose0.z - objectPose1.tz()
        )
    }

    private fun changeUnit(distanceMeter: Float, unit: String): Float {
        return when (unit) {
            "cm" -> distanceMeter * 100
            "mm" -> distanceMeter * 1000
            "ft" -> 0.0328f * (distanceMeter * 100)
            else -> distanceMeter
        }
    }

    private fun showPermissionRequiredError() {
        AlertDialog.Builder(
            this@NavigationActivity,
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
                this@NavigationActivity.finish()
            }.create().show()
    }

    private fun createShopList(): List<ShopItems> {
        val mallItemList =
            Gson().fromJson(
                this.assets.readAssetsFile("itemlist.json"),
                MallItemResponse::class.java
            )
        return mallItemList.mallItems
    }
}