package com.game.reschange

import android.content.Context

object ModePrefs {
    private const val PREF_NAME = "mode_prefs"
    private const val KEY_MODE  = "operation_mode"

    const val MODE_DEFAULT     = "default"     // cmd game downscale
    const val MODE_ALTERNATIVE = "alternative" // device_config game_overlay

    fun saveMode(context: Context, mode: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode).apply()
    }

    fun getMode(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_DEFAULT) ?: MODE_DEFAULT
    }

    fun isAlternative(context: Context): Boolean {
        return getMode(context) == MODE_ALTERNATIVE
    }
}
