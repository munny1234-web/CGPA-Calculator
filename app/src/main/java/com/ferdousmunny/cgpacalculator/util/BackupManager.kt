package com.ferdousmunny.cgpacalculator.util

import com.ferdousmunny.cgpacalculator.model.Course
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Lets the student back up their full course list to a JSON file (and
 * restore it later), independent of the phone's local storage -- useful
 * when switching phones or reinstalling the app, without needing any
 * cloud/Firestore setup.
 */
object BackupManager {

    fun exportToJson(courses: List<Course>): String {
        val arr = JSONArray()
        courses.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("credit", c.credit)
            obj.put("gradeLabel", c.gradeLabel)
            obj.put("gradePoint", c.gradePoint)
            obj.put("semester", c.semester)
            obj.put("notes", c.notes)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    fun importFromJson(jsonText: String): List<Course> {
        return try {
            val arr = JSONArray(jsonText)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    Course(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        credit = obj.getDouble("credit"),
                        gradeLabel = obj.getString("gradeLabel"),
                        gradePoint = obj.getDouble("gradePoint"),
                        semester = obj.optString("semester", ""),
                        notes = obj.optString("notes", "")
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
