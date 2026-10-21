package com.teju.classnow.util

import com.teju.classnow.data.model.TimetableEntry
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object CsvHelper {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val validDays = setOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    data class ParseResult(
        val entries: List<TimetableEntry>,
        val errors: List<String>,
        val warnings: List<String> = emptyList()
    )

    fun parseCsv(csvText: String): ParseResult {
        val entries = mutableListOf<TimetableEntry>()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val lines = csvText.lines()

        if (lines.isEmpty() || (lines.size == 1 && lines[0].isBlank())) {
            return ParseResult(emptyList(), listOf("CSV file is empty."))
        }

        val header = lines.first().split(",")
        if (header.size < 4 || !header[0].trim().equals("Day", ignoreCase = true)) {
            warnings.add("Warning: CSV header should be 'Day,Subject,Start,End'")
        }

        lines.drop(1).forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            val rowNum = index + 2
            val tokens = line.split(",").map { it.trim() }

            if (tokens.size < 4) {
                errors.add("Row $rowNum: Invalid column count (Expected at least 4 values).")
                return@forEachIndexed
            }

            val day = tokens[0].lowercase().replaceFirstChar { it.uppercase() }
            val subject = tokens[1]
            val start = tokens[2]
            val end = tokens[3]
            val faculty = tokens.getOrNull(4)

            if (!validDays.contains(day)) {
                errors.add("Row $rowNum: Invalid day name '$day'.")
                return@forEachIndexed
            }

            if (subject.isBlank()) {
                errors.add("Row $rowNum: Subject name cannot be blank.")
                return@forEachIndexed
            }

            val startTimeParsed = try {
                LocalTime.parse(start, timeFormatter)
            } catch (e: Exception) {
                null
            }

            val endTimeParsed = try {
                LocalTime.parse(end, timeFormatter)
            } catch (e: Exception) {
                null
            }

            if (startTimeParsed == null) {
                errors.add("Row $rowNum: Start time '$start' must be in HH:mm format.")
                return@forEachIndexed
            }

            if (endTimeParsed == null) {
                errors.add("Row $rowNum: End time '$end' must be in HH:mm format.")
                return@forEachIndexed
            }

            if (!endTimeParsed.isAfter(startTimeParsed)) {
                errors.add("Row $rowNum: End time ($end) must be after start time ($start).")
                return@forEachIndexed
            }

            // Check for overlaps within the current imported list
            val overlap = entries.filter { it.dayOfWeek == day }.find { existing ->
                val exStart = LocalTime.parse(existing.startTime, timeFormatter)
                val exEnd = LocalTime.parse(existing.endTime, timeFormatter)
                
                // (StartA < EndB) and (EndA > StartB)
                startTimeParsed.isBefore(exEnd) && endTimeParsed.isAfter(exStart)
            }

            if (overlap != null) {
                warnings.add("Row $rowNum: Overlaps with another class in the CSV ('${overlap.subject}' on $day).")
            }

            entries.add(
                TimetableEntry(
                    dayOfWeek = day,
                    subject = subject,
                    startTime = start,
                    endTime = end,
                    faculty = faculty.takeIf { !it.isNullOrBlank() }
                )
            )
        }

        return ParseResult(entries, errors, warnings)
    }

    fun exportToCsv(entries: List<TimetableEntry>): String {
        val sb = StringBuilder()
        sb.append("Day,Subject,Start,End,Faculty\n")
        // Sort entries for cleaner export
        val sorted = entries.sortedWith(compareBy({ getDayOrder(it.dayOfWeek) }, { it.startTime }))
        sorted.forEach { entry ->
            sb.append("${entry.dayOfWeek},${entry.subject},${entry.startTime},${entry.endTime},${entry.faculty ?: ""}\n")
        }
        return sb.toString()
    }

    private fun getDayOrder(day: String): Int {
        return when (day) {
            "Monday" -> 1
            "Tuesday" -> 2
            "Wednesday" -> 3
            "Thursday" -> 4
            "Friday" -> 5
            "Saturday" -> 6
            "Sunday" -> 7
            else -> 8
        }
    }
}
