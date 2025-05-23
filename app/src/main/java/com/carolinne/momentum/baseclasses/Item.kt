package com.carolinne.momentum.baseclasses

data class Item(
    var tarefa: String? = null,
    var statusTarefa: String? = null,
    val base64Image: String? = null,
    val imageUrl: String? = null
)
