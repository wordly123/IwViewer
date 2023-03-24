package com.rerere.iwara4a.data.model.session

import android.content.Context
import androidx.core.content.edit
import com.rerere.iwara4a.sharedPreferencesOf

class SessionManager(private val context: Context) {
    val session: Session by lazy {
        val sharedPreferences = context.sharedPreferencesOf("session")
        Session(
            sharedPreferences.getString("key", "")!!
        )
    }

    fun update(key: String) {
        session.key = key
        context.sharedPreferencesOf("session").edit {
            putString("key", key)
        }
    }
}