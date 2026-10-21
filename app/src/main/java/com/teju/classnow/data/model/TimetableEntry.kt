package com.teju.classnow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timetable_entries")
data class TimetableEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: String, // "Monday", "Tuesday", etc.
    val subject: String,
    val startTime: String, // "HH:mm"
    val endTime: String,   // "HH:mm"
    val faculty: String? = null,
    val silentModeRule: String = RULE_DEFAULT,
    val customSilentSchedule: String? = null
) {
    companion object {
        const val RULE_DEFAULT = "DEFAULT"
        const val RULE_FORCE_SILENT = "FORCE_SILENT"
        const val RULE_FORCE_SOUND = "FORCE_SOUND"
        const val RULE_IGNORE = "IGNORE"
    }
}
