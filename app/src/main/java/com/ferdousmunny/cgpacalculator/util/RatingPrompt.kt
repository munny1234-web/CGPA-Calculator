package com.ferdousmunny.cgpacalculator.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Shows a "Rate Us" prompt after the app has been opened a few times,
 * without asking again once the user has responded either way (or resetting
 * the counter if they choose "Later").
 */
object RatingPrompt {
    private const val PREFS = "app_prefs"
    private const val KEY_OPEN_COUNT = "app_open_count"
    private const val KEY_ASKED = "rating_asked"
    private const val THRESHOLD = 5

    /** Call once per app session. Returns true if the rating dialog should be shown now. */
    fun recordAppOpenAndShouldPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return false
        val count = prefs.getInt(KEY_OPEN_COUNT, 0) + 1
        prefs.edit().putInt(KEY_OPEN_COUNT, count).apply()
        return count >= THRESHOLD
    }

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** User chose "Later" -- reset the counter so it asks again after a few more opens. */
    fun remindLater(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_OPEN_COUNT, 0).apply()
    }

    fun openPlayStore(context: Context) {
        val packageName = context.packageName
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }
}
