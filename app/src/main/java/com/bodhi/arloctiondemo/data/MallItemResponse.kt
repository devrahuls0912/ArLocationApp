package com.bodhi.arloctiondemo.data

import java.io.Serializable
import kotlin.properties.Delegates

class MallItemResponse() {
    var mallItems: List<ShopItems> = emptyList()
}

class ShopItems() : Serializable {
    var itemCode by Delegates.notNull<Int>()
    var mallCode by Delegates.notNull<Int>()
    lateinit var itemName: String
    var locaton: List<Double>? = null
    var route: List<ShopLoc>? = null
    var path: List<PathNavigation>? = null
    var isSelected: Boolean = false
}

class ShopLoc() {
    lateinit var loc: List<Double>
}

class PathNavigation() {
    var pathId: Int? = 0
    var legs: List<LegNav> = emptyList()
}

class LegNav() {
    var type: String = ""
    var info: String? = ""
    var angle: Double = 0.0
    var distance: Double = 0.0
    var height: Double = 0.0
    var depth: Double = 0.0
}