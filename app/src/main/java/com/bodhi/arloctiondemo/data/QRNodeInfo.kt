package com.bodhi.arloctiondemo.data

class QRNodeInfo() {
    var navigation: List<QRNodePathInfo> = emptyList()
}

class QRNodePathInfo() {
    var itemCode: Int = 0
    var path: PathNavigation? = null
}