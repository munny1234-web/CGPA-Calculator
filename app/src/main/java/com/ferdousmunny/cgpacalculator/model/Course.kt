package com.ferdousmunny.cgpacalculator.model

import java.util.UUID

data class Course(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var credit: Double,
    var gradeLabel: String,
    var gradePoint: Double,
    var semester: String = "",
    var notes: String = ""
)

/**
 * Calculates CGPA as a credit-weighted average.
 * CGPA = Σ(credit * gradePoint) / Σ(credit)
 */
fun calculateCGPA(courses: List<Course>): Double {
    val totalCredit = courses.sumOf { it.credit }
    if (totalCredit == 0.0) return 0.0
    val totalPoints = courses.sumOf { it.credit * it.gradePoint }
    return totalPoints / totalCredit
}
