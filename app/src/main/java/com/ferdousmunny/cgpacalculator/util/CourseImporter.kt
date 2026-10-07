package com.ferdousmunny.cgpacalculator.util

import android.content.Context
import android.net.Uri
import com.ferdousmunny.cgpacalculator.model.Course
import com.ferdousmunny.cgpacalculator.model.GradeOption
import com.ferdousmunny.cgpacalculator.model.GradeScale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

data class ImportResult(val courses: List<Course>, val skippedLines: Int)

/**
 * Lets students who already have several semesters of results add all their
 * courses at once instead of one-by-one, by importing a CSV/TXT file, a PDF
 * (e.g. an official result sheet), or a photo of a mark-sheet (read via OCR).
 *
 * Two line formats are understood for every source:
 *
 * 1) Simple comma-separated (recommended for CSV/TXT you create yourself):
 *      Course Name, Credit, Grade
 *      Example: Data Structures, 3, A
 *
 * 2) Flexible / table-like (for real transcripts, PDFs, and OCR'd photos,
 *    where columns are separated by spaces/tabs instead of commas):
 *      CSE101  Introduction to Programming   3.00   A+
 *    The parser looks at the words on the line, finds a recognizable grade
 *    (A+, A, A-, ... F), then the nearest number just before it (the
 *    credit hours), and treats everything before that as the course name.
 *
 * Blank lines, header rows, and lines that don't match either format are
 * skipped automatically and counted so the user can be told how many lines
 * were skipped. A preview is always shown before anything is added, since
 * OCR/transcript parsing is best-effort and not always perfect.
 */
object CourseImporter {

    private val courseCodeRegex = Regex("[A-Za-z]{2,6}-?[0-9]{2,4}[A-Za-z]?")
    private val semesterYearRegex = Regex(
        "\\b(SPRING|SUMMER|FALL|AUTUMN|WINTER)\\s+[0-9]{4}\\b",
        RegexOption.IGNORE_CASE
    )
    // PDF text extraction very often glues a plain single-letter grade
    // (A, B, C, D, F -- without a +/- modifier) directly onto the next
    // row's semester label with no space, e.g. "3.00 BSPRING 2023 ...".
    // This inserts the missing space so that row splitting works correctly.
    private val gluedGradeSemesterRegex = Regex(
        "([A-Fa-f])(SPRING|SUMMER|FALL|AUTUMN|WINTER)(?=\\s)",
        RegexOption.IGNORE_CASE
    )
    private val tokenSplitRegex = Regex("[\\s,|;]+")

    /** Reads a plain CSV or TXT file. */
    fun parse(inputStream: InputStream): ImportResult {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val text = reader.readText()
        return parseLines(text)
    }

    /** Extracts text from a PDF (e.g. an official result sheet/transcript) and parses it. */
    fun parsePdf(context: Context, uri: Uri): ImportResult {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { document ->
                PDFTextStripper().getText(document)
            }
        } ?: ""
        return parseLines(text)
    }

    /** Runs OCR on a photo of a mark-sheet/course list and parses the recognized text. */
    suspend fun parseImage(context: Context, uri: Uri): ImportResult {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()
        return parseLines(result.text)
    }

    /**
     * Core parser shared by all import sources. First normalizes the text so
     * each course gets its own line (splitting on course-code / semester+year
     * patterns, since PDF and OCR text frequently merges multiple rows into
     * one line), then tries the strict comma format on each line, falling
     * back to flexible table-style parsing.
     */
    fun parseLines(text: String): ImportResult {
        val courses = mutableListOf<Course>()
        var skipped = 0

        normalizeIntoRows(text).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val course = tryParseCommaFormat(line) ?: tryParseFlexibleFormat(line)
            if (course != null) {
                courses.add(course)
            } else {
                skipped++
            }
        }

        return ImportResult(courses, skipped)
    }

    private fun normalizeIntoRows(text: String): List<String> {
        var normalized = gluedGradeSemesterRegex.replace(text) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        normalized = semesterYearRegex.replace(normalized) { "\n" + it.value }
        normalized = courseCodeRegex.replace(normalized) { "\n" + it.value }
        return normalized.lines()
    }

    /** Handles "Course Name, Credit, Grade" style lines. */
    private fun tryParseCommaFormat(line: String): Course? {
        val parts = line.split(",").map { it.trim() }
        if (parts.size < 3) return null

        val name = parts[0]
        val creditValue = parts[1].toDoubleOrNull()
        val gradeText = parts[2]

        // A header row like "Course Name, Credit, Grade" won't have a
        // numeric credit value, so it's naturally rejected here.
        if (creditValue == null || creditValue < 0.0 || name.isBlank()) return null

        val grade = GradeScale.grades.firstOrNull { it.label.equals(gradeText, ignoreCase = true) } ?: return null

        return Course(
            id = UUID.randomUUID().toString(),
            name = name,
            credit = creditValue,
            gradeLabel = grade.label,
            gradePoint = grade.point
        )
    }

    /**
     * Handles table-like rows such as those from a scanned/exported
     * transcript, e.g.: "CSE113  Fundamentals of Computer   3.00   B+"
     * or pipe-separated "CSE113 | Fundamentals of Computer | 3.00 | B+".
     */
    private fun tryParseFlexibleFormat(line: String): Course? {
        val tokens = line.split(tokenSplitRegex).filter { it.isNotBlank() }
        if (tokens.size < 3) return null

        var gradeIndex = -1
        var matchedGrade: GradeOption? = null
        for (i in tokens.indices.reversed()) {
            val cleaned = tokens[i].trim('.', ';', ':')
            val grade = GradeScale.grades.firstOrNull { it.label.equals(cleaned, ignoreCase = true) }
            if (grade != null) {
                gradeIndex = i
                matchedGrade = grade
                break
            }
        }
        if (gradeIndex <= 0 || matchedGrade == null) return null

        var creditIndex = -1
        var creditValue: Double? = null
        for (i in (gradeIndex - 1) downTo 0) {
            val cleaned = tokens[i].trim('.', ',')
            val value = cleaned.toDoubleOrNull()
            if (value != null && value in 0.0..10.0) {
                creditIndex = i
                creditValue = value
                break
            }
        }
        if (creditIndex <= 0 || creditValue == null) return null

        // Prefer starting the name at a course code if one is present
        // (drops leftover semester/year text such as "SPRING 2023" before it).
        var nameStart = 0
        for (i in 0 until creditIndex) {
            if (courseCodeRegex.matches(tokens[i])) {
                nameStart = i
                break
            }
        }

        val name = tokens.subList(nameStart, creditIndex).joinToString(" ").trim()
        if (name.isBlank() || name.length < 2) return null

        return Course(
            id = UUID.randomUUID().toString(),
            name = name,
            credit = creditValue,
            gradeLabel = matchedGrade.label,
            gradePoint = matchedGrade.point
        )
    }
}
