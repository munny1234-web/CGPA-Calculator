package com.ferdousmunny.cgpacalculator.data

import android.content.Context
import com.ferdousmunny.cgpacalculator.model.Course
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves the course list to the phone's local storage (SharedPreferences),
 * scoped to the logged-in user's UID so switching accounts on the same
 * device never mixes up data. No internet connection is needed to read
 * or write this data -- only login itself requires the network.
 */
class CourseRepository(context: Context, uid: String) {
    private val prefs = context.getSharedPreferences("cgpa_prefs_$uid", Context.MODE_PRIVATE)

    fun loadCourses(): MutableList<Course> {
        val json = prefs.getString("courses", null) ?: return mutableListOf()
        val arr = JSONArray(json)
        val list = mutableListOf<Course>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                Course(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    credit = obj.getDouble("credit"),
                    gradeLabel = obj.getString("gradeLabel"),
                    gradePoint = obj.getDouble("gradePoint"),
                    semester = obj.optString("semester", ""),
                    notes = obj.optString("notes", "")
                )
            )
        }
        return list
    }

    fun saveCourses(courses: List<Course>) {
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
        prefs.edit().putString("courses", arr.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().remove("courses").apply()
    }
}
