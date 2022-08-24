package com.bodhi.arloctiondemo.data

class QRScanCode(
    val itemCode: Int = 0,
    val navigation: List<QRScanItem> = emptyList()
) {
}

data class QRScanItem(val itemCode: Int, val legId: Int)