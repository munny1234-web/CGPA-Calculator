package com.ferdousmunny.cgpacalculator.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ferdousmunny.cgpacalculator.model.Course
import com.ferdousmunny.cgpacalculator.model.calculateCGPA
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a simple academic transcript PDF from the student's courses,
 * grouped by semester, and lets them share it via any installed app (Drive,
 * WhatsApp, email, etc.) using Android's built-in PDF and file-sharing APIs
 * -- no extra libraries needed.
 */
object PdfExporter {

    fun exportTranscript(context: Context, courses: List<Course>): File {
        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 40f
        val rightEdge = pageWidth - 40f
        val bottomLimit = pageHeight - 60f

        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val textPaint = Paint().apply { textSize = 11f }
        val linePaint = Paint().apply { strokeWidth = 1f; color = android.graphics.Color.LTGRAY }

        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 40f

        fun newPageIfNeeded() {
            if (y > bottomLimit) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }
        }

        canvas.drawText("Academic Transcript", leftMargin, y, titlePaint)
        y += 26f

        val cgpa = calculateCGPA(courses)
        val totalCredit = courses.sumOf { it.credit }
        canvas.drawText(
            "CGPA: ${String.format("%.2f", cgpa)}    Total Credits: ${String.format("%.1f", totalCredit)}",
            leftMargin, y, textPaint
        )
        y += 24f

        canvas.drawText("Course", leftMargin, y, headerPaint)
        canvas.drawText("Credit", leftMargin + 320f, y, headerPaint)
        canvas.drawText("Grade", leftMargin + 390f, y, headerPaint)
        y += 6f
        canvas.drawLine(leftMargin, y, rightEdge, y, linePaint)
        y += 18f

        val grouped = courses.groupBy { it.semester.ifBlank { "Unassigned" } }
        grouped.forEach { (semester, list) ->
            newPageIfNeeded()
            canvas.drawText(semester, leftMargin, y, headerPaint)
            y += 18f
            list.forEach { c ->
                newPageIfNeeded()
                val displayName = if (c.name.length > 45) c.name.take(42) + "..." else c.name
                canvas.drawText(displayName, leftMargin, y, textPaint)
                canvas.drawText(String.format("%.2f", c.credit), leftMargin + 320f, y, textPaint)
                canvas.drawText(c.gradeLabel, leftMargin + 390f, y, textPaint)
                y += 16f
            }
            y += 10f
        }

        document.finishPage(page)

        val file = File(context.cacheDir, "transcript_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Transcript"))
    }
}
