package com.bodhi.arloctiondemo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

fun AssetManager.readAssetsFile(
    fileName: String
): String = open(fileName).bufferedReader().use { it.readText() }

fun mapShopItemImageDrawable(context: Context, itemId: Int): Drawable? {
    return when (itemId) {
        1 -> ContextCompat.getDrawable(context, R.drawable.groceries)
        2 -> ContextCompat.getDrawable(context, R.drawable.dry_fruits)
        3 -> ContextCompat.getDrawable(context, R.drawable.veggies)
        else -> ContextCompat.getDrawable(context, R.drawable.drinks)
    }

}

private const val CAMERA_PERMISSION_CODE = 0
private const val MULTIPLE_PERMISSION_CODE = 1
const val CAMERA_PERMISSION = Manifest.permission.CAMERA
val permissions =
    arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

fun hasLocationAndCameraPermissions(activity: Activity): Boolean {
    permissions.forEach { permission ->
        if (ActivityCompat.checkSelfPermission(
                activity,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }
    return true
}

fun hasCameraPermission(activity: Activity): Boolean {
    return (ActivityCompat.checkSelfPermission(
        activity,
        CAMERA_PERMISSION
    ) == PackageManager.PERMISSION_GRANTED)
}

fun requestCameraPermission(activity: Activity) {
    ActivityCompat.requestPermissions(
        activity, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_CODE
    )
}

fun requestCameraAndLocationPermissions(activity: Activity) {
    ActivityCompat.requestPermissions(
        activity, permissions, MULTIPLE_PERMISSION_CODE
    )
}

fun launchPermissionSettings(activity: Activity) {
    val intent = Intent()
    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    intent.data = Uri.fromParts("package", activity.packageName, null)
    activity.startActivity(intent)
}