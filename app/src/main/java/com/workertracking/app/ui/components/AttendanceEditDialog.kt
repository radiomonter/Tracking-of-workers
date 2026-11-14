package com.workertracking.app.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.workertracking.app.model.AttendanceEntry
import com.workertracking.app.model.AttendanceStatus
import com.workertracking.app.model.MonthInfo
import com.workertracking.app.model.Worker
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceEditDialog(
    worker: Worker,
    day: Int,
    monthInfo: MonthInfo,
    currentEntry: AttendanceEntry?,
    onDismiss: () -> Unit,
    onSave: (hours: Double?, status: AttendanceStatus?, note: String) -> Unit
) {
    var selectedHours by remember {
        mutableStateOf(currentEntry?.hours?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }
    var selectedStatus by remember { mutableStateOf<AttendanceStatus?>(currentEntry?.status) }
    var note by remember { mutableStateOf(currentEntry?.note ?: "") }

    val presetHours = listOf("10", "8", "12", "7", "5", "4")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = worker.fullName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$day ${monthInfo.titleShort} (день $day)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Preset hours
                Text(
                    text = "Часы работы / Godziny:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetHours.forEach { h ->
                        val isSelected = selectedHours == h && selectedStatus == null
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedHours = h
                                    selectedStatus = null
                                },
                            color = if (isSelected) ColorPresent else ColorPresentBg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$h ч",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else ColorPresent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stepper & custom hours
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = selectedHours,
                        onValueChange = {
                            selectedHours = it
                            if (it.isNotBlank()) selectedStatus = null
                        },
                        label = { Text("Ввести часы вручную") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val cur = selectedHours.toDoubleOrNull() ?: 0.0
                            if (cur > 0.5) selectedHours = (cur - 1.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                            selectedStatus = null
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Минус 1")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val cur = selectedHours.toDoubleOrNull() ?: 0.0
                            selectedHours = (cur + 1.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                            selectedStatus = null
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Плюс 1")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Statuses (Неявка, Болезнь, Отпуск)
                Text(
                    text = "Статус отсутствия / Status:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        label = "Н (Неявка / N)",
                        status = AttendanceStatus.ABSENT,
                        isSelected = selectedStatus == AttendanceStatus.ABSENT,
                        activeColor = ColorAbsent,
                        bgColor = ColorAbsentBg
                    ) {
                        selectedStatus = AttendanceStatus.ABSENT
                        selectedHours = ""
                    }

                    StatusChip(
                        label = "Б (Болезнь / Ch)",
                        status = AttendanceStatus.SICK,
                        isSelected = selectedStatus == AttendanceStatus.SICK,
                        activeColor = ColorSick,
                        bgColor = ColorSickBg
                    ) {
                        selectedStatus = AttendanceStatus.SICK
                        selectedHours = ""
                    }

                    StatusChip(
                        label = "О (Отпуск / U)",
                        status = AttendanceStatus.VACATION,
                        isSelected = selectedStatus == AttendanceStatus.VACATION,
                        activeColor = ColorVacation,
                        bgColor = ColorVacationBg
                    ) {
                        selectedStatus = AttendanceStatus.VACATION
                        selectedHours = ""
                    }

                    StatusChip(
                        label = "В (Выходной / W)",
                        status = AttendanceStatus.DAY_OFF,
                        isSelected = selectedStatus == AttendanceStatus.DAY_OFF,
                        activeColor = ColorDayOff,
                        bgColor = ColorDayOffBg
                    ) {
                        selectedStatus = AttendanceStatus.DAY_OFF
                        selectedHours = ""
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Reset & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onSave(null, null, "")
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorAbsent)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Очистить")
                    }

                    Button(
                        onClick = {
                            val h = selectedHours.toDoubleOrNull()
                            onSave(h, selectedStatus, note)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    status: AttendanceStatus,
    isSelected: Boolean,
    activeColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) activeColor else bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (isSelected) Color.White else activeColor
        )
    }
}
