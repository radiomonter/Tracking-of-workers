package com.workertracking.app.model

enum class AttendanceStatus(
    val codeRu: String,
    val codePl: String,
    val labelRu: String,
    val labelPl: String
) {
    PRESENT("Я", "O", "Явка (часы)", "Obecność (godziny)"),
    ABSENT("Н", "N", "Неявка", "Nieobecność"),
    SICK("Б", "Ch", "Болезнь", "Choroba"),
    VACATION("О", "U", "Отпуск", "Urlop"),
    DAY_OFF("В", "W", "Выходной", "Dzień wolny");

    companion object {
        fun fromString(value: String): AttendanceStatus? {
            val trimmed = value.trim().lowercase()
            return when (trimmed) {
                "н", "n" -> ABSENT
                "б", "ch" -> SICK
                "о", "u" -> VACATION
                "в", "w" -> DAY_OFF
                else -> null
            }
        }
    }
}
