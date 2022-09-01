package com.bodhi.arloctiondemo.data

class QRScanCode(
    val itemCode: Int = 0,
    val navigation: List<QRScanItem> = emptyList(),
    val nodeId: String = ""
) {
}

data class QRScanItem(val itemCode: Int, val pathId: Int)