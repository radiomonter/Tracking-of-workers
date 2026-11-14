package com.workertracking.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workertracking.app.model.WorkerMonthSummary
import com.workertracking.app.ui.components.MonthSelectorBar
import com.workertracking.app.ui.theme.AccentCyan
import com.workertracking.app.ui.theme.ColorAbsent
import com.workertracking.app.ui.theme.ColorPresent
import com.workertracking.app.ui.theme.ColorSick
import com.workertracking.app.ui.theme.ColorVacation
import com.workertracking.app.ui.theme.PrimaryBlue
import com.workertracking.app.viewmodel.MainViewModel

@Composable
fun SummaryScreen(viewModel: MainViewModel) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val summaries by viewModel.monthSummaries.collectAsState()

    val totalHoursAll = summaries.sumOf { it.totalHours }
    val totalDaysAll = summaries.sumOf { it.totalDays }
    val totalSickAll = summaries.sumOf { it.sickDays }
    val totalAbsentAll = summaries.sumOf { it.absentDays }
    val totalVacationAll = summaries.sumOf { it.vacationDays }
    val totalPayAll = summaries.sumOf { it.estimatedPay }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Month navigation
        MonthSelectorBar(
            currentMonth = currentMonth,
            onPrev = { viewModel.prevMonth() },
            onNext = { viewModel.nextMonth() },
            onSelectMonth = { y, m -> viewModel.setMonth(y, m) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Big KPI Grid (2x2 cards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = "Всего часов / Razem",
                value = if (totalHoursAll % 1.0 == 0.0) totalHoursAll.toInt().toString() else "%.1f".format(totalHoursAll),
                subtitle = "Отработано за месяц",
                icon = Icons.Default.AccessTime,
                color = ColorPresent,
                modifier = Modifier.weight(1f)
            )

            KpiMetricCard(
                title = "Смен / Dni",
                value = "$totalDaysAll",
                subtitle = "Человеко-дней",
                icon = Icons.Default.CalendarMonth,
                color = PrimaryBlue,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = "Работников",
                value = "${summaries.count { it.worker.isActive }}",
                subtitle = "В табеле",
                icon = Icons.Default.People,
                color = AccentCyan,
                modifier = Modifier.weight(1f)
            )

            if (totalPayAll > 0) {
                KpiMetricCard(
                    title = "Фонд оплаты",
                    value = "%.0f zł".format(totalPayAll),
                    subtitle = "Расчетная сумма",
                    icon = Icons.Default.LocalAtm,
                    color = ColorSick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                KpiMetricCard(
                    title = "Пропуски / Отпуска",
                    value = "$totalSickAll / $totalAbsentAll",
                    subtitle = "Больничные / Неявки",
                    icon = Icons.Default.LocalAtm,
                    color = ColorSick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Worker Breakdown Title
        Text(
            text = "Сводка по каждому сотруднику",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Workers Summary List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(summaries.sortedByDescending { it.totalHours }) { summary ->
                WorkerSummaryCard(summary = summary)
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkerSummaryCard(summary: WorkerMonthSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.worker.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (summary.worker.position.isNotEmpty()) {
                        Text(
                            text = summary.worker.position,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = ColorPresent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${if (summary.totalHours % 1.0 == 0.0) summary.totalHours.toInt().toString() else "%.1f".format(summary.totalHours)} ч",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorPresent
                        )
                    }

                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${summary.totalDays} дн",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PrimaryBlue
                        )
                    }
                }
            }

            if (summary.sickDays > 0 || summary.absentDays > 0 || summary.vacationDays > 0 || summary.estimatedPay > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (summary.sickDays > 0) {
                            Text(
                                text = "Б: ${summary.sickDays} дн",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorSick
                            )
                        }
                        if (summary.absentDays > 0) {
                            Text(
                                text = "Н: ${summary.absentDays} дн",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorAbsent
                            )
                        }
                        if (summary.vacationDays > 0) {
                            Text(
                                text = "О: ${summary.vacationDays} дн",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorVacation
                            )
                        }
                    }

                    if (summary.estimatedPay > 0) {
                        Text(
                            text = "%.0f zł".format(summary.estimatedPay),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PrimaryBlue
                        )
                    }
                }
            }
        }
    }
}
