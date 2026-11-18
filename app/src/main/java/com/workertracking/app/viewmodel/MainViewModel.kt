package com.workertracking.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workertracking.app.data.AppSettings
import com.workertracking.app.data.TimesheetStorage
import com.workertracking.app.excel.ExcelManager
import com.workertracking.app.model.AdvancePayment
import com.workertracking.app.model.AttendanceEntry
import com.workertracking.app.model.AttendanceStatus
import com.workertracking.app.model.DateAdvanceGroup
import com.workertracking.app.model.MonthInfo
import com.workertracking.app.model.Worker
import com.workertracking.app.model.WorkerMonthSummary
import com.workertracking.app.model.WorkerPayrollSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.regex.Pattern

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = TimesheetStorage(application)
    val excelManager = ExcelManager(application)

    private val _currentMonth = MutableStateFlow(MonthInfo(2026, 10))
    val currentMonth: StateFlow<MonthInfo> = _currentMonth.asStateFlow()

    private val _workers = MutableStateFlow<List<Worker>>(emptyList())
    val workers: StateFlow<List<Worker>> = _workers.asStateFlow()

    private val _attendance = MutableStateFlow<List<AttendanceEntry>>(emptyList())
    val attendance: StateFlow<List<AttendanceEntry>> = _attendance.asStateFlow()

    private val _advances = MutableStateFlow<List<AdvancePayment>>(emptyList())
    val advances: StateFlow<List<AdvancePayment>> = _advances.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus: StateFlow<String?> = _operationStatus.asStateFlow()

    private val _lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportedFile: StateFlow<File?> = _lastExportedFile.asStateFlow()

    private val _appSettings = MutableStateFlow(storage.getSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    val filteredWorkers: StateFlow<List<Worker>> = combine(_workers, _searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter {
            it.fullName.contains(query, ignoreCase = true) ||
            it.position.contains(query, ignoreCase = true) ||
            it.phone.contains(query, ignoreCase = true) ||
            it.sepDetails.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val monthSummaries: StateFlow<List<WorkerMonthSummary>> = combine(_workers, _attendance, _currentMonth) { wList, aList, month ->
        wList.map { worker ->
            val workerEntries = aList.filter { it.workerId == worker.id && it.year == month.year && it.month == month.month }
            var daysCount = 0
            var hoursSum = 0.0
            var sickCount = 0
            var absentCount = 0
            var vacationCount = 0

            workerEntries.forEach { entry ->
                if (entry.hours != null && entry.hours > 0) {
                    daysCount++
                    hoursSum += entry.hours
                } else if (entry.status != null) {
                    when (entry.status) {
                        AttendanceStatus.SICK -> sickCount++
                        AttendanceStatus.ABSENT -> absentCount++
                        AttendanceStatus.VACATION -> vacationCount++
                        else -> {}
                    }
                }
            }

            WorkerMonthSummary(
                worker = worker,
                totalDays = daysCount,
                totalHours = hoursSum,
                sickDays = sickCount,
                absentDays = absentCount,
                vacationDays = vacationCount,
                estimatedPay = hoursSum * worker.hourlyRate
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val dateAdvanceGroups: StateFlow<List<DateAdvanceGroup>> = combine(_advances, _currentMonth) { advList, month ->
        // Group all advances by date string, with optional month matching or chronologically sorted
        advList.groupBy { it.date }.map { (date, items) ->
            DateAdvanceGroup(date = date, items = items)
        }.sortedWith(Comparator { a, b ->
            val da = a.items.firstOrNull()?.parsedDate
            val db = b.items.firstOrNull()?.parsedDate
            if (da != null && db != null) db.compareTo(da)
            else b.date.compareTo(a.date)
        })
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val workerPayrollSummaries: StateFlow<List<WorkerPayrollSummary>> = combine(
        _workers, _attendance, _advances, _currentMonth
    ) { wList, aList, advList, month ->
        wList.map { worker ->
            val workerEntries = aList.filter { it.workerId == worker.id && it.year == month.year && it.month == month.month }
            val hoursSum = workerEntries.mapNotNull { it.hours }.sum()
            val earned = hoursSum * worker.hourlyRate
            val workerMonthAdvances = advList.filter {
                it.workerId == worker.id && it.year == month.year && it.month == month.month
            }
            val totalAdv = workerMonthAdvances.sumOf { it.amount }
            WorkerPayrollSummary(
                worker = worker,
                totalHours = hoursSum,
                hourlyRate = worker.hourlyRate,
                earnedTotal = earned,
                advancesTotal = totalAdv,
                balanceToPay = earned - totalAdv,
                advancesList = workerMonthAdvances
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _workers.value = storage.getWorkers()
            _attendance.value = storage.getAttendance()
            _advances.value = storage.getAdvances()
            _appSettings.value = storage.getSettings()
        }
    }

    fun setMonth(year: Int, month: Int) {
        _currentMonth.value = MonthInfo(year, month)
    }

    fun nextMonth() {
        val curr = _currentMonth.value
        val next = if (curr.month == 12) MonthInfo(curr.year + 1, 1) else MonthInfo(curr.year, curr.month + 1)
        _currentMonth.value = next
    }

    fun prevMonth() {
        val curr = _currentMonth.value
        val prev = if (curr.month == 1) MonthInfo(curr.year - 1, 12) else MonthInfo(curr.year, curr.month - 1)
        _currentMonth.value = prev
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSettings(companyName: String, responsiblePerson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = AppSettings(
                companyName = companyName.trim().ifEmpty { "DreamHaus" },
                responsiblePerson = responsiblePerson.trim().ifEmpty { "Maksym Terekhov" }
            )
            storage.saveSettings(s)
            _appSettings.value = s
            _operationStatus.value = "Подпись обновлена: ${s.responsiblePerson} (${s.companyName})"
        }
    }

    fun setAttendance(workerId: Long, day: Int, hours: Double?, status: AttendanceStatus?, note: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val month = _currentMonth.value
            storage.setAttendance(workerId, month.year, month.month, day, hours, status, note)
            _attendance.value = storage.getAttendance()
        }
    }

    fun batchSetHoursForDay(day: Int, hours: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val month = _currentMonth.value
            _workers.value.filter { it.isActive }.forEach { worker ->
                storage.setAttendance(worker.id, month.year, month.month, day, hours, null)
            }
            _attendance.value = storage.getAttendance()
            _operationStatus.value = "Проставлено $hours ч. для всех рабочих на $day-е число"
        }
    }

    fun addWorker(
        fullName: String,
        position: String = "",
        hourlyRate: Double = 0.0,
        phone: String = "",
        hasSep: Boolean = false,
        sepDetails: String = "",
        shoeSize: String = "",
        jacketSize: String = "",
        pantsSize: String = "",
        birthYear: Int? = null,
        notes: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = _workers.value.toMutableList()
            val newWorker = Worker(
                id = System.currentTimeMillis(),
                orderIndex = currentList.size + 1,
                fullName = fullName,
                position = position,
                hourlyRate = hourlyRate,
                phone = phone,
                hasSep = hasSep,
                sepDetails = sepDetails,
                shoeSize = shoeSize,
                jacketSize = jacketSize,
                pantsSize = pantsSize,
                birthYear = birthYear,
                notes = notes
            )
            currentList.add(newWorker)
            storage.saveWorkers(currentList)
            _workers.value = storage.getWorkers()
            _operationStatus.value = "Сотрудник $fullName добавлен"
        }
    }

    fun updateWorker(worker: Worker) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _workers.value.map { if (it.id == worker.id) worker else it }
            storage.saveWorkers(list)
            _workers.value = storage.getWorkers()
            _operationStatus.value = "Данные сотрудника обновлены"
        }
    }

    fun deleteWorker(workerId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _workers.value.filter { it.id != workerId }
            storage.saveWorkers(list)
            _workers.value = storage.getWorkers()
            _operationStatus.value = "Сотрудник удален"
        }
    }

    fun exportToExcel(onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = _appSettings.value
                val file = excelManager.exportTimesheet(
                    monthInfo = _currentMonth.value,
                    workers = _workers.value,
                    entries = _attendance.value,
                    companyName = settings.companyName,
                    responsiblePerson = settings.responsiblePerson
                )
                _lastExportedFile.value = file
                _operationStatus.value = "Табель экспортирован: ${file.name}"
                launch(Dispatchers.Main) {
                    onComplete(file)
                }
            } catch (e: Exception) {
                _operationStatus.value = "Ошибка экспорта: ${e.message}"
            }
        }
    }

    fun exportWorkersToExcel(onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = _appSettings.value
                val file = excelManager.exportWorkersRegistry(
                    workers = _workers.value,
                    companyName = settings.companyName,
                    responsiblePerson = settings.responsiblePerson
                )
                _lastExportedFile.value = file
                _operationStatus.value = "Реестр сотрудников экспортирован: ${file.name}"
                launch(Dispatchers.Main) {
                    onComplete(file)
                }
            } catch (e: Exception) {
                _operationStatus.value = "Ошибка экспорта реестра: ${e.message}"
            }
        }
    }

    private fun normalizedWorkerName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun importFromExcel(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = excelManager.importTimesheet(inputStream)
                if (result.workers.isEmpty()) {
                    _operationStatus.value = "В файле не найдено строк с сотрудниками"
                    return@launch
                }
                if (result.entries.isEmpty()) {
                    _operationStatus.value = "Импорт остановлен: в файле нет отметок посещаемости"
                    return@launch
                }

                val month = result.monthInfo ?: MonthInfo.current()
                val existingWorkers = storage.getWorkers().toMutableList()
                val importedNames = mutableSetOf<String>()
                val workerIdMap = mutableMapOf<Long, Long>()
                val newWorkers = mutableListOf<Worker>()
                var nextWorkerId = maxOf(System.currentTimeMillis(), (existingWorkers.maxOfOrNull { it.id } ?: 0L) + 1L)

                for (importedWorker in result.workers) {
                    val nameKey = normalizedWorkerName(importedWorker.fullName)
                    if (nameKey.isBlank()) {
                        _operationStatus.value = "Импорт остановлен: в файле есть сотрудник без имени"
                        return@launch
                    }
                    if (!importedNames.add(nameKey)) {
                        _operationStatus.value = "Импорт остановлен: в файле повторяется сотрудник «${importedWorker.fullName}»"
                        return@launch
                    }

                    val matches = existingWorkers.filter { normalizedWorkerName(it.fullName) == nameKey }
                    if (matches.size > 1) {
                        _operationStatus.value = "Импорт остановлен: в списке сотрудников неоднозначное совпадение «${importedWorker.fullName}»"
                        return@launch
                    }

                    val targetWorker = matches.singleOrNull() ?: importedWorker.copy(
                        id = nextWorkerId++,
                        orderIndex = existingWorkers.size + newWorkers.size + 1
                    ).also { newWorkers.add(it) }
                    workerIdMap[importedWorker.id] = targetWorker.id
                }

                val mappedEntries = result.entries.map { entry ->
                    val workerId = workerIdMap[entry.workerId]
                        ?: throw IllegalArgumentException("Не удалось сопоставить сотрудника из Excel")
                    entry.copy(workerId = workerId)
                }
                val importedWorkerIds = workerIdMap.values.toSet()
                val preservedAttendance = storage.getAttendance().filterNot { entry ->
                    entry.workerId in importedWorkerIds && entry.year == month.year && entry.month == month.month
                }

                storage.saveWorkers(existingWorkers + newWorkers)
                storage.saveAttendance(preservedAttendance + mappedEntries)
                _workers.value = storage.getWorkers()
                _attendance.value = storage.getAttendance()
                _currentMonth.value = month
                _operationStatus.value = "Импортировано: ${result.workers.size} сотрудников, ${mappedEntries.size} отметок за ${month.month}/${month.year}. Остальные данные сохранены."
            } catch (e: Exception) {
                e.printStackTrace()
                _operationStatus.value = "Ошибка при импорте Excel: ${e.message}"
            }
        }
    }

    fun resetToTemplateData() {
        viewModelScope.launch(Dispatchers.IO) {
            storage.initDefaultData()
            storage.initDefaultAdvances()
            _workers.value = storage.getWorkers()
            _attendance.value = storage.getAttendance()
            _advances.value = storage.getAdvances()
            _currentMonth.value = MonthInfo(2026, 10)
            _operationStatus.value = "Данные восстановлены из шаблона (Октябрь 2026)"
        }
    }

    // ==========================================
    // ACCOUNTING / ADVANCES (БУХГАЛТЕРИЯ)
    // ==========================================

    fun addAdvance(
        workerId: Long,
        workerName: String,
        date: String,
        amount: Double,
        currency: String = "zł",
        comment: String = "Аванс",
        paymentMethod: String = "Наличные"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val adv = AdvancePayment(
                id = System.currentTimeMillis(),
                workerId = workerId,
                workerName = workerName,
                date = date,
                amount = amount,
                currency = currency,
                comment = comment,
                paymentMethod = paymentMethod
            )
            storage.addAdvance(adv)
            _advances.value = storage.getAdvances()
            _operationStatus.value = "Аванс для $workerName ($amount $currency) добавлен"
        }
    }

    fun addBatchAdvances(
        date: String,
        payments: List<Pair<Worker, Double>>,
        comment: String = "Аванс",
        paymentMethod: String = "Наличные"
    ) {
        saveAdvanceSheet(null, date, payments, paymentMethod, comment)
    }

    fun saveAdvanceSheet(
        oldDate: String?,
        newDate: String,
        payments: List<Pair<Worker, Double>>,
        paymentMethod: String = "Наличные",
        comment: String = "Аванс"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (payments.any { (_, amount) -> amount <= 0.0 || !amount.isFinite() }) {
                _operationStatus.value = "Ведомость не сохранена: суммы должны быть больше нуля"
                return@launch
            }

            // An edit replaces the source date group. Never clear an existing group at the target date.
            if (oldDate != null) {
                storage.deleteAdvancesForDate(oldDate)
            }
            val list = payments.mapIndexed { idx, (worker, amount) ->
                AdvancePayment(
                    id = System.currentTimeMillis() + idx,
                    workerId = worker.id,
                    workerName = worker.fullName,
                    date = newDate,
                    amount = amount,
                    currency = "zł",
                    comment = comment,
                    paymentMethod = paymentMethod
                )
            }
            storage.addAdvances(list)
            _advances.value = storage.getAdvances()
            val total = list.sumOf { it.amount }
            val totalStr = if (total % 1.0 == 0.0) total.toInt().toString() else "%.2f".format(total)
            _operationStatus.value = "Ведомость за $newDate сохранена (${list.size} выплат, Итого: $totalStr zł)"
        }
    }

    fun deleteAdvance(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteAdvance(id)
            _advances.value = storage.getAdvances()
            _operationStatus.value = "Запись об авансе удалена"
        }
    }

    fun deleteAdvancesForDate(date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteAdvancesForDate(date)
            _advances.value = storage.getAdvances()
            _operationStatus.value = "Ведомость за $date удалена"
        }
    }

    /**
     * Parses multi-line text format:
     * 13.10.2026
     * 1:Maksym Terekhov 700
     * 2:Roman Hardizon 800
     * ...
     * Итого 5700
     */
    fun parseAndSaveAdvanceText(rawText: String, onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) {
                    launch(Dispatchers.Main) { onFinished(false, "Текст пуст") }
                    return@launch
                }

                var detectedDate: String? = null
                val dateRegex = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})")
                for (line in lines) {
                    val matcher = dateRegex.matcher(line)
                    if (matcher.find()) {
                        val day = matcher.group(1)?.toIntOrNull() ?: 1
                        val month = matcher.group(2)?.toIntOrNull() ?: 1
                        val year = matcher.group(3)?.toIntOrNull() ?: 2026
                        detectedDate = "%02d.%02d.%04d".format(day, month, year)
                        break
                    }
                }
                val resolvedDate = detectedDate ?: "%02d.%02d.%04d".format(13, _currentMonth.value.month, _currentMonth.value.year)
                val currentWorkers = storage.getWorkers()
                val linePattern = Pattern.compile(
                    "^(?:\\d+[\\s.:)\\-#]+|\\d+\\s+)?(.*?)\\s+([0-9]+(?:[.,][0-9]+)?)\\s*(zł|zl|pln|uah|грн)?$",
                    Pattern.CASE_INSENSITIVE
                )
                val newAdvances = mutableListOf<AdvancePayment>()
                var validationError: String? = null

                lines.forEachIndexed { index, line ->
                    if (validationError != null) return@forEachIndexed
                    if (dateRegex.matcher(line).find() ||
                        line.startsWith("итого", ignoreCase = true) ||
                        line.startsWith("total", ignoreCase = true) ||
                        line.startsWith("razem", ignoreCase = true) ||
                        line.startsWith("suma", ignoreCase = true)
                    ) return@forEachIndexed

                    val matcher = linePattern.matcher(line)
                    if (!matcher.matches()) {
                        if (line.any { it.isDigit() }) {
                            validationError = "Не удалось разобрать строку: «$line»"
                        }
                        return@forEachIndexed
                    }

                    val rawName = matcher.group(1)?.trim().orEmpty()
                    val amount = matcher.group(2)?.replace(',', '.')?.toDoubleOrNull()
                    val currency = matcher.group(3)?.lowercase(Locale.ROOT)
                    if (currency == "uah" || currency == "грн") {
                        validationError = "Выплаты в гривнах нельзя смешивать с PLN. Укажите суммы в zł/PLN."
                        return@forEachIndexed
                    }
                    if (rawName.isBlank() || amount == null || !amount.isFinite() || amount <= 0.0) {
                        validationError = "Проверьте имя и сумму в строке: «$line»"
                        return@forEachIndexed
                    }

                    val matches = currentWorkers.filter { normalizedWorkerName(it.fullName) == normalizedWorkerName(rawName) }
                    if (matches.isEmpty()) {
                        validationError = "Сотрудник «$rawName» не найден. Добавьте его в список и повторите импорт."
                        return@forEachIndexed
                    }
                    if (matches.size > 1) {
                        validationError = "Имя «$rawName» совпадает с несколькими сотрудниками. Уточните список перед импортом."
                        return@forEachIndexed
                    }

                    val worker = matches.single()
                    newAdvances.add(
                        AdvancePayment(
                            id = System.currentTimeMillis() + index,
                            workerId = worker.id,
                            workerName = worker.fullName,
                            date = resolvedDate,
                            amount = amount,
                            currency = "zł",
                            comment = "Аванс",
                            paymentMethod = "Наличные"
                        )
                    )
                }

                if (validationError != null) {
                    val message = validationError!!
                    _operationStatus.value = message
                    launch(Dispatchers.Main) { onFinished(false, message) }
                    return@launch
                }

                if (newAdvances.isNotEmpty()) {
                    storage.addAdvances(newAdvances)
                    _advances.value = storage.getAdvances()
                    val total = newAdvances.sumOf { it.amount }
                    val totalFormatted = if (total % 1.0 == 0.0) total.toInt().toString() else "%.2f".format(total)
                    val message = "Успешно сохранено ${newAdvances.size} выплат на $resolvedDate на сумму $totalFormatted zł"
                    _operationStatus.value = message
                    launch(Dispatchers.Main) { onFinished(true, message) }
                } else {
                    launch(Dispatchers.Main) { onFinished(false, "Не найдены строки выплат с сотрудниками и суммами.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) { onFinished(false, "Ошибка: ${e.message}") }
            }
        }
    }

    fun clearOperationStatus() {
        _operationStatus.value = null
    }
}
