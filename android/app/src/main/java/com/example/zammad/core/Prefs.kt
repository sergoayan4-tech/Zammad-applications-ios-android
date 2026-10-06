package com.example.zammad.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** App settings persisted in SharedPreferences. Language/theme are state-backed. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("zammad", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_SERVER, value).apply()
        }

    var token: String
        get() = sp.getString(KEY_TOKEN, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_TOKEN, value).apply()
        }

    var login: String
        get() = sp.getString(KEY_LOGIN, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_LOGIN, value).apply()
        }

    var password: String
        get() = sp.getString(KEY_PASSWORD, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_PASSWORD, value).apply()
        }

    private var _language by mutableStateOf(sp.getString(KEY_LANGUAGE, "system") ?: "system")

    /** "system" | "ru" | "en" */
    var language: String
        get() = _language
        set(value) {
            _language = value
            sp.edit().putString(KEY_LANGUAGE, value).apply()
        }

    private var _theme by mutableStateOf(sp.getString(KEY_THEME, "system") ?: "system")

    /** "system" | "light" | "dark" */
    var theme: String
        get() = _theme
        set(value) {
            _theme = value
            sp.edit().putString(KEY_THEME, value).apply()
        }

    fun clearCredentials() {
        sp.edit()
            .remove(KEY_SERVER)
            .remove(KEY_TOKEN)
            .remove(KEY_LOGIN)
            .remove(KEY_PASSWORD)
            .apply()
    }

    /** Effective language code: explicit choice or the system locale. */
    fun resolveLanguage(): String {
        val stored = _language
        if (stored == "ru" || stored == "en") return stored
        return if (Locale.getDefault().language == "ru") "ru" else "en"
    }

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_TOKEN = "token"
        const val KEY_LOGIN = "login"
        const val KEY_PASSWORD = "password"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
    }
}
