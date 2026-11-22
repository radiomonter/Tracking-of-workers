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
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            val lower = trimmed.lowercase()

            // 1. Direct enum name matching (e.g. DAY_OFF, ABSENT, SICK, VACATION, PRESENT)
            entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

            // 2. Short codes and labels in Russian and Polish
            return when {
                lower in listOf("н", "n", "absent", "неявка", "nieobecność", "nieobecnosc") -> ABSENT
                lower in listOf("б", "ch", "sick", "болезнь", "choroba") -> SICK
                lower in listOf("о", "u", "vacation", "отпуск", "urlop") -> VACATION
                lower in listOf("в", "w", "day_off", "dayoff", "выходной", "dzień wolny", "dzien wolny") -> DAY_OFF
                lower in listOf("я", "o", "present", "явка", "obecność", "obecnosc") -> PRESENT
                else -> null
            }
        }
    }
}
