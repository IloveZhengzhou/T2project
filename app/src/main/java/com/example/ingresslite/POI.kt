package com.example.ingresslite

import com.google.gson.annotations.SerializedName

data class POI(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("latitude")
    val latitude: Double,

    @SerializedName("longitude")
    val longitude: Double,

    // 🌟 修改这里！
    @SerializedName("owner")
    val owner: String? // 字段名改为 owner
)