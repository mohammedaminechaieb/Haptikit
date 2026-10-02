package com.haptikit.app.data

import android.content.Context

/** Small global switches — plain SharedPreferences, read by the listener service. */
class HaptiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("haptikit_prefs", Context.MODE_PRIVATE)

    /** Master switch: when off, notifications vibrate exactly as before. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Stay quiet while the phone is on silent (ringer mode silent). */
    var respectSilent: Boolean
        get() = prefs.getBoolean("respect_silent", true)
        set(value) = prefs.edit().putBoolean("respect_silent", value).apply()
}
