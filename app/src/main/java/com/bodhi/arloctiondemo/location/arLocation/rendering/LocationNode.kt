package com.bodhi.arloctiondemo.location.arLocation.rendering

import com.bodhi.arloctiondemo.location.arLocation.LocationMarker
import com.bodhi.arloctiondemo.location.arLocation.LocationScene
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.maps.android.SphericalUtil
import kotlin.math.sin
import kotlin.math.sqrt

class LocationNode(
    anchor: Anchor?,
    val locationMarker: LocationMarker,
    private val locationScene: LocationScene
) : AnchorNode(anchor) {
    private val TAG = "LocationNode"
    var renderEvent: LocationNodeRender? = null
    var distance = 0.0
    var distanceInAR = 0.0
    var scaleModifier = 1f
    var height = 0f
    var gradualScalingMinScale = 0.8f
    var gradualScalingMaxScale = 1.4f
    var scalingMode = LocationMarker.ScalingMode.FIXED_SIZE_ON_SCREEN
    override fun onUpdate(frameTime: FrameTime) {

        // Typically, getScene() will never return null because onUpdate() is only called when the node
        // is in the scene.
        // However, if onUpdate is called explicitly or if the node is removed from the scene on a
        // different thread during onUpdate, then getScene may be null.
        for (n in children) {
            if (scene == null) {
                return
            }
            val cameraPosition = scene?.camera?.worldPosition
            val nodePosition = n.worldPosition

            // Compute the difference vector between the camera and anchor
            cameraPosition?.let {

                val dx = it.x - nodePosition.x
                val dy = it.y - nodePosition.y
                val dz = it.z - nodePosition.z

                // Compute the straight-line distance.
                distanceInAR = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                if (locationScene.shouldOffsetOverlapping()) {
                    if (locationScene.mArSceneView.scene.overlapTestAll(n).size > 0) {
                        height += 1.2f
                    }
                }
                if (locationScene.shouldRemoveOverlapping()) {
                    val ray = Ray()
                    ray.origin = it
                    val xDelta = (distanceInAR * sin(Math.PI / 15)).toFloat() // 12 degrees
                    scene?.camera?.left?.normalized()?.let { cameraLeft ->
                        val left = Vector3.add(nodePosition, cameraLeft.scaled(xDelta))
                        val right = Vector3.add(nodePosition, cameraLeft.scaled(-xDelta))
                        val isOverlapping = (
                                isOverlapping(n, ray, left, it) ||
                                        isOverlapping(n, ray, nodePosition, it) ||
                                        isOverlapping(n, ray, right, it)
                                )
                        isEnabled = !isOverlapping
                    }
                }
            }
        }
        if (!locationScene.minimalRefreshing())
            scaleAndRotate()
        if (renderEvent != null) {
            if (this.isTracking && this.isActive && this.isEnabled)
                renderEvent?.render(this)
        }
    }

    private fun isOverlapping(
        n: Node,
        ray: Ray,
        target: Vector3,
        cameraPosition: Vector3
    ): Boolean {
        val nodeDirection = Vector3.subtract(target, cameraPosition)
        ray.direction = nodeDirection
        val hitTestResults = locationScene.mArSceneView.scene.hitTestAll(ray)
        if (hitTestResults.size > 0) {
            var closestHit: HitTestResult? = null
            for (hit in hitTestResults) {
                // Get the closest hit on enabled Node
                if (hit.node != null && hit.node!!.isEnabled) {
                    closestHit = hit
                    break
                }
            }

            // if closest hit is not the current node, it is hidden behind another node that is closer
            return closestHit != null && closestHit.node !== n
        }
        return false
    }

    fun scaleAndRotate() {
        for (n in children) {
            val markerDistance = SphericalUtil.computeDistanceBetween(
                LatLng(
                    locationMarker.location.latitude,
                    locationMarker.location.longitude
                ),
                LatLng(
                    locationScene.deviceLocation?.currentBestLocation!!.latitude,
                    locationScene.deviceLocation?.currentBestLocation!!.longitude
                )
            )

            distance = markerDistance

            // Limit the distance of the Anchor within the scene.
            // Prevents uk.co.appoly.arcorelocation.rendering issues.
            var renderDistance = markerDistance
            if (renderDistance > locationScene.distanceLimit)
                renderDistance = locationScene.distanceLimit.toDouble()
            var scale = 1.0f
            val cameraPosition = scene!!.camera.worldPosition
            val direction = Vector3.subtract(cameraPosition, n.worldPosition)
            when (scalingMode) {
                LocationMarker.ScalingMode.FIXED_SIZE_ON_SCREEN ->
                    scale =
                        sqrt(
                            (
                                    direction.x * direction.x +
                                            direction.y * direction.y + direction.z * direction.z
                                    ).toDouble()
                        ).toFloat()
                LocationMarker.ScalingMode.GRADUAL_TO_MAX_RENDER_DISTANCE -> {
                    val scaleDifference = gradualScalingMaxScale - gradualScalingMinScale
                    scale = (
                            gradualScalingMinScale +
                                    (locationScene.distanceLimit - markerDistance.toFloat()) *
                                    (scaleDifference / locationScene.distanceLimit)
                            ) * renderDistance.toFloat()
                }
                LocationMarker.ScalingMode.GRADUAL_FIXED_SIZE -> {
                    scale =
                        sqrt(
                            (
                                    direction.x * direction.x +
                                            direction.y * direction.y + direction.z * direction.z
                                    ).toDouble()
                        )
                            .toFloat()
                    var gradualScale = gradualScalingMaxScale - gradualScalingMinScale
                    gradualScale = gradualScalingMaxScale - gradualScale /
                            (renderDistance * markerDistance).toFloat()
                    scale *= gradualScale.coerceAtLeast(gradualScalingMinScale)
                }
                else -> {}
            }
            scale *= scaleModifier

            // Log.d("LocationScene", "scale " + scale);
            n.worldPosition = Vector3(n.worldPosition.x, height, n.worldPosition.z)
            val lookRotation = Quaternion.lookRotation(direction, Vector3.up())
            n.worldRotation = lookRotation
            n.worldScale = Vector3(scale, scale, scale)
        }
    }
}
