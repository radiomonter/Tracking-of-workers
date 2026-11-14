package com.workertracking.app.ui.screens

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.workertracking.app.model.AdvancePayment
import com.workertracking.app.model.DateAdvanceGroup
import com.workertracking.app.model.Worker
import com.workertracking.app.ui.theme.ColorAbsent
import com.workertracking.app.ui.theme.ColorAbsentBg
import com.workertracking.app.ui.theme.ColorPresent
import com.workertracking.app.ui.theme.ColorPresentBg
import com.workertracking.app.ui.theme.PrimaryBlue
import com.workertracking.app.viewmodel.MainViewModel
import java.util.Calendar

@Composable
fun AccountingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsState()
    val allWorkers by viewModel.workers.collectAsState()
    val dateGroups by viewModel.dateAdvanceGroups.collectAsState()
    val operationStatus by viewModel.operationStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<DateAdvanceGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<DateAdvanceGroup?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }

    // Filter statements by search query (by date or worker name in the list)
    val filteredGroups = if (searchQuery.isBlank()) {
        dateGroups
    } else {
        dateGroups.filter { group ->
            group.date.contains(searchQuery, ignoreCase = true) ||
            group.items.any { it.workerName.contains(searchQuery, ignoreCase = true) }
        }
    }

    val totalAdvancesSum = dateGroups.sumOf { it.totalAmount }
    val totalPayoutsCount = dateGroups.sumOf { it.items.size }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Создать ведомость")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header (точно как во вкладке «Рабочие»)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Бухгалтерия • ${settings.companyName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ведомостей: ${dateGroups.size} • Выплат: $totalPayoutsCount • Итого: ${totalAdvancesSum.toInt()} zł",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Quick Paste button
                OutlinedButton(
                    onClick = { showPasteDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Вставить", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Operation status message banner
            if (operationStatus != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    color = PrimaryBlue.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = operationStatus ?: "",
                            fontSize = 12.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearOperationStatus() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Search (Поиск по дате или имени)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск по дате (13.10.2026) или фамилии...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Statements List
            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Ничего не найдено по запросу" else "Ведомостей пока нет.\nНажмите «+» чтобы создать первую ведомость.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredGroups) { group ->
                        StatementCard(
                            group = group,
                            onEdit = { editingGroup = group },
                            onDelete = { groupToDelete = group },
                            onExportTxt = {
                                val textFile = viewModel.excelManager.exportDateAdvanceGroupToTextFile(group)
                                val shareIntent = viewModel.excelManager.getTextFileShareIntent(
                                    textFile,
                                    "Ведомость авансов за ${group.date}"
                                )
                                context.startActivity(Intent.createChooser(shareIntent, "Экспорт ведомости в текстовый документ (.txt)"))
                            },
                            onCopy = {
                                val text = group.formatAsShareText()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Ведомость ${group.date}", text))
                                Toast.makeText(context, "Ведомость скопирована в буфер", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // 1. CREATE STATEMENT DIALOG
    // ==========================================
    if (showCreateDialog) {
        StatementFormDialog(
            title = "Новая ведомость выплат",
            initialDate = "%02d.%02d.%04d".format(
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
                Calendar.getInstance().get(Calendar.MONTH) + 1,
                Calendar.getInstance().get(Calendar.YEAR)
            ),
            initialItems = emptyList(),
            allWorkers = allWorkers.filter { it.isActive },
            onDismiss = { showCreateDialog = false },
            onSave = { date, list ->
                viewModel.saveAdvanceSheet(
                    oldDate = null,
                    newDate = date,
                    payments = list
                )
                showCreateDialog = false
            }
        )
    }

    // ==========================================
    // 2. EDIT STATEMENT DIALOG
    // ==========================================
    editingGroup?.let { group ->
        val activeWorkers = allWorkers.filter { it.isActive }
        // Map existing advance payments into (Worker, Amount) list
        val currentItems = group.orderedItems.mapNotNull { adv ->
            val w = allWorkers.firstOrNull { it.id == adv.workerId }
                ?: Worker(id = adv.workerId, orderIndex = 0, fullName = adv.workerName)
            Pair(w, adv.amount)
        }

        StatementFormDialog(
            title = "Редактирование ведомости",
            initialDate = group.date,
            initialItems = currentItems,
            allWorkers = activeWorkers,
            onDismiss = { editingGroup = null },
            onSave = { newDate, updatedList ->
                viewModel.saveAdvanceSheet(
                    oldDate = group.date,
                    newDate = newDate,
                    payments = updatedList
                )
                editingGroup = null
            }
        )
    }

    // ==========================================
    // 3. DELETE CONFIRMATION DIALOG
    // ==========================================
    groupToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("Удалить ведомость?") },
            text = { Text("Удалить ведомость за ${target.date} (${target.items.size} выплат на сумму ${target.totalAmount.toInt()} zł)?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAdvancesForDate(target.date)
                        groupToDelete = null
                        Toast.makeText(context, "Ведомость за ${target.date} удалена", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAbsent)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { groupToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // ==========================================
    // 4. QUICK PASTE TEXT DIALOG
    // ==========================================
    if (showPasteDialog) {
        PasteAdvanceTextDialog(
            onDismiss = { showPasteDialog = false },
            onSave = { text ->
                viewModel.parseAndSaveAdvanceText(text) { success, msg ->
                    if (success) {
                        showPasteDialog = false
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

// ==========================================
// STATEMENT CARD (КАРТОЧКА ВЕДОМОСТИ В СТИЛЕ ВКЛАДКИ РАБОЧИЕ)
// ==========================================

@Composable
fun StatementCard(
    group: DateAdvanceGroup,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExportTxt: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Date & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calendar icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrimaryBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.date,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${group.items.size} сотрудников в списке",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Total Badge
                Surface(
                    color = ColorPresentBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Итого ${if (group.totalAmount % 1.0 == 0.0) group.totalAmount.toInt().toString() else "%.2f".format(group.totalAmount)} zł",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorPresent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Delete button
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить ведомость",
                        tint = ColorAbsent.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // List of workers (автоматическая нумерация 1:, 2:, 3:...)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.orderedItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PrimaryBlue,
                            modifier = Modifier.width(26.dp)
                        )

                        Text(
                            text = item.workerName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        val amountStr = if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else "%.2f".format(item.amount)
                        Text(
                            text = "$amountStr zł",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Toolbar at Bottom of Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Summary Text
                val totalStr = if (group.totalAmount % 1.0 == 0.0) group.totalAmount.toInt().toString() else "%.2f".format(group.totalAmount)
                Text(
                    text = "Итого $totalStr zł",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorPresent
                )

                // Right: Buttons [Редактировать], [.TXT], [Копия]
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Edit button
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Изменить", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Export .TXT button
                    OutlinedButton(
                        onClick = onExportTxt,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(".txt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Copy button
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ==========================================
// STATEMENT FORM DIALOG (СОЗДАНИЕ И РЕДАКТИРОВАНИЕ СПИСКА ВЫПЛАТ)
// ==========================================

data class StatementDraftRow(
    val tempId: Long = System.currentTimeMillis() + (0..10000).random(),
    var worker: Worker,
    var amountText: String = "700"
)

@Composable
fun StatementFormDialog(
    title: String,
    initialDate: String,
    initialItems: List<Pair<Worker, Double>>,
    allWorkers: List<Worker>,
    onDismiss: () -> Unit,
    onSave: (date: String, items: List<Pair<Worker, Double>>) -> Unit
) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(initialDate) }

    // Draft rows list
    val rows = remember {
        mutableStateListOf<StatementDraftRow>().apply {
            if (initialItems.isNotEmpty()) {
                initialItems.forEach { (w, amt) ->
                    val amtStr = if (amt % 1.0 == 0.0) amt.toInt().toString() else "%.2f".format(amt)
                    add(StatementDraftRow(worker = w, amountText = amtStr))
                }
            } else {
                // By default, add first worker if list is empty
                allWorkers.firstOrNull()?.let { firstWorker ->
                    add(StatementDraftRow(worker = firstWorker, amountText = "700"))
                }
            }
        }
    }

    var workerPickerRowIndex by remember { mutableStateOf<Int?>(null) }

    // Native DatePickerDialog (выбор даты на календаре в один клик)
    val calendar = Calendar.getInstance()
    val parts = date.split(".")
    val initDay = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
    val initMonth = (parts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
    val initYear = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)

    val datePickerDialog = DatePickerDialog(
        context,
        { _, y, m, d ->
            date = "%02d.%02d.%04d".format(d, m + 1, y)
        },
        initYear,
        initMonth,
        initDay
    )

    // Calculate live total
    val liveTotal = rows.sumOf { it.amountText.replace(',', '.').toDoubleOrNull() ?: 0.0 }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header (точно как в DetailedWorkerFormDialog)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = "Список рабочих и сумм", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                HorizontalDivider()

                // 1. ДАТА (Выбор на календаре)
                Text(text = "1. Дата ведомости", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { datePickerDialog.show() },
                    color = PrimaryBlue.copy(alpha = 0.06f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Дата выплат:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = date, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            text = "Выбрать дату 📅",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue
                        )
                    }
                }

                // 2. СПИСОК СОТРУДНИКОВ И СУММ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Список выплат (${rows.size} чел.)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )

                    // Fill all active workers in one click
                    TextButton(
                        onClick = {
                            rows.clear()
                            allWorkers.forEach { w ->
                                rows.add(StatementDraftRow(worker = w, amountText = "700"))
                            }
                        }
                    ) {
                        Text("Заполнить всеми (${allWorkers.size})", fontSize = 11.sp)
                    }
                }

                // List of rows in the dialog
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEachIndexed { index, rowItem ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // Row 1: Index + Worker name selector + Delete button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Автоматическая нумерация 1:, 2:, 3:...
                                    Text(
                                        text = "${index + 1}:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PrimaryBlue,
                                        modifier = Modifier.width(26.dp)
                                    )

                                    // Worker Selector Surface
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { workerPickerRowIndex = index },
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = rowItem.worker.fullName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Remove worker row
                                    IconButton(
                                        onClick = { rows.removeAt(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Удалить",
                                            tint = ColorAbsent.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Row 2: Fixed amount preset chips [400] [500] [600] [700] + Manual Input
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("400", "500", "600", "700").forEach { preset ->
                                        val isSelected = rowItem.amountText == preset
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    rows[index] = rowItem.copy(amountText = preset)
                                                },
                                            color = if (isSelected) ColorPresent else ColorPresentBg,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = preset,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isSelected) Color.White else ColorPresent
                                                )
                                            }
                                        }
                                    }

                                    // Manual input field
                                    OutlinedTextField(
                                        value = rowItem.amountText,
                                        onValueChange = { newText ->
                                            rows[index] = rowItem.copy(amountText = newText)
                                        },
                                        placeholder = { Text("zł") },
                                        singleLine = true,
                                        modifier = Modifier.width(72.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                            }
                        }
                    }
                }

                // Button: + Добавить сотрудника в список
                OutlinedButton(
                    onClick = {
                        // Pick next unselected worker if available, else first
                        val usedIds = rows.map { it.worker.id }.toSet()
                        val nextWorker = allWorkers.firstOrNull { it.id !in usedIds } ?: allWorkers.firstOrNull()
                        if (nextWorker != null) {
                            rows.add(StatementDraftRow(worker = nextWorker, amountText = "700"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Добавить сотрудника в список", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // 3. ПОДСЧЁТ ОБЩЕЙ СУММЫ (ИТОГО)
                Surface(
                    color = ColorPresent.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Итого по ведомости:",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val totalStr = if (liveTotal % 1.0 == 0.0) liveTotal.toInt().toString() else "%.2f".format(liveTotal)
                        Text(
                            text = "Итого $totalStr zł",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ColorPresent
                        )
                    }
                }

                // Buttons: Сохранить и Отмена
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            val validItems = rows.mapNotNull { row ->
                                val amt = row.amountText.replace(',', '.').toDoubleOrNull()
                                if (amt != null && amt > 0) Pair(row.worker, amt) else null
                            }
                            if (validItems.isNotEmpty()) {
                                onSave(date, validItems)
                            }
                        },
                        enabled = rows.any { (it.amountText.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 },
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Сохранить ведомость", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal Worker Picker for specific row in the dialog
    workerPickerRowIndex?.let { rowIndex ->
        if (rowIndex in rows.indices) {
            WorkerPickerModalDialog(
                workers = allWorkers,
                selectedWorker = rows[rowIndex].worker,
                onDismiss = { workerPickerRowIndex = null },
                onSelect = { selected ->
                    rows[rowIndex] = rows[rowIndex].copy(worker = selected)
                    workerPickerRowIndex = null
                }
            )
        }
    }
}

// ==========================================
// WORKER PICKER MODAL DIALOG
// ==========================================

@Composable
fun WorkerPickerModalDialog(
    workers: List<Worker>,
    selectedWorker: Worker?,
    onDismiss: () -> Unit,
    onSelect: (Worker) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = workers.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) ||
        it.position.contains(searchQuery, ignoreCase = true) ||
        it.orderIndex.toString() == searchQuery.trim()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Выберите сотрудника", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск по имени или номеру...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered) { worker ->
                        val isSelected = selectedWorker?.id == worker.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(worker) },
                            color = if (isSelected) PrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${worker.orderIndex}.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = worker.fullName,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = worker.position,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

// ==========================================
// PASTE ADVANCE TEXT DIALOG (БЫСТРАЯ ВСТАВКА ИЗ БУФЕРА)
// ==========================================

@Composable
fun PasteAdvanceTextDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val sampleText = """
13.10.2026
1:Maksym Terekhov 700
2:Roman Hardizon 800
3:Ivan Rolk 700
4:Oleksandr Kostornoi 700
5:Oleksandr Bakov 700
6:Vasyl Radishchuk 700
7:Andrii Ivanov 700
8:Jambulat Gviniashvili 700
Итого 5700
""".trimIndent()

    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Вставка ведомости текстом", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Вставьте или отредактируйте текст ведомости:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { inputText = sampleText }) {
                        Text("📋 Вставить пример (13.10.2026)", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Список рабочих и сумм") },
                    placeholder = {
                        Text("13.10.2026\n1:Maksym Terekhov 700\n...\nИтого 5700", fontSize = 12.sp)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(inputText) },
                enabled = inputText.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
