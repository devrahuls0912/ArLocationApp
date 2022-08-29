package com.bodhi.arloctiondemo.data

import java.io.Serializable

data class MallItemResponse(
    val mallItems: List<ShopItems>
) {
}

data class ShopItems(
    val itemCode: Int,
    val mallCode: Int,
    val itemName: String,
    val locaton: List<Double>,
    val route: List<ShopLoc>?,
    val path: List<PathNavigation>?,
    var isSelected: Boolean = false
) : Serializable

data class ShopLoc(val loc: List<Double>)
data class PathNavigation(val pathId: Int, val legs: List<LegNav>)
data class LegNav(
    val type: String,
    val info: String? = "",
    val angle: Double,
    val distance: Double,
    val height: Double,
    val depth: Double

)