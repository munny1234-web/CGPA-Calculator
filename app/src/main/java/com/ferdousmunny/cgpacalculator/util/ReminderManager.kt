package com.ferdousmunny.cgpacalculator.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ferdousmunny.cgpacalculator.ReminderReceiver
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(val id: Int, val title: String, val message: String, val timestamp: Long)

/**
 * Schedules local notifications -- a recurring "update your CGPA" reminder,
 * and one-off custom reminders (e.g. for a result date or exam) -- using
 * Android's AlarmManager. No server or Firebase is involved; everything
 * runs entirely on-device.
 */
object ReminderManager {
    const val CHANNEL_ID = "cgpa_reminders"
    private const val SEMESTER_REMINDER_ID = 9001
    private const val SEMESTER_REMINDER_INTERVAL_MS = 90L * 24 * 60 * 60 * 1000 // ~90 days
    private const val PREFS_NAME = "cgpa_reminders_prefs"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CGPA Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to update your CGPA or check results"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun pendingIntentFor(context: Context, id: Int, title: String, message: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun setSemesterReminderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("semester_reminder_enabled", enabled).apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(
            context, SEMESTER_REMINDER_ID,
            "Update your CGPA",
            "It's been a while -- add your latest course grades to keep your CGPA up to date."
        )
        if (enabled) {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + SEMESTER_REMINDER_INTERVAL_MS,
                SEMESTER_REMINDER_INTERVAL_MS,
                pendingIntent
            )
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun isSemesterReminderEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("semester_reminder_enabled", false)
    }

    fun loadReminders(context: Context): List<Reminder> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("custom_reminders", null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Reminder(
                id = obj.getInt("id"),
                title = obj.getString("title"),
                message = obj.getString("message"),
                timestamp = obj.getLong("timestamp")
            )
        }.sortedBy { it.timestamp }
    }

    private fun saveReminders(context: Context, reminders: List<Reminder>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        reminders.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("title", r.title)
            obj.put("message", r.message)
            obj.put("timestamp", r.timestamp)
            arr.put(obj)
        }
        prefs.edit().putString("custom_reminders", arr.toString()).apply()
    }

    fun addCustomReminder(context: Context, title: String, message: String, timestamp: Long): Reminder {
        val id = (10000..999999).random()
        val reminder = Reminder(id, title, message, timestamp)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(context, id, title, message)
        alarmManager.set(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)
        saveReminders(context, loadReminders(context) + reminder)
        return reminder
    }

    fun cancelCustomReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(context, reminder.id, reminder.title, reminder.message)
        alarmManager.cancel(pendingIntent)
        saveReminders(context, loadReminders(context).filterNot { it.id == reminder.id })
    }
}
