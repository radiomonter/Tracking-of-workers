package com.workertracking.app.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AdvancePayment(
    val id: Long = System.currentTimeMillis(),
    val workerId: Long,
    val workerName: String,
    val date: String, // format "dd.MM.yyyy", e.g. "23.10.2026"
    val amount: Double, // e.g. 700.0
    val currency: String = "zł",
    val comment: String = "",
    val paymentMethod: String = "Наличные" // "Наличные" or "На карту"
) {
    val parsedDate: LocalDate?
        get() = try {
            LocalDate.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (e: Exception) {
            null
        }

    val year: Int
        get() = parsedDate?.year ?: 2026

    val month: Int
        get() = parsedDate?.monthValue ?: 10

    val day: Int
        get() = parsedDate?.dayOfMonth ?: 1
}

data class WorkerPayrollSummary(
    val worker: Worker,
    val totalHours: Double,
    val hourlyRate: Double,
    val earnedTotal: Double,
    val advancesTotal: Double,
    val balanceToPay: Double,
    val advancesList: List<AdvancePayment>
)

data class DateAdvanceGroup(
    val date: String,
    val items: List<AdvancePayment>,
    val totalAmount: Double = items.sumOf { it.amount }
) {
    val orderedItems: List<AdvancePayment>
        get() = items.sortedBy { it.id }

    fun formatAsShareText(): String {
        val sb = StringBuilder()
        sb.appendLine(date)
        orderedItems.forEachIndexed { index, item ->
            val amountStr = if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else "%.2f".format(item.amount)
            sb.appendLine("${index + 1}:${item.workerName} $amountStr")
        }
        val totalStr = if (totalAmount % 1.0 == 0.0) totalAmount.toInt().toString() else "%.2f".format(totalAmount)
        sb.append("Итого $totalStr")
        return sb.toString()
    }
}
