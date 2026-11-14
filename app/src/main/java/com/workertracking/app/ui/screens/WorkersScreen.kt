package com.workertracking.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.workertracking.app.model.Worker
import com.workertracking.app.ui.theme.ColorAbsent
import com.workertracking.app.ui.theme.ColorAbsentBg
import com.workertracking.app.ui.theme.ColorPresent
import com.workertracking.app.ui.theme.ColorPresentBg
import com.workertracking.app.ui.theme.ColorSick
import com.workertracking.app.ui.theme.ColorSickBg
import com.workertracking.app.ui.theme.PrimaryBlue
import com.workertracking.app.viewmodel.MainViewModel

@Composable
fun WorkersScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val workers by viewModel.filteredWorkers.collectAsState()
    val allWorkers by viewModel.workers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingWorker by remember { mutableStateOf<Worker?>(null) }
    var deletingWorker by remember { mutableStateOf<Worker?>(null) }

    val settings by viewModel.appSettings.collectAsState()
    val sepCount = allWorkers.count { it.hasSep }
    val activeCount = allWorkers.count { it.isActive }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить сотрудника")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header Stats & Export Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Реестр • ${settings.companyName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Составил: ${settings.responsiblePerson} • Всего: ${allWorkers.size} • SEP: $sepCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Button: Export Workers List to Excel
                Button(
                    onClick = {
                        viewModel.exportWorkersToExcel { exportedFile ->
                            val shareIntent = viewModel.excelManager.getShareIntent(exportedFile)
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться списком сотрудников Excel"))
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

            Spacer(modifier = Modifier.height(10.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Поиск по имени, должности, телефону, SEP...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Workers List
            if (workers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Сотрудники не найдены.\nНажмите «+» чтобы добавить первого рабочего.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(workers, key = { _, w -> w.id }) { index, worker ->
                        DetailedWorkerCard(
                            orderIndex = index + 1,
                            worker = worker,
                            onEdit = { editingWorker = worker },
                            onDelete = { deletingWorker = worker },
                            onCall = {
                                if (worker.phone.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${worker.phone}"))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Worker Dialog
    if (showAddDialog) {
        DetailedWorkerFormDialog(
            title = "Добавить сотрудника",
            initialWorker = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { workerData ->
                viewModel.addWorker(
                    fullName = workerData.fullName,
                    position = workerData.position,
                    hourlyRate = workerData.hourlyRate,
                    phone = workerData.phone,
                    hasSep = workerData.hasSep,
                    sepDetails = workerData.sepDetails,
                    shoeSize = workerData.shoeSize,
                    jacketSize = workerData.jacketSize,
                    pantsSize = workerData.pantsSize,
                    birthYear = workerData.birthYear,
                    notes = workerData.notes
                )
                showAddDialog = false
            }
        )
    }

    // Edit Worker Dialog
    editingWorker?.let { worker ->
        DetailedWorkerFormDialog(
            title = "Редактировать сотрудника",
            initialWorker = worker,
            onDismiss = { editingWorker = null },
            onConfirm = { updated ->
                viewModel.updateWorker(updated)
                editingWorker = null
            }
        )
    }

    // Delete Confirmation
    deletingWorker?.let { worker ->
        AlertDialog(
            onDismissRequest = { deletingWorker = null },
            title = { Text("Удалить сотрудника?") },
            text = { Text("Вы уверены, что хотите удалить «${worker.fullName}» из списка? Все его отметки в табеле будут удалены.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorker(worker.id)
                        deletingWorker = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAbsent)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingWorker = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailedWorkerCard(
    orderIndex: Int,
    worker: Worker,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (worker.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: Index, Full Name, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (worker.isActive) PrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$orderIndex",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (worker.isActive) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = worker.fullName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (worker.position.isNotEmpty()) {
                        Text(
                            text = worker.position,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Rate Badge
                if (worker.hourlyRate > 0) {
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${worker.hourlyRate} zł/ч",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PrimaryBlue
                        )
                    }
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Редактировать", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Удалить", tint = ColorAbsent, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Phone & Birth year / Age & SEP Badge
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Phone
                if (worker.phone.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCall),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = worker.phone, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Age & Birth Year
                if (worker.birthYear != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cake, contentDescription = null, tint = ColorSick, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            val ageText = worker.age?.let { "$it лет (${worker.birthYear} г.р.)" } ?: "${worker.birthYear} г.р."
                            Text(text = ageText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // SEP Status
                if (worker.hasSep) {
                    Surface(
                        color = ColorPresentBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = ColorPresent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            val sepLabel = if (worker.sepDetails.isNotBlank()) "SEP: ${worker.sepDetails}" else "SEP ✓"
                            Text(text = sepLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorPresent)
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Без SEP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Workwear sizes row (Обувь, Куртка, Штаны)
            val hasSizes = worker.shoeSize.isNotBlank() || worker.jacketSize.isNotBlank() || worker.pantsSize.isNotBlank()
            if (hasSizes) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Спецодежда:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (worker.shoeSize.isNotBlank()) {
                        SizeTag(label = "Обувь", value = worker.shoeSize)
                    }
                    if (worker.jacketSize.isNotBlank()) {
                        SizeTag(label = "Куртка", value = worker.jacketSize)
                    }
                    if (worker.pantsSize.isNotBlank()) {
                        SizeTag(label = "Штаны", value = worker.pantsSize)
                    }
                }
            }

            if (worker.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Примечание: ${worker.notes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SizeTag(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DetailedWorkerFormDialog(
    title: String,
    initialWorker: Worker?,
    onDismiss: () -> Unit,
    onConfirm: (Worker) -> Unit
) {
    var name by remember { mutableStateOf(initialWorker?.fullName ?: "") }
    var position by remember { mutableStateOf(initialWorker?.position ?: "") }
    var rateStr by remember { mutableStateOf(initialWorker?.hourlyRate?.let { if (it > 0) it.toString() else "" } ?: "") }
    var phone by remember { mutableStateOf(initialWorker?.phone ?: "") }

    var hasSep by remember { mutableStateOf(initialWorker?.hasSep ?: false) }
    var sepDetails by remember { mutableStateOf(initialWorker?.sepDetails ?: "") }

    var shoeSize by remember { mutableStateOf(initialWorker?.shoeSize ?: "") }
    var jacketSize by remember { mutableStateOf(initialWorker?.jacketSize ?: "") }
    var pantsSize by remember { mutableStateOf(initialWorker?.pantsSize ?: "") }

    var birthYearStr by remember { mutableStateOf(initialWorker?.birthYear?.toString() ?: "") }
    var notes by remember { mutableStateOf(initialWorker?.notes ?: "") }
    var isActive by remember { mutableStateOf(initialWorker?.isActive ?: true) }

    var hasNameError by remember { mutableStateOf(false) }

    val calculatedAge = remember(birthYearStr) {
        val y = birthYearStr.toIntOrNull()
        if (y != null && y in 1940..java.time.Year.now().value) {
            java.time.Year.now().value - y
        } else null
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                HorizontalDivider()

                // 1. Basic Info
                Text(text = "Основная информация", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        hasNameError = it.isBlank()
                    },
                    label = { Text("ФИО сотрудника *") },
                    isError = hasNameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Должность (напр. Электромонтажник)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rateStr,
                        onValueChange = { rateStr = it },
                        label = { Text("Ставка (zł/ч)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Телефон") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }

                HorizontalDivider()

                // 2. Qualification (SEP)
                Text(text = "Квалификация и допуски", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Имеется ли допуск SEP?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(text = "Польская квалификация электробезопасности", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = hasSep, onCheckedChange = { hasSep = it })
                }

                if (hasSep) {
                    OutlinedTextField(
                        value = sepDetails,
                        onValueChange = { sepDetails = it },
                        label = { Text("Категория SEP (напр. G1 do 1kV, E+D)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // 3. Birth year & Age
                Text(text = "Год рождения и возраст", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = birthYearStr,
                        onValueChange = { birthYearStr = it },
                        label = { Text("Год рождения (19xx / 20xx)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )

                    Surface(
                        modifier = Modifier.weight(0.8f),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Возраст", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = calculatedAge?.let { "$it лет" } ?: "—",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 4. Workwear Sizes
                Text(text = "Размеры спецодежды", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = shoeSize,
                        onValueChange = { shoeSize = it },
                        label = { Text("Обувь") },
                        placeholder = { Text("43") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = jacketSize,
                        onValueChange = { jacketSize = it },
                        label = { Text("Куртка") },
                        placeholder = { Text("XL / 52") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = pantsSize,
                        onValueChange = { pantsSize = it },
                        label = { Text("Штаны") },
                        placeholder = { Text("50 / L") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()

                // 5. Notes & Active toggle
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Примечания / Адрес / Доп. инфо") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialWorker != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Активен в табеле", fontSize = 14.sp)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                hasNameError = true
                            } else {
                                val rate = rateStr.toDoubleOrNull() ?: 0.0
                                val by = birthYearStr.toIntOrNull()
                                val worker = (initialWorker ?: Worker(fullName = name.trim())).copy(
                                    fullName = name.trim(),
                                    position = position.trim(),
                                    hourlyRate = rate,
                                    phone = phone.trim(),
                                    hasSep = hasSep,
                                    sepDetails = if (hasSep) sepDetails.trim() else "",
                                    shoeSize = shoeSize.trim(),
                                    jacketSize = jacketSize.trim(),
                                    pantsSize = pantsSize.trim(),
                                    birthYear = by,
                                    notes = notes.trim(),
                                    isActive = isActive
                                )
                                onConfirm(worker)
                            }
                        },
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
