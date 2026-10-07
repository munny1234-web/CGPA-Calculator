package com.ferdousmunny.cgpacalculator.util

import android.content.Context
import com.ferdousmunny.cgpacalculator.model.Course
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WhatIfScenario(
    val id: String,
    val name: String,
    val courses: List<Course>,
    val createdAt: Long
)

/**
 * Lets a student save more than one "what if I take these courses next
 * semester" plan (e.g. "Best case", "Realistic", "Worst case") so they can
 * come back and compare them later, instead of only being able to work
 * with one plan at a time.
 */
object ScenarioManager {
    private const val PREFS_NAME = "cgpa_whatif_scenarios"

    fun loadScenarios(context: Context): List<WhatIfScenario> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("scenarios", null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val coursesArr = obj.getJSONArray("courses")
            val courses = (0 until coursesArr.length()).map { j ->
                val c = coursesArr.getJSONObject(j)
                Course(
                    id = c.optString("id", UUID.randomUUID().toString()),
                    name = c.getString("name"),
                    credit = c.getDouble("credit"),
                    gradeLabel = c.getString("gradeLabel"),
                    gradePoint = c.getDouble("gradePoint"),
                    semester = c.optString("semester", ""),
                    notes = c.optString("notes", "")
                )
            }
            WhatIfScenario(
                id = obj.getString("id"),
                name = obj.getString("name"),
                courses = courses,
                createdAt = obj.getLong("createdAt")
            )
        }.sortedByDescending { it.createdAt }
    }

    private fun saveScenarios(context: Context, scenarios: List<WhatIfScenario>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        scenarios.forEach { scenario ->
            val obj = JSONObject()
            obj.put("id", scenario.id)
            obj.put("name", scenario.name)
            obj.put("createdAt", scenario.createdAt)
            val coursesArr = JSONArray()
            scenario.courses.forEach { c ->
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("name", c.name)
                cObj.put("credit", c.credit)
                cObj.put("gradeLabel", c.gradeLabel)
                cObj.put("gradePoint", c.gradePoint)
                cObj.put("semester", c.semester)
                cObj.put("notes", c.notes)
                coursesArr.put(cObj)
            }
            obj.put("courses", coursesArr)
            arr.put(obj)
        }
        prefs.edit().putString("scenarios", arr.toString()).apply()
    }

    fun saveScenario(context: Context, name: String, courses: List<Course>) {
        val scenario = WhatIfScenario(
            id = UUID.randomUUID().toString(),
            name = name,
            courses = courses,
            createdAt = System.currentTimeMillis()
        )
        saveScenarios(context, loadScenarios(context) + scenario)
    }

    fun deleteScenario(context: Context, scenario: WhatIfScenario) {
        saveScenarios(context, loadScenarios(context).filterNot { it.id == scenario.id })
    }
}
