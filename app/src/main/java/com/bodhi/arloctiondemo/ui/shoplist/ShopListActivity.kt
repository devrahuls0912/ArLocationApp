package com.bodhi.arloctiondemo.ui.shoplist

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bodhi.arloctiondemo.data.MallItemResponse
import com.bodhi.arloctiondemo.data.ShopItems
import com.bodhi.arloctiondemo.databinding.ActivityShopItemListingBinding
import com.bodhi.arloctiondemo.location.ar.view.NavigationActivity
import com.bodhi.arloctiondemo.readAssetsFile
import com.bodhi.arloctiondemo.ui.adapter.ShopItemAdapter
import com.bodhi.arloctiondemo.ui.login.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.util.*

class ShopListActivity : AppCompatActivity() {
    private val binding: ActivityShopItemListingBinding by lazy {
        ActivityShopItemListingBinding.inflate(
            LayoutInflater.from(
                this
            )
        )
    }

    private lateinit var adapter: ShopItemAdapter

    private val shopItems: List<ShopItems> by lazy {
        createShopItems()
    }

    private var selectedShopItemList: List<ShopItems> = emptyList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""

        with(binding) {
            listitems.layoutManager = GridLayoutManager(this@ShopListActivity, 2)
            adapter = ShopItemAdapter(shopItems) {
                selectedShopItemList = it
                if (selectedShopItemList.isNotEmpty())
                    shopButton.visibility = View.VISIBLE
                else shopButton.visibility = View.INVISIBLE
            }
            listitems.adapter = adapter
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

            searchET.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun afterTextChanged(editable: Editable?) {
                    filter(editable.toString())
                }

            })

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

    private fun filter(searchText: String){
        val filteredList = arrayListOf<ShopItems>()
        for (item in shopItems) {
            if(item.itemName.lowercase(Locale.getDefault()).contains(searchText.lowercase(Locale.getDefault()))){
                filteredList.add(item)
            }
        }
        adapter.filterList(filteredList)
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