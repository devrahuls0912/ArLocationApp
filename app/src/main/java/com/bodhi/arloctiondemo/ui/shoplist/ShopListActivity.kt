package com.bodhi.arloctiondemo.ui.shoplist

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bodhi.arloctiondemo.data.MallItemResponse
import com.bodhi.arloctiondemo.data.QRScanCode
import com.bodhi.arloctiondemo.data.ShopItems
import com.bodhi.arloctiondemo.databinding.ActivityShopItemListingBinding
import com.bodhi.arloctiondemo.location.ar.view.NavigationActivity
import com.bodhi.arloctiondemo.readAssetsFile
import com.bodhi.arloctiondemo.ui.adapter.ShopItemAdapter
import com.bodhi.arloctiondemo.ui.login.LoginActivity
import com.google.firebase.auth.FirebaseAuth
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
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ShopListActivity : AppCompatActivity() {
    private val binding: ActivityShopItemListingBinding by lazy {
        ActivityShopItemListingBinding.inflate(
            LayoutInflater.from(
                this
            )
        )
    }
    private val qrOptions by lazy {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
    }

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            handlePostProcessingCapture()
        }
    }

    private fun handlePostProcessingCapture() {

        lifecycleScope.launchWhenResumed {
            delay(500)
            val bitmap = BitmapFactory.decodeFile(currentPhotoFile.absolutePath)
            val exif = ExifInterface(currentPhotoFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            );

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            val copyBm = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            bitmap.recycle()
            /* Scan barcode */
            BarcodeScanning.getClient(
                qrOptions
            )
                .process(
                    InputImage.fromBitmap(copyBm, 0)
                )
                .addOnSuccessListener { scann ->
                    scann?.let {
                        if (it.isNotEmpty() &&
                            it.first().displayValue?.contains("itemCode") == true
                        ) {
                            Toast.makeText(
                                this@ShopListActivity,
                                "Populating Destination on Ar View",
                                Toast.LENGTH_SHORT
                            ).show()
                            it.first().displayValue?.let {
                                val qrScanCode = Gson().fromJson(
                                    it,
                                    QRScanCode::class.java
                                )
                                val filteredList = shopItems.filter {
                                    qrScanCode.itemCode == it.itemCode
                                }
                                if (filteredList.isNotEmpty()) {
                                    startActivity(
                                        Intent(
                                            this@ShopListActivity,
                                            NavigationActivity::class.java
                                        ).apply {
                                            putExtra(
                                                "listSelected",
                                                listOf(
                                                    filteredList.first().itemCode
                                                ).toTypedArray()
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this@ShopListActivity,
                        "It is not a proper barcode.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private val shopItems: List<ShopItems> by lazy {
        createShopItems()
    }
    private val currentPhotoFile: File by lazy {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
    private var selectedShopItemList: List<ShopItems> = emptyList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""

        with(binding) {
            listitems.layoutManager = GridLayoutManager(this@ShopListActivity, 2)
            listitems.adapter = ShopItemAdapter(shopItems) {
                selectedShopItemList = it
                if (selectedShopItemList.isNotEmpty())
                    shopButton.visibility = View.VISIBLE
                else shopButton.visibility = View.GONE
            }
            logout.setOnClickListener {
                FirebaseAuth.getInstance().signOut()
                startActivity(
                    Intent(
                        this@ShopListActivity,
                        LoginActivity::class.java
                    )
                )
                finish()
            }

            binding.scanQr.setOnClickListener {
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                    // Ensure that there's a camera activity to handle the intent
                    takePictureIntent.resolveActivity(packageManager)?.also {
                        // Create the File where the photo should go
                        val photoFile: File? = try {
                            currentPhotoFile
                        } catch (ex: IOException) {
                            null
                        }
                        // Continue only if the File was successfully created
                        photoFile?.also {
                            val photoURI: Uri = FileProvider.getUriForFile(
                                this@ShopListActivity,
                                "com.bodhi.ardemo.android.fileprovider",
                                it
                            )
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                            startForResult.launch(takePictureIntent)
                        }
                    }
                }
            }

            shopButton.setOnClickListener {
                startActivity(
                    Intent(
                        this@ShopListActivity,
                        NavigationActivity::class.java
                    ).apply {
                        putExtra(
                            "listSelected",
                            selectedShopItemList.first().itemCode
                        )
                    }
                )
            }
        }
    }

    private fun createShopItems(): List<ShopItems> {
        val mallItemList =
            Gson().fromJson(
                this.assets.readAssetsFile("itemlist.json"),
                MallItemResponse::class.java
            )
        return mallItemList.mallItems
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        Dexter.withActivity(this@ShopListActivity)
            .withPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if ((report?.grantedPermissionResponses?.size ?: 0) > 1) {
                        //Nothing to do
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

    private fun showPermissionRequiredError() {
        AlertDialog.Builder(
            this@ShopListActivity,
            androidx.appcompat.R.style.AlertDialog_AppCompat_Light
        )
            .setTitle("Permission Error")
            .setMessage(
                "Please allow Location,Camera and storage permission from settings to " +
                        "use this feature."
            )
            .setPositiveButton(
                "Ok"
            ) { dialog, which ->
                dialog.dismiss()
                this@ShopListActivity.finish()
            }.create().show()
    }

    override fun onBackPressed() {
        finish()
    }
}