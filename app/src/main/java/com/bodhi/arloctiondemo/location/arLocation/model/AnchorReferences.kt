package com.bodhi.arloctiondemo.location.arLocation.model

data class AnchorReferences(
    val itemCode: Int,
    val mallCode: Int,
    val anchor: List<AnchorReferenceItems>
) {
}

data class AnchorReferenceItems(
    val type: Int,
    val count: Int,
    val distance: Int
)