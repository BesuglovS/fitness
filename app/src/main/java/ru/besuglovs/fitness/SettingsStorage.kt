package ru.besuglovs.fitness

import android.content.Context
import android.content.SharedPreferences

class SettingsStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("fitness_settings", Context.MODE_PRIVATE)

    var defaultRestSeconds: Int
        get() = prefs.getInt(KEY_REST_SECONDS, 240)
        set(value) = prefs.edit().putInt(KEY_REST_SECONDS, value).apply()

    companion object {
        private const val KEY_REST_SECONDS = "rest_seconds"
    }
}
