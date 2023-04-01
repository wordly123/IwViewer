package com.rerere.iwara4a.data.model.user

data class Self(
    val id: String,
    val name: String,
    val profilePic: String,
    val about: String? = null,
    val friendRequest: Int = 0,
    val messages: Int = 0,
    val notifications: Int = 0
) {
    companion object {
        val GUEST = Self("", "访客", "https://www.iwara.tv/images/default-avatar.jpg")
    }
}