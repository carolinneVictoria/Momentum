package com.carolinne.momentum.baseclasses

import java.io.Serializable

data class Item(
    var endereco: String? = null,
    var tarefa: String? = null,
    var statusTarefa: String? = null,
    val base64Image: String? = null,
    val imageUrl: String? = null

) : Serializable //
