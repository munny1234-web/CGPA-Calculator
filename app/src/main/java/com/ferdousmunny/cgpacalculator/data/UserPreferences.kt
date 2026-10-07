package com.ferdousmunny.cgpacalculator.data

import android.content.Context
import com.ferdousmunny.cgpacalculator.util.AppLanguage

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Stores per-user app settings (department, university, language, dark mode,
 * name) locally on the device, scoped by UID so different accounts don't
 * share settings.
 */
class UserPreferences(context: Context, uid: String) {
    private val prefs = context.getSharedPreferences("cgpa_user_$uid", Context.MODE_PRIVATE)

    fun getDepartment(): String = prefs.getString("department", "") ?: ""
    fun setDepartment(value: String) {
        prefs.edit().putString("department", value).apply()
    }

    fun getUniversity(): String = prefs.getString("university", "") ?: ""
    fun setUniversity(value: String) {
        prefs.edit().putString("university", value).apply()
    }

    fun getLanguage(): AppLanguage =
        if (prefs.getString("language", "en") == "bn") AppLanguage.BANGLA else AppLanguage.ENGLISH

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString("language", if (lang == AppLanguage.BANGLA) "bn" else "en").apply()
    }

    fun getFullName(): String = prefs.getString("full_name", "") ?: ""
    fun setFullName(value: String) {
        prefs.edit().putString("full_name", value).apply()
    }

    /** Total credits required to graduate, used for the credit-progress insight. */
    fun getTotalCreditsRequired(): Double {
        return prefs.getFloat("total_credits_required", 130f).toDouble()
    }
    fun setTotalCreditsRequired(value: Double) {
        prefs.edit().putFloat("total_credits_required", value.toFloat()).apply()
    }

    /** Light / Dark / follow the phone's system setting. */
    fun getThemeMode(): ThemeMode {
        return when (prefs.getString("theme_mode", "system")) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val value = when (mode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        prefs.edit().putString("theme_mode", value).apply()
    }

    fun isBiometricLockEnabled(): Boolean = prefs.getBoolean("biometric_lock_enabled", false)
    fun setBiometricLockEnabled(value: Boolean) {
        prefs.edit().putBoolean("biometric_lock_enabled", value).apply()
    }

    /** "4.00" or "5.00" -- see GradeScale.forScaleName(). */
    fun getGradingScale(): String = prefs.getString("grading_scale", "4.00") ?: "4.00"
    fun setGradingScale(value: String) {
        prefs.edit().putString("grading_scale", value).apply()
    }
}
