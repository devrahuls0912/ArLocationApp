package com.bodhi.arloctiondemo.location.arLocation.rendering

import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3

private const val DISTANCE_LIMIT = 300

class CompassMarker(
    anchor: Anchor?,
    val distance: Double = 0.0,
    val height: Double = 0.2, // fixed for now
    val isPath: Boolean,
    val needRefresh: () -> Boolean = { true },
    val refreshDone: () -> Unit = { }
) : AnchorNode(anchor) {
    private var firstTimeRefresh = true
    override fun onUpdate(frameTime: FrameTime) {

        // Typically, getScene() will never return null because onUpdate() is only called when the node
        // is in the scene.
        // However, if onUpdate is called explicitly or if the node is removed from the scene on a
        // different thread during onUpdate, then getScene may be null.
        if (needRefresh() || firstTimeRefresh)
            updateCompassNode()
    }

    private fun updateCompassNode() {
        children.forEachIndexed { index, node ->
            if (scene == null) {
                refreshDone()
                return
            }
            val cameraPosition = scene?.camera?.worldPosition
            val nodePosition = node.worldPosition
            val direction = Vector3.subtract(cameraPosition, nodePosition)
            node.worldPosition =
                Vector3(node.worldPosition.x, height.toFloat(), node.worldPosition.z)
            val lookRotation = Quaternion.lookRotation(direction, Vector3.up())
            if (!isPath)
                node.worldRotation = lookRotation

            if (index == children.count()) {
                firstTimeRefresh = false
                refreshDone()
            }
        }
    }

}
