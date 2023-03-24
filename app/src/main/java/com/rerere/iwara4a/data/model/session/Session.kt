package com.rerere.iwara4a.data.model.session


data class Session(
    var key: String,
) {
    fun isNotEmpty() = key.isNotEmpty()

    fun getToken()  = "Bearer $key"
}