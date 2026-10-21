package com.teju.classnow.data.model

data class CustomSoundInterval(
    val startTime: String, // "HH:mm" (24-hour format)
    val endTime: String,   // "HH:mm" (24-hour format)
    val mode: String = MODE_SILENT // "SILENT" or "NORMAL"
) {
    companion object {
        const val MODE_SILENT = "SILENT"
        const val MODE_NORMAL = "NORMAL"
    }
}

data class CustomClassSchedule(
    val intervals: List<CustomSoundInterval> = emptyList(),
    val endAction: String = END_ACTION_DEFAULT // "DEFAULT", "FORCE_NORMAL", "KEEP_AS_IS"
) {
    companion object {
        const val END_ACTION_DEFAULT = "DEFAULT"
        const val END_ACTION_NORMAL = "FORCE_NORMAL"
        const val END_ACTION_KEEP = "KEEP_AS_IS"
    }
}
