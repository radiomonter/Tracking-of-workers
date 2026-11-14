package com.workertracking.app.model

import java.time.YearMonth
import java.util.Locale

data class MonthInfo(
    val year: Int,
    val month: Int // 1..12
) {
    val daysInMonth: Int
        get() = try {
            YearMonth.of(year, month).lengthOfMonth()
        } catch (e: Exception) {
            31
        }

    val titleRuPl: String
        get() {
            val (ru, pl) = monthNames[month - 1]
            return "$ru $year / $pl $year"
        }

    val titleShort: String
        get() {
            val (ru, pl) = monthNames[month - 1]
            return "$ru $year"
        }

    companion object {
        val monthNames = listOf(
            "Январь" to "Styczeń",
            "Февраль" to "Luty",
            "Март" to "Marzec",
            "Апрель" to "Kwiecień",
            "Май" to "Maj",
            "Июнь" to "Czerwiec",
            "Июль" to "Lipiec",
            "Август" to "Sierpień",
            "Сентябрь" to "Wrzesień",
            "Октябрь" to "Październik",
            "Ноябрь" to "Listopad",
            "Декабрь" to "Grudzień"
        )

        fun current(): MonthInfo {
            val now = java.time.LocalDate.now()
            return MonthInfo(now.year, now.monthValue)
        }
    }
}

data class WorkerMonthSummary(
    val worker: Worker,
    val totalDays: Int,
    val totalHours: Double,
    val sickDays: Int,
    val absentDays: Int,
    val vacationDays: Int,
    val estimatedPay: Double
)
