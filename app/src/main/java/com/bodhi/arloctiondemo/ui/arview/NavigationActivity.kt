package com.bodhi.arloctiondemo.ui.arview

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bodhi.arloctiondemo.ArViewModel
import com.bodhi.arloctiondemo.R
import com.bodhi.arloctiondemo.data.LegNav
import com.bodhi.arloctiondemo.databinding.NavigationArActivityBinding
import com.bodhi.arloctiondemo.location.Utils
import com.bodhi.arloctiondemo.location.arLocation.handleSessionException
import com.bodhi.arloctiondemo.location.arLocation.rendering.CompassMarker
import com.bodhi.arloctiondemo.location.arLocation.sensor.DeviceOrientation
import com.bodhi.arloctiondemo.location.arLocation.setupSession
import com.bodhi.arloctiondemo.mapShopItemImageDrawable
import com.bodhi.arloctiondemo.ui.adapter.MultiItemSelectionAdapter
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.jackandphantom.carouselrecyclerview.CarouselLayoutManager
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.launch

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
    private var shouldProcessFrame = false

    private val selectedItemCodeList: ArrayList<Int> by lazy {
        intent.getSerializableExtra("selectedItemCodeList") as ArrayList<Int>
    }

    private var anchorRefreshTask: Runnable = object : Runnable {
        override fun run() {
            anchorsNeedRefresh = true
            mHandler.postDelayed(this, arAnchorRefreshInterval.toLong())
        }
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
    private val viewModel: ArViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setUpObservable()
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
        binding.navigateToSelectedItem.setOnClickListener {
            // before plotting hide selection list and button
            with(binding) {
                navigateToSelectedItem.visibility = View.GONE
                selectedItemList.visibility = View.GONE
                qrCode.visibility = View.VISIBLE
                exitNow.visibility = View.VISIBLE
                headingSelectedNavigationInfo.visibility = View.VISIBLE
                headingSelectedNavigationInfo.text = ""
            }
            shouldProcessFrame = true
            binding.headingSelectedNavigationInfo.text = "Scan QR code to navigate to ${
                viewModel.getSelectedItemName() ?: "Selected Item"
            }"
        }
        if (selectedItemCodeList.size > 1) {
            //here update the temp array to remove item from selection list
            with(binding) {
                navigateToSelectedItem.visibility = View.VISIBLE
                selectedItemList.visibility = View.VISIBLE
                qrCode.visibility = View.GONE
                exitNow.visibility = View.GONE
                headingSelectedNavigationInfo.visibility = View.GONE
            }
        } else {
            with(binding) {
                navigateToSelectedItem.visibility = View.GONE
                selectedItemList.visibility = View.GONE
                qrCode.visibility = View.VISIBLE
                exitNow.visibility = View.VISIBLE
                headingSelectedNavigationInfo.visibility = View.VISIBLE
            }
        }
        viewModel.createShopItems()
    }

    private fun setUpObservable() {
        viewModel.lastSyncedQRItemObservable().observe(this) {
            it?.let {
                if ((viewModel.shouldShowSelection() ||
                            it.any { AnchorType.valueOf(it.type) == AnchorType.DESTINATION }) &&
                    selectedItemCodeList.size > 1
                ) {
                    //here update the temp array to remove item from selection list
                    with(binding) {
                        navigateToSelectedItem.visibility = View.VISIBLE
                        selectedItemList.visibility = View.VISIBLE
                        qrCode.visibility = View.GONE
                        exitNow.visibility = View.GONE
                        headingSelectedNavigationInfo.visibility = View.GONE
                    }
                } else {
                    with(binding) {
                        navigateToSelectedItem.visibility = View.GONE
                        selectedItemList.visibility = View.GONE
                        qrCode.visibility = View.VISIBLE
                        exitNow.visibility = View.VISIBLE
                        headingSelectedNavigationInfo.visibility = View.VISIBLE
                    }
                }
                plotLastSyncedQrSelectedItem(it)
            }
        }
        viewModel.getSelectedItemListingObservable(selectedItemCodeList).observe(this) { shopList ->
            shopList?.let {
                viewModel.currentSelectedItemId = it.first().itemCode

                if (selectedItemCodeList.size > 1) // more than one item selected
                    binding.selectedItemList.apply {
                        adapter = MultiItemSelectionAdapter(it)
                        set3DItem(true)
                        setAlpha(true)
                        setItemSelectListener(object : CarouselLayoutManager.OnSelected {
                            override fun onItemSelected(position: Int) {
                                // clear and plot navigation for selected position
                                viewModel.updateCurrentSelectedItemId(position)
                            }
                        })
                    }
                else { // no need to show selection
                    shouldProcessFrame = true
                    binding.headingSelectedNavigationInfo.text = "Scan QR code to navigate to ${
                        viewModel.getSelectedItemName() ?: "Selected Item"
                    }"
                }
            }

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
                        barCode.first().displayValue?.contains("nodeId") == true
                    ) {
                        barCode.first().displayValue?.let {
                            viewModel.syncedQRCode(it)
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

    private fun plotLastSyncedQrSelectedItem(navigationList: List<LegNav>) {
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
        navigationList.forEach {
            createPrimaryMarker(
                it,
                viewModel.getSelectedItemName() ?: "",
                viewModel.currentSelectedItemId
            )
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

    private fun Pose.createCompassMarkerPose(
        legNav: LegNav,
        renderView: Renderable
    ) {
        if (renderView is ModelRenderable) {
            this.let { pose ->
                val translatedPose = viewModel.translateNavigationPose(legNav, pose)
                createAndAttachMarker(translatedPose, legNav, renderView)
            }
        } else {
            val translatedPose = viewModel.translateAnglePose(
                legNav,
                this,
                deviceOrientation.orientation
            )
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
                            viewModel.calculateLocalHeading(
                                AnchorType.valueOf(legNav.type)
                            )?.let {
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
}