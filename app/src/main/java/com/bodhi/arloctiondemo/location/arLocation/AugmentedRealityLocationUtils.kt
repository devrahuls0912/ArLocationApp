package com.bodhi.arloctiondemo.location.arLocation

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import com.bodhi.arloctiondemo.R
import com.bodhi.arloctiondemo.location.arLocation.model.ArImageIndexing
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt


private const val RENDER_MARKER_MIN_DISTANCE = 2.0 // meters
private const val RENDER_MARKER_MAX_DISTANCE = 7000.0 // meters
const val INVALID_MARKER_SCALE_MODIFIER = -1F
const val INITIAL_MARKER_SCALE_MODIFIER = 0.5f

lateinit var imageDatabase: AugmentedImageDatabase
val imageDatabaseIndex: ArrayList<ArImageIndexing> = arrayListOf()

@Throws(UnavailableException::class)
fun setupSession(activity: Activity, installRequested: Boolean): Session? {
    imageDatabaseIndex.clear()
    when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
            return null
        }
        ArCoreApk.InstallStatus.INSTALLED -> {
            // just continue with session setup
        }
        else -> {
            // just continue with session setup
        }
    }

    val session = Session(activity)
    val config = Config(session)
    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
    // IMPORTANT!!!  ArSceneView requires the `LATEST_CAMERA_IMAGE` non-blocking update mode.
    imageDatabase = AugmentedImageDatabase(session)
//    val qr1 = readAssetBitmap(activity, "qrcode1.png")
//    val qr2 = readAssetBitmap(activity, "qrcode2.png")
//    val qr3 = readAssetBitmap(activity, "qrcode3.png")
//    val qr4 = readAssetBitmap(activity, "qrcode4.png")
//    val qr5 = readAssetBitmap(activity, "qrcode5.png")
//    imageDatabaseIndex.add(
//        ArImageIndexing(
//            imageDatabase.addImage("qr1", qr1),
//            "qrcode1.png"
//        )
//    )
//    imageDatabaseIndex.add(
//        ArImageIndexing(
//            imageDatabase.addImage("qr2", qr2),
//            "qrcode2.png"
//        )
//    )
//    imageDatabaseIndex.add(
//        ArImageIndexing(
//            imageDatabase.addImage("qr3", qr3),
//            "qrcode3.png"
//        )
//    )
//    imageDatabaseIndex.add(
//        ArImageIndexing(
//            imageDatabase.addImage("qr4", qr4),
//            "qrcode4.png"
//        )
//    )
//    imageDatabaseIndex.add(
//        ArImageIndexing(
//            imageDatabase.addImage("qr5", qr5),
//            "qrcode5.png"
//        )
//    )
    config.focusMode = Config.FocusMode.AUTO
    config.depthMode = Config.DepthMode.DISABLED
    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
//    config.augmentedImageDatabase = imageDatabase
    session.configure(config)
    return session
}
/*private fun Pose.createCompassMarkerPose(
    heading: Double,
    distance: Double,
    height: Double,
    renderView: Renderable,
    anchorClass: AnchorType
) {
    var markerBearing = heading - deviceOrientation.orientation

    // Bearing adjustment can be set if you are trying to
    // correct the heading of north - setBearingAdjustment(10)
    markerBearing += 10 + 360
    markerBearing %= 360
    val rotation = floor(markerBearing)
    Log.d(
        "MarkerBearing", "currentDegree " + deviceOrientation.orientation
                + " bearing " + heading + " markerBearing " + markerBearing
                + " rotation " + rotation + " distance " + distance
    )
    val rotationRadian = Math.toRadians(rotation)
    val zRotated = (distance * cos(rotationRadian)).toFloat()
    val xRotated = (-(distance * sin(rotationRadian))).toFloat()
    val y = height.toFloat() // fixed height
    val translatedPose = this.compose(
        Pose.makeTranslation(
            xRotated, y, zRotated
        )
    ).extractTranslation()

    val primaryAnchor = arSceneView.session?.createAnchor(translatedPose)
    val compassMarker = CompassMarker(
        primaryAnchor,
        distance,
        isPath = renderView is ModelRenderable,
        needRefresh = {
            anchorsNeedRefresh
        },
        refreshDone = {
            anchorsNeedRefresh = false
//                mHandler.removeCallbacks(anchorRefreshTask)
        }
    ).apply {

        *//* Left - Default is right
        Quaternion.lookRotation(
                        Vector3.back(),
                        Vector3.up()
                    )
                    *//*
        *//* Top - Default is right
        Quaternion.lookRotation(
                        Vector3.forward(),
                        Vector3.left()
                    )
                    *//*
        *//* Down - Default is right
     Quaternion.lookRotation(
                        Vector3.forward(),
                        Vector3.right()
                    )
                    *//*
        name = "MyLocationNode"
        addChild(
            Node().apply {
                renderable = renderView
                if (renderView is ModelRenderable) {
                    when (anchorClass) {
                        AnchorType.LEFT_TURN -> {
                            localRotation = Quaternion.lookRotation(
                                Vector3.back(),
                                Vector3.up()
                            )
                        }
                        AnchorType.UP_DIRECTION -> {
                            localRotation = Quaternion.lookRotation(
                                Vector3.forward(),
                                Vector3.left()
                            )
                        }
                        AnchorType.DOWN_DIRECTION -> {
                            localRotation = Quaternion.lookRotation(
                                Vector3.forward(),
                                Vector3.right()
                            )
                        }
                        AnchorType.FORWARD_RIGHT, AnchorType.FORWARD_LEFT -> {
                            localRotation = Quaternion.lookRotation(
                                Vector3.forward(),
                                Vector3.up()
                            )
                        }
                        AnchorType.BACK_RIGHT, AnchorType.BACK_LEFT -> {
                            localRotation = Quaternion.lookRotation(
                                Vector3.back(),
                                Vector3.right()
                            )
                        }
                        else -> {}
                    }
                    localScale = Vector3(0.015f, 0.015f, 0.015f)

                }
            })
        setParent(arSceneView.scene)
    }
    anchorNodeList.add(compassMarker)
}*/

fun readAssetBitmap(activity: Activity, name: String): Bitmap? {
    return try {
        // get input stream
        val ims: InputStream = activity.assets.open(name)
        val d = Drawable.createFromStream(ims, null)
        d.toBitmap(300, 300, Bitmap.Config.ARGB_8888)
    } catch (ex: IOException) {
        null
    }
}

fun handleSessionException(activity: Activity, sessionException: UnavailableException) {
    val message = when (sessionException) {
        is UnavailableArcoreNotInstalledException ->
            activity.resources.getString(R.string.arcore_not_installed)

        is UnavailableUserDeclinedInstallationException ->
            activity.resources.getString(R.string.arcore_not_installed)

        is UnavailableApkTooOldException ->
            activity.resources.getString(R.string.arcore_not_updated)

        is UnavailableSdkTooOldException ->
            activity.resources.getString(R.string.arcore_not_supported)

        else -> activity.resources.getString(R.string.arcore_not_supported)
    }
    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
}

fun getScaleModifierBasedOnRealDistance(distance: Double): Float {
    return when (distance) {

        in Double.MIN_VALUE..RENDER_MARKER_MIN_DISTANCE -> INVALID_MARKER_SCALE_MODIFIER
        in RENDER_MARKER_MIN_DISTANCE + 1..5.0 -> 0.9f
        in 5.1..10.0 -> 0.885f
        in 10.1..15.0 -> 0.865f
        in 15.1..20.0 -> 0.850f
        in 20.1..25.0 -> 0.825f
        in 25.1..30.0 -> 0.7f
        in 30.1..35.0 -> 0.785f
        in 35.1..40.0 -> 0.765f
        in 41.1..45.0 -> 0.750f
        in 45.1..50.0 -> 0.725f
        in 50.1..55.0 -> 0.6f
        in 55.1..60.0 -> 0.685f
        in 60.1..65.0 -> 0.650f
        in 65.1..70.0 -> 0.625f
        in 70.1..75.0 -> 0.5f
        in 75.1..80.0 -> 0.585f
        in 80.1..85.0 -> 0.550f
        in 85.1..90.0 -> 0.525f
        in 90.1..95.0 -> 0.4f
        in 95.1..100.0 -> 0.485f
        in 100.1..105.0 -> 0.465f
        in 100.1..500.0 -> 0.450f
        in 500.1..1000.0 -> 0.425f
        in 1001.0..1500.0 -> 0.3f
        in 1501.0..2000.0 -> 0.385f
        in 2001.0..2500.0 -> 0.355f
        in 2501.0..3000.0 -> 0.280f
        in 3001.0..RENDER_MARKER_MAX_DISTANCE -> 0.25f
        in RENDER_MARKER_MAX_DISTANCE + 1..Integer.MAX_VALUE.toDouble() -> 0.15f
        else -> -1f
    }
}

fun generateRandomHeightBasedOnDistance(distance: Int): Float {
    return when (distance) {
        in 0..1000 -> (1..3).random().toFloat()
        in 0..1000 -> (1..3).random().toFloat()
        in 1001..1500 -> (4..6).random().toFloat()
        in 1501..2000 -> (7..9).random().toFloat()
        in 2001..3000 -> (10..12).random().toFloat()
        in 3001..RENDER_MARKER_MAX_DISTANCE.toInt() -> (12..13).random().toFloat()
        else -> 0f
    }
}

fun showDistance(distance: Double): String {
    return if (distance >= 1000)
        String.format("%.2f", (distance.toDouble() / 1000)) + " km"
    else
        "${(distance * 10.0).roundToInt() / 10.0} m"
}
