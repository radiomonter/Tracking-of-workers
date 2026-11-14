package com.workertracking.app.data

import android.content.Context
import com.workertracking.app.model.AdvancePayment
import com.workertracking.app.model.AttendanceEntry
import com.workertracking.app.model.AttendanceStatus
import com.workertracking.app.model.Worker
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppSettings(
    val companyName: String = "DreamHaus",
    val responsiblePerson: String = "Maksym Terekhov"
)

class TimesheetStorage(private val context: Context) {

    private val workersFile = File(context.filesDir, "workers.json")
    private val attendanceFile = File(context.filesDir, "attendance.json")
    private val settingsFile = File(context.filesDir, "settings.json")
    private val advancesFile = File(context.filesDir, "advances.json")

    init {
        if (!workersFile.exists()) {
            initDefaultData()
        }
        if (!advancesFile.exists()) {
            initDefaultAdvances()
        }
        if (!settingsFile.exists()) {
            saveSettings(AppSettings())
        }
    }

    @Synchronized
    fun getSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return try {
            val obj = JSONObject(settingsFile.readText())
            AppSettings(
                companyName = obj.optString("companyName", "DreamHaus"),
                responsiblePerson = obj.optString("responsiblePerson", "Maksym Terekhov")
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }

    @Synchronized
    fun saveSettings(settings: AppSettings) {
        try {
            val obj = JSONObject().apply {
                put("companyName", settings.companyName)
                put("responsiblePerson", settings.responsiblePerson)
            }
            settingsFile.writeText(obj.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getWorkers(): List<Worker> {
        if (!workersFile.exists()) return emptyList()
        val list = mutableListOf<Worker>()
        try {
            val jsonStr = workersFile.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val birthYearVal = if (obj.has("birthYear") && !obj.isNull("birthYear")) obj.getInt("birthYear") else null
                list.add(
                    Worker(
                        id = obj.optLong("id", System.currentTimeMillis() + i),
                        orderIndex = obj.optInt("orderIndex", i + 1),
                        fullName = obj.optString("fullName", ""),
                        position = obj.optString("position", ""),
                        hourlyRate = obj.optDouble("hourlyRate", 0.0),
                        phone = obj.optString("phone", ""),
                        hasSep = obj.optBoolean("hasSep", false),
                        sepDetails = obj.optString("sepDetails", ""),
                        shoeSize = obj.optString("shoeSize", ""),
                        jacketSize = obj.optString("jacketSize", ""),
                        pantsSize = obj.optString("pantsSize", ""),
                        birthYear = birthYearVal,
                        isActive = obj.optBoolean("isActive", true),
                        notes = obj.optString("notes", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedBy { it.orderIndex }
    }

    @Synchronized
    fun saveWorkers(workers: List<Worker>) {
        val array = JSONArray()
        workers.forEachIndexed { index, w ->
            val obj = JSONObject().apply {
                put("id", w.id)
                put("orderIndex", index + 1)
                put("fullName", w.fullName)
                put("position", w.position)
                put("hourlyRate", w.hourlyRate)
                put("phone", w.phone)
                put("hasSep", w.hasSep)
                put("sepDetails", w.sepDetails)
                put("shoeSize", w.shoeSize)
                put("jacketSize", w.jacketSize)
                put("pantsSize", w.pantsSize)
                if (w.birthYear != null) put("birthYear", w.birthYear)
                put("isActive", w.isActive)
                put("notes", w.notes)
            }
            array.put(obj)
        }
        workersFile.writeText(array.toString(2))
    }

    @Synchronized
    fun getAttendance(): List<AttendanceEntry> {
        if (!attendanceFile.exists()) return emptyList()
        val list = mutableListOf<AttendanceEntry>()
        try {
            val jsonStr = attendanceFile.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val hours = if (obj.has("hours") && !obj.isNull("hours")) obj.getDouble("hours") else null
                val statusStr = obj.optString("status", "")
                val status = if (statusStr.isNotEmpty()) AttendanceStatus.fromString(statusStr) else null
                list.add(
                    AttendanceEntry(
                        workerId = obj.getLong("workerId"),
                        year = obj.getInt("year"),
                        month = obj.getInt("month"),
                        day = obj.getInt("day"),
                        hours = hours,
                        status = status,
                        note = obj.optString("note", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @Synchronized
    fun saveAttendance(entries: List<AttendanceEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject().apply {
                put("workerId", e.workerId)
                put("year", e.year)
                put("month", e.month)
                put("day", e.day)
                if (e.hours != null) put("hours", e.hours)
                if (e.status != null) put("status", e.status.name)
                if (e.note.isNotEmpty()) put("note", e.note)
            }
            array.put(obj)
        }
        attendanceFile.writeText(array.toString())
    }

    @Synchronized
    fun setAttendance(workerId: Long, year: Int, month: Int, day: Int, hours: Double?, status: AttendanceStatus?, note: String = "") {
        val all = getAttendance().toMutableList()
        all.removeAll { it.workerId == workerId && it.year == year && it.month == month && it.day == day }
        if (hours != null || status != null || note.isNotEmpty()) {
            all.add(
                AttendanceEntry(
                    workerId = workerId,
                    year = year,
                    month = month,
                    day = day,
                    hours = hours,
                    status = status,
                    note = note
                )
            )
        }
        saveAttendance(all)
    }

    fun initDefaultData() {
        val defaultWorkers = listOf(
            Worker(
                id = 1L, orderIndex = 1, fullName = "Maksym Terekhov",
                position = "Бригадир / Elektromonter", hourlyRate = 34.0, phone = "+48 571 234 501",
                hasSep = true, sepDetails = "E+D do 1kV", shoeSize = "43", jacketSize = "XL", pantsSize = "52",
                birthYear = 1991, notes = "Лидер бригады"
            ),
            Worker(
                id = 2L, orderIndex = 2, fullName = "Gambulat Gviniashvili",
                position = "Электромонтажник", hourlyRate = 30.0, phone = "+48 571 234 502",
                hasSep = true, sepDetails = "G1 do 1kV", shoeSize = "44", jacketSize = "L", pantsSize = "50",
                birthYear = 1989, notes = ""
            ),
            Worker(
                id = 3L, orderIndex = 3, fullName = "Grzegorz Pceniczny",
                position = "Монтажник трасс", hourlyRate = 28.0, phone = "+48 571 234 503",
                hasSep = false, sepDetails = "", shoeSize = "42", jacketSize = "L", pantsSize = "48",
                birthYear = 1994, notes = ""
            ),
            Worker(
                id = 4L, orderIndex = 4, fullName = "Evgen Dulin",
                position = "Электромонтажник", hourlyRate = 30.0, phone = "+48 571 234 504",
                hasSep = true, sepDetails = "G1 do 1kV", shoeSize = "43", jacketSize = "XL", pantsSize = "52",
                birthYear = 1986, notes = ""
            ),
            Worker(
                id = 5L, orderIndex = 5, fullName = "Roman Hardizon",
                position = "Электромонтажник", hourlyRate = 30.0, phone = "+48 571 234 505",
                hasSep = true, sepDetails = "G1 do 1kV", shoeSize = "42", jacketSize = "M", pantsSize = "48",
                birthYear = 1996, notes = ""
            ),
            Worker(
                id = 6L, orderIndex = 6, fullName = "Vasyl Radishchuk",
                position = "Помощник монтажника", hourlyRate = 26.0, phone = "+48 571 234 506",
                hasSep = false, sepDetails = "", shoeSize = "44", jacketSize = "L", pantsSize = "50",
                birthYear = 2000, notes = ""
            ),
            Worker(
                id = 7L, orderIndex = 7, fullName = "Ivan Roik",
                position = "Электромонтажник", hourlyRate = 29.0, phone = "+48 571 234 507",
                hasSep = true, sepDetails = "G1 do 1kV", shoeSize = "43", jacketSize = "L", pantsSize = "50",
                birthYear = 1992, notes = ""
            ),
            Worker(
                id = 8L, orderIndex = 8, fullName = "Oleksandr Kostornoi",
                position = "Монтажник", hourlyRate = 28.0, phone = "+48 571 234 508",
                hasSep = false, sepDetails = "", shoeSize = "45", jacketSize = "XXL", pantsSize = "54",
                birthYear = 1987, notes = ""
            ),
            Worker(
                id = 9L, orderIndex = 9, fullName = "Oleksii Minkin",
                position = "Электромонтажник", hourlyRate = 30.0, phone = "+48 571 234 509",
                hasSep = true, sepDetails = "G1 do 1kV", shoeSize = "42", jacketSize = "M", pantsSize = "48",
                birthYear = 1995, notes = ""
            ),
            Worker(
                id = 10L, orderIndex = 10, fullName = "Michał Kanclerz",
                position = "Специалист автоматики", hourlyRate = 35.0, phone = "+48 571 234 510",
                hasSep = true, sepDetails = "E+D bez ograniczeń", shoeSize = "43", jacketSize = "L", pantsSize = "50",
                birthYear = 1985, notes = "Uprawnienia UDT"
            ),
            Worker(
                id = 11L, orderIndex = 11, fullName = "Oleksandr Bakov",
                position = "Монтажник", hourlyRate = 28.0, phone = "+48 571 234 511",
                hasSep = false, sepDetails = "", shoeSize = "44", jacketSize = "XL", pantsSize = "52",
                birthYear = 1997, notes = ""
            ),
            Worker(
                id = 12L, orderIndex = 12, fullName = "Pavlo Doronin",
                position = "Электромонтажник", hourlyRate = 30.0, phone = "+48 571 234 512",
                hasSep = true, sepDetails = "G1 do 1kV", shoeSize = "43", jacketSize = "L", pantsSize = "50",
                birthYear = 1993, notes = ""
            ),
            Worker(
                id = 13L, orderIndex = 13, fullName = "Rostyslav Vasyliev",
                position = "Монтажник кабельных трасс", hourlyRate = 28.0, phone = "+48 571 234 513",
                hasSep = false, sepDetails = "", shoeSize = "42", jacketSize = "M", pantsSize = "48",
                birthYear = 1999, notes = ""
            ),
            Worker(
                id = 14L, orderIndex = 14, fullName = "Andrii Ivanov",
                position = "Электромонтажник", hourlyRate = 28.0, phone = "+48 571 234 514",
                hasSep = false, sepDetails = "", shoeSize = "43", jacketSize = "L", pantsSize = "50",
                birthYear = 1995, notes = ""
            )
        )
        saveWorkers(defaultWorkers)

        // Seed sample attendance for October 2026 matching template
        val entries = mutableListOf<AttendanceEntry>()
        val year = 2026
        val month = 10

        fun addHours(wId: Long, day: Int, h: Double) {
            entries.add(AttendanceEntry(wId, year, month, day, hours = h))
        }
        fun addStatus(wId: Long, day: Int, s: AttendanceStatus) {
            entries.add(AttendanceEntry(wId, year, month, day, status = s))
        }

        // Maksym Terekhov
        listOf(1, 2, 5, 6, 7, 8, 9, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 26, 27, 29, 30).forEach { addHours(1L, it, 10.0) }
        listOf(24, 28).forEach { addHours(1L, it, 5.0) }

        // Gambulat Gviniashvili
        listOf(1, 5, 6, 7, 9, 12, 14, 15, 16, 19, 20, 22, 23, 26, 27, 28, 29, 30).forEach { addHours(2L, it, 10.0) }
        addHours(2L, 31, 5.0)

        // Grzegorz Pceniczny
        listOf(1, 5, 6, 7, 8, 9, 12, 14, 15, 16, 19, 20, 21, 22, 23).forEach { addHours(3L, it, 10.0) }
        listOf(26, 27, 28, 29, 30).forEach { addStatus(3L, it, AttendanceStatus.ABSENT) }

        // Evgen Dulin
        listOf(1, 2, 5, 6, 7, 8, 9, 12, 13, 14, 15, 16, 19, 26, 27, 28, 29, 30).forEach { addHours(4L, it, 10.0) }
        listOf(20, 21, 22, 23).forEach { addStatus(4L, it, AttendanceStatus.SICK) }
        addHours(4L, 31, 5.0)

        // Roman Hardizon
        addHours(5L, 1, 7.0)
        listOf(2, 5, 7, 8, 9, 12, 13, 14, 15, 16, 19, 23, 27, 28, 29, 30).forEach { addHours(5L, it, 10.0) }
        listOf(3, 17, 24, 31).forEach { addHours(5L, it, 5.0) }

        saveAttendance(entries)
    }

    @Synchronized
    fun getAdvances(): List<AdvancePayment> {
        if (!advancesFile.exists()) return emptyList()
        val list = mutableListOf<AdvancePayment>()
        try {
            val jsonStr = advancesFile.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AdvancePayment(
                        id = obj.optLong("id", System.currentTimeMillis() + i),
                        workerId = obj.getLong("workerId"),
                        workerName = obj.optString("workerName", ""),
                        date = obj.optString("date", "23.10.2026"),
                        amount = obj.optDouble("amount", 0.0),
                        currency = obj.optString("currency", "zł"),
                        comment = obj.optString("comment", ""),
                        paymentMethod = obj.optString("paymentMethod", "Наличные")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.id }
    }

    @Synchronized
    fun saveAdvances(advances: List<AdvancePayment>) {
        val array = JSONArray()
        advances.forEach { a ->
            val obj = JSONObject().apply {
                put("id", a.id)
                put("workerId", a.workerId)
                put("workerName", a.workerName)
                put("date", a.date)
                put("amount", a.amount)
                put("currency", a.currency)
                put("comment", a.comment)
                put("paymentMethod", a.paymentMethod)
            }
            array.put(obj)
        }
        advancesFile.writeText(array.toString(2))
    }

    @Synchronized
    fun addAdvance(advance: AdvancePayment) {
        val all = getAdvances().toMutableList()
        all.add(0, advance)
        saveAdvances(all)
    }

    @Synchronized
    fun addAdvances(newAdvances: List<AdvancePayment>) {
        val all = getAdvances().toMutableList()
        all.addAll(0, newAdvances)
        saveAdvances(all)
    }

    @Synchronized
    fun deleteAdvance(id: Long) {
        val all = getAdvances().filter { it.id != id }
        saveAdvances(all)
    }

    @Synchronized
    fun deleteAdvancesForDate(date: String) {
        val all = getAdvances().filter { it.date != date }
        saveAdvances(all)
    }

    fun initDefaultAdvances() {
        // Seed initial advances including user's 13.10.2026 sheet
        val sample = listOf(
            AdvancePayment(id = 1001L, workerId = 1L, workerName = "Maksym Terekhov", date = "13.10.2026", amount = 700.0, comment = "Аванс", paymentMethod = "Наличные"),
            AdvancePayment(id = 1002L, workerId = 5L, workerName = "Roman Hardizon", date = "13.10.2026", amount = 800.0, comment = "Аванс", paymentMethod = "Наличные"),
            AdvancePayment(id = 1003L, workerId = 7L, workerName = "Ivan Roik", date = "13.10.2026", amount = 700.0, comment = "Аванс", paymentMethod = "Наличные"),
            AdvancePayment(id = 1004L, workerId = 8L, workerName = "Oleksandr Kostornoi", date = "13.10.2026", amount = 700.0, comment = "Аванс", paymentMethod = "Наличные"),
            AdvancePayment(id = 1005L, workerId = 11L, workerName = "Oleksandr Bakov", date = "13.10.2026", amount = 700.0, comment = "Аванс", paymentMethod = "Наличные"),
            AdvancePayment(id = 1006L, workerId = 6L, workerName = "Vasyl Radishchuk", date = "13.10.2026", amount = 700.0, comment = "Аванс", paymentMethod = "Наличные"),
            AdvancePayment(id = 1007L, workerId = 14L, workerName = "Andrii Ivanov", date = "13.10.2026", amount = 700.0, comment = "Аванс", paymentMethod = "Наличные"),
            AdvancePayment(id = 1008L, workerId = 2L, workerName = "Gambulat Gviniashvili", date = "13.10.2026", amount = 700.0, comment = "Аванс", paymentMethod = "Наличные"),
            // Plus an additional sample for 23.10.2026
            AdvancePayment(id = 1009L, workerId = 1L, workerName = "Maksym Terekhov", date = "23.10.2026", amount = 700.0, comment = "Аванс", paymentMethod = "Наличные"),
            AdvancePayment(id = 1010L, workerId = 2L, workerName = "Gambulat Gviniashvili", date = "23.10.2026", amount = 500.0, comment = "Аванс", paymentMethod = "Наличные")
        )
        saveAdvances(sample)
    }
}
