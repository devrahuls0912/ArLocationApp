package com.bodhi.arloctiondemo

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.bodhi.arloctiondemo.data.*
import com.bodhi.arloctiondemo.ui.arview.AnchorType
import com.google.ar.core.Pose
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlin.math.*

class ArViewModel : ViewModel() {
    private val mallItemResponse: MutableLiveData<MallItemResponse?> = MutableLiveData()
    fun getItemListingObservable(): LiveData<List<ShopItems>?> = mallItemResponse.map {
        it?.mallItems ?: emptyList()
    }

    private var selectedShopItems: List<ShopItems> = listOf()
    fun getSelectedItemListingObservable(
        selectedItemCodeList: ArrayList<Int>
    ): LiveData<List<ShopItems>?> =
        mallItemResponse.map {
            selectedShopItems = it?.mallItems?.filter { shopList ->
                selectedItemCodeList.any {
                    shopList.itemCode == it
                }
            } ?: listOf()
            selectedShopItems
        }

    private val lastSyncedQRItem: MutableLiveData<QRNodeInfo?> = MutableLiveData()
    fun lastSyncedQRItemObservable(): LiveData<List<LegNav>> = lastSyncedQRItem.map {
        it?.navigation?.find {
            it.itemCode == currentSelectedItemId
        }?.path?.legs ?: emptyList()
    }

    var currentSelectedItemId = -1 //setting code initially as SELECT_NONE(-1)
    fun createShopItems(mallId: String = "1") {
        Firebase.firestore.collection(
            "ItemListing"
        ).document(
            mallId
        ).get().addOnSuccessListener { document ->
            document?.let {
                val mallItemList = it.toObject(MallItemResponse::class.java)
                mallItemResponse.postValue(mallItemList)
            }
        }.addOnFailureListener {
            mallItemResponse.postValue(null)
        }
    }

    fun syncedQRCode(displayValue: String) {
        val qrscan = Gson().fromJson(
            displayValue,
            QRScanCode::class.java
        )
        Firebase.firestore.collection(
            "node"
        )
            .document(qrscan.nodeId)
            .get()
            .addOnSuccessListener { document ->
                document?.let {
                    val qrNodeInfo = it.toObject(QRNodeInfo::class.java)
                    lastSyncedQRItem.postValue(qrNodeInfo)
                }
            }
            .addOnFailureListener {
                lastSyncedQRItem.value = null
            }
    }

    fun shouldShowSelection(): Boolean {
        return currentSelectedItemId == -1
        //|| append condition to check the temp array :TO-DO
        // previously plotted path is traversed and destination shown
        // and next item is ready to be selected

    }

    fun getSelectedItemName(): String? {
        return selectedShopItems.find { it.itemCode == currentSelectedItemId }?.itemName
    }

    fun updateCurrentSelectedItemId(position: Int) {
        currentSelectedItemId = selectedShopItems[position].itemCode
    }

    fun changeUnit(distanceMeter: Float, unit: String): Float {
        return when (unit) {
            "cm" -> distanceMeter * 100
            "mm" -> distanceMeter * 1000
            "ft" -> 0.0328f * (distanceMeter * 100)
            else -> distanceMeter
        }
    }

    fun calculateDistance(objectPose0: Vector3, objectPose1: Pose): Float {
        return calculateDistance(
            objectPose0.x - objectPose1.tx(),
            objectPose0.y - objectPose1.ty(),
            objectPose0.z - objectPose1.tz()
        )
    }

    private fun calculateDistance(x: Float, y: Float, z: Float): Float {
        return sqrt(x.pow(2) + y.pow(2) + z.pow(2))
    }

    fun calculateLocalHeading(anchorClass: AnchorType): Quaternion? {
        return when (anchorClass) {
            AnchorType.LEFT_TURN -> {
                Quaternion.lookRotation(
                    Vector3.back(),
                    Vector3.up()
                )
            }
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

    fun translateNavigationPose(legNav: LegNav, pose: Pose): Pose =
        when (AnchorType.valueOf(legNav.type)) {
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
                    legNav.height.toFloat(),
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

    fun translateAnglePose(
        legNav: LegNav,
        pose: Pose,
        deviceOrientation: Float
    ): Pose {
        var markerBearing = legNav.angle - deviceOrientation

        // Bearing adjustment can be set if you are trying to
        // correct the heading of north - setBearingAdjustment(10)
        markerBearing += 10 + 360
        markerBearing %= 360
        val rotation = floor(markerBearing)
        Log.d(
            "MarkerBearing", "currentDegree " + deviceOrientation
                    + " bearing " + legNav.angle + " markerBearing " + markerBearing
                    + " rotation " + rotation + " distance " + legNav.distance
        )
        val rotationRadian = Math.toRadians(rotation)
        val zRotated = (legNav.distance * cos(rotationRadian)).toFloat()
        val xRotated = (-(legNav.distance * sin(rotationRadian))).toFloat()
        val y = legNav.height.toFloat() // fixed height
        return pose.compose(
            Pose.makeTranslation(
                xRotated,
                y,
                zRotated - 0.5f
            )
        ).extractTranslation()
    }

}