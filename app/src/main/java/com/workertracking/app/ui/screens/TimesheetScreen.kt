package com.workertracking.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workertracking.app.model.AttendanceEntry
import com.workertracking.app.model.AttendanceStatus
import com.workertracking.app.model.Worker
import com.workertracking.app.ui.components.AttendanceEditDialog
import com.workertracking.app.ui.components.MonthSelectorBar
import com.workertracking.app.ui.theme.ColorAbsent
import com.workertracking.app.ui.theme.ColorAbsentBg
import com.workertracking.app.ui.theme.ColorDayOff
import com.workertracking.app.ui.theme.ColorDayOffBg
import com.workertracking.app.ui.theme.ColorPresent
import com.workertracking.app.ui.theme.ColorPresentBg
import com.workertracking.app.ui.theme.ColorSick
import com.workertracking.app.ui.theme.ColorSickBg
import com.workertracking.app.ui.theme.ColorVacation
import com.workertracking.app.ui.theme.ColorVacationBg
import com.workertracking.app.viewmodel.MainViewModel

@Composable
fun TimesheetScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentMonth by viewModel.currentMonth.collectAsState()
    val workers by viewModel.filteredWorkers.collectAsState()
    val allAttendance by viewModel.attendance.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Dialog state for cell editing
    var editingCell by remember { mutableStateOf<Triple<Worker, Int, AttendanceEntry?>?>(null) }
    var showBatchDialog by remember { mutableStateOf(false) }

    val daysInMonth = currentMonth.daysInMonth
    val horizontalScrollState = rememberScrollState()

    // Attendance map for fast cell lookups
    val attendanceMap = remember(allAttendance, currentMonth) {
        allAttendance
            .filter { it.year == currentMonth.year && it.month == currentMonth.month }
            .associateBy { "${it.workerId}_${it.day}" }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Month navigation
        MonthSelectorBar(
            currentMonth = currentMonth,
            onPrev = { viewModel.prevMonth() },
            onNext = { viewModel.nextMonth() },
            onSelectMonth = { y, m -> viewModel.setMonth(y, m) }
        )

        // Search & Batch Actions bar & Excel Export button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Поиск...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Button(
                onClick = { showBatchDialog = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Смена", fontSize = 13.sp)
            }

            // Direct Export Button for Timesheet
            Button(
                onClick = {
                    viewModel.exportToExcel { exportedFile ->
                        val shareIntent = viewModel.excelManager.getShareIntent(exportedFile)
                        context.startActivity(Intent.createChooser(shareIntent, "Поделиться табелем Excel"))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPresent)
            ) {
                Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Excel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Table: Header + Scrollable Matrix
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Fixed Worker Header
                    Text(
                        text = "Сотрудник / Pracownik",
                        modifier = Modifier
                            .width(150.dp)
                            .padding(start = 12.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Scrollable Days 1..N + Totals
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (d in 1..daysInMonth) {
                            Text(
                                text = "$d",
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Totals headers
                        Text(
                            text = "Дни",
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Часы",
                            modifier = Modifier.width(54.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))

                // Table Body Rows
                if (workers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет сотрудников в списке.\nДобавьте сотрудников на вкладке «Рабочие» или импортируйте Excel.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(workers, key = { _, w -> w.id }) { index, worker ->
                            WorkerTimesheetRow(
                                orderIndex = index + 1,
                                worker = worker,
                                daysInMonth = daysInMonth,
                                attendanceMap = attendanceMap,
                                horizontalScrollState = horizontalScrollState,
                                onCellClick = { day, entry ->
                                    editingCell = Triple(worker, day, entry)
                                }
                            )
                            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        // Legend at bottom
        TimesheetLegend()

        // Signature Bar (DreamHaus & Maksym Terekhov)
        val settings by viewModel.appSettings.collectAsState()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "✍️ Заполнил: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${settings.responsiblePerson} (${settings.companyName})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.exportToExcel { exportedFile ->
                                val shareIntent = viewModel.excelManager.getShareIntent(exportedFile)
                                context.startActivity(Intent.createChooser(shareIntent, "Поделиться табелем Excel"))
                            }
                        },
                    color = ColorPresent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = ColorPresent, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Экспорт .xlsx",
                            fontSize = 11.sp,
                            color = ColorPresent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Edit Cell Dialog
    editingCell?.let { (worker, day, entry) ->
        AttendanceEditDialog(
            worker = worker,
            day = day,
            monthInfo = currentMonth,
            currentEntry = entry,
            onDismiss = { editingCell = null },
            onSave = { hours, status, note ->
                viewModel.setAttendance(worker.id, day, hours, status, note)
            }
        )
    }

    // Batch shift hours dialog
    if (showBatchDialog) {
        BatchShiftDialog(
            daysInMonth = daysInMonth,
            onDismiss = { showBatchDialog = false },
            onApply = { day, hours ->
                viewModel.batchSetHoursForDay(day, hours)
                showBatchDialog = false
            }
        )
    }
}

@Composable
private fun WorkerTimesheetRow(
    orderIndex: Int,
    worker: Worker,
    daysInMonth: Int,
    attendanceMap: Map<String, AttendanceEntry>,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onCellClick: (day: Int, entry: AttendanceEntry?) -> Unit
) {
    var totalDays = 0
    var totalHours = 0.0

    for (d in 1..daysInMonth) {
        val entry = attendanceMap["${worker.id}_$d"]
        if (entry != null && entry.hours != null && entry.hours > 0) {
            totalDays++
            totalHours += entry.hours
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sticky Worker Name + Index
        Row(
            modifier = Modifier
                .width(150.dp)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$orderIndex.",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = worker.fullName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (worker.position.isNotEmpty()) {
                    Text(
                        text = worker.position,
                        fontSize = 11.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Synchronously scrolled day cells
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (day in 1..daysInMonth) {
                val entry = attendanceMap["${worker.id}_$day"]
                TimesheetCell(
                    entry = entry,
                    onClick = { onCellClick(day, entry) }
                )
            }

            // Totals
            Text(
                text = "$totalDays",
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (totalHours % 1.0 == 0.0) totalHours.toInt().toString() else totalHours.toString(),
                modifier = Modifier.width(54.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TimesheetCell(
    entry: AttendanceEntry?,
    onClick: () -> Unit
) {
    val (bgColor, textColor, borderColor, text) = when {
        entry?.hours != null && entry.hours > 0 -> {
            val hText = if (entry.hours % 1.0 == 0.0) entry.hours.toInt().toString() else entry.hours.toString()
            Quadruple(ColorPresentBg, ColorPresent, Color(0xFF34D399), hText)
        }
        entry?.status == AttendanceStatus.ABSENT -> Quadruple(ColorAbsentBg, ColorAbsent, Color(0xFFF87171), "Н")
        entry?.status == AttendanceStatus.SICK -> Quadruple(ColorSickBg, ColorSick, Color(0xFFFBBF24), "Б")
        entry?.status == AttendanceStatus.VACATION -> Quadruple(ColorVacationBg, ColorVacation, Color(0xFF38BDF8), "О")
        entry?.status == AttendanceStatus.DAY_OFF -> Quadruple(ColorDayOffBg, ColorDayOff, Color(0xFF94A3B8), "В")
        entry?.status == AttendanceStatus.PRESENT -> Quadruple(ColorPresentBg, ColorPresent, Color(0xFF34D399), "Я")
        else -> Quadruple(Color.Transparent, Color.Transparent, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), "")
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (text.isNotEmpty()) 1.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun TimesheetLegend() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = ColorPresent, label = "Явка (ч)")
            LegendItem(color = ColorAbsent, label = "Н (неявка)")
            LegendItem(color = ColorSick, label = "Б (болезнь)")
            LegendItem(color = ColorVacation, label = "О (отпуск)")
            LegendItem(color = ColorDayOff, label = "В (выходной)")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BatchShiftDialog(
    daysInMonth: Int,
    onDismiss: () -> Unit,
    onApply: (day: Int, hours: Double) -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(1) }
    var selectedHours by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заполнить смену для всех") },
        text = {
            Column {
                Text(
                    text = "Быстро проставить часы работы всем активным сотрудникам на выбранный день:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = selectedDay.toString(),
                    onValueChange = {
                        val d = it.toIntOrNull()
                        if (d != null && d in 1..daysInMonth) selectedDay = d
                    },
                    label = { Text("День месяца (1..$daysInMonth)") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = selectedHours,
                    onValueChange = { selectedHours = it },
                    label = { Text("Количество часов (напр. 8, 10, 12)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = selectedHours.toDoubleOrNull() ?: 10.0
                    onApply(selectedDay, h)
                }
            ) {
                Text("Применить ко всем")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
