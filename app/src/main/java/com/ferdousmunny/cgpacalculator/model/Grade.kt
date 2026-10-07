package com.ferdousmunny.cgpacalculator.model

data class GradeOption(val label: String, val point: Double)

/**
 * Grading scales the app supports. "4.00" is the standard scale used by
 * most Bangladeshi universities; "5.00" is offered for universities that
 * use a 5-point scale instead. Changing the active scale only affects the
 * grade options shown when adding/editing a course going forward -- it
 * does not change the point value already stored on existing courses.
 */
object GradeScale {
    val scale4 = listOf(
        GradeOption("A+", 4.00),
        GradeOption("A", 3.75),
        GradeOption("A-", 3.50),
        GradeOption("B+", 3.25),
        GradeOption("B", 3.00),
        GradeOption("B-", 2.75),
        GradeOption("C+", 2.50),
        GradeOption("C", 2.25),
        GradeOption("D", 2.00),
        GradeOption("F", 0.00)
    )

    val scale5 = listOf(
        GradeOption("A+", 5.00),
        GradeOption("A", 4.50),
        GradeOption("A-", 4.00),
        GradeOption("B+", 3.50),
        GradeOption("B", 3.00),
        GradeOption("B-", 2.50),
        GradeOption("C+", 2.00),
        GradeOption("C", 1.50),
        GradeOption("D", 1.00),
        GradeOption("F", 0.00)
    )

    /** Default/backward-compatible reference to the standard 4.00 scale. */
    val grades = scale4

    fun forScaleName(name: String): List<GradeOption> = if (name == "5.00") scale5 else scale4
}
