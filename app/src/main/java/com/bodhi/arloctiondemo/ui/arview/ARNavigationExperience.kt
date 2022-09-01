package com.bodhi.arloctiondemo.ui.arview

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.bodhi.arloctiondemo.data.QRScanCode
import com.bodhi.arloctiondemo.databinding.NavigationArActivityBinding
import com.bodhi.arloctiondemo.location.arLocation.handleSessionException
import com.bodhi.arloctiondemo.location.arLocation.setupSession
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.ArSceneView
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage


class ARNavigationExperience : BaseLocationActivity() {

    private val binding: NavigationArActivityBinding by lazy {
        NavigationArActivityBinding.inflate(
            LayoutInflater.from(this)
        )
    }
    private val arSceneView: ArSceneView by lazy {
        binding.arSceneView
    }
    private val qrOptions by lazy {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.arSceneView.planeRenderer.isEnabled = false
        binding.arSceneView.scene.camera.nearClipPlane = 0.5f
        binding.arSceneView.scene.camera.farClipPlane = 500f

        binding.exitNow.setOnClickListener {
            exitView()
            binding.distanceToDestination.visibility = View.GONE
            binding.recalibrate.visibility = View.GONE
            binding.exitNow.visibility = View.GONE
            finish()
        }
        binding.recalibrate.setOnClickListener {
            recalibrate()
        }
        selectedItems = intent?.getSerializableExtra("listSelected") as Array<Int>

        binding.qrCode.setOnClickListener {
            arSceneView.arFrame?.acquireCameraImage()?.let {
                val image = InputImage.fromMediaImage(
                    it,
                    0
                )
                BarcodeScanning.getClient(
                    qrOptions
                )
                    .process(image)
                    .addOnSuccessListener { scann ->
                        scann?.let {
                            if (it.isNotEmpty() &&
                                it.first().displayValue?.contains("itemCode") == true
                            ) {
                                Toast.makeText(
                                    this@ARNavigationExperience,
                                    "QR code compatible trying to plot next location.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                it.first().displayValue?.let {
                                    val qrScanCode = Gson().fromJson(
                                        it,
                                        QRScanCode::class.java
                                    )
                                    populateRoute(qrScanCode.itemCode)
                                    Toast.makeText(
                                        this@ARNavigationExperience,
                                        "Populating next navigation path.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        Log.i("Barcode", it.toString())

                    }
                    .addOnFailureListener {
                        Log.i("Barcode", it.toString())
                    }
            }
        }
    }

    override fun setupSession() {
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
        initialiseLocationScene(arSceneView)
    }

    override fun giveDirectionSuggestion(suggestion: String, distance: String) {
        binding.distanceToDestination.text = distance
    }


    override fun changeViewForDestination() {
        with(binding) {
            distanceToDestination.text = "Re-calibrating..."
            recalibrate.visibility = View.VISIBLE
            exitNow.visibility = View.VISIBLE
            distanceToDestination.visibility = View.VISIBLE
            recalibrate.visibility = View.VISIBLE
            exitNow.visibility = View.VISIBLE
        }
    }

}
