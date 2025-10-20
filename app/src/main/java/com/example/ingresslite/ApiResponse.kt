package com.example.ingresslite

import com.google.gson.annotations.SerializedName

// 使用泛型 <T> 让这个类可以被复用
data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: T?
)