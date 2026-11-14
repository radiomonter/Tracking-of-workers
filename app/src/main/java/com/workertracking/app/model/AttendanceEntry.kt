package com.workertracking.app.model

data class AttendanceEntry(
    val workerId: Long,
    val year: Int,
    val month: Int,
    val day: Int,
    val hours: Double? = null,
    val status: AttendanceStatus? = null,
    val note: String = ""
) {
    val displayValue: String
        get() {
            if (hours != null) {
                return if (hours % 1.0 == 0.0) {
                    hours.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.1f", hours)
                }
            }
            if (status != null) {
                return status.codeRu
            }
            return ""
        }

    val isWorked: Boolean
        get() = hours != null && hours > 0.0
}
