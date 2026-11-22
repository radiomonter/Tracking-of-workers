package com.workertracking.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workertracking.app.ui.components.GoogleDriveSyncDialog
import com.workertracking.app.ui.screens.AccountingScreen
import com.workertracking.app.ui.screens.ExcelScreen
import com.workertracking.app.ui.screens.SummaryScreen
import com.workertracking.app.ui.screens.TimesheetScreen
import com.workertracking.app.ui.screens.WorkersScreen
import com.workertracking.app.ui.theme.ColorPresent
import com.workertracking.app.ui.theme.PrimaryBlue
import com.workertracking.app.ui.theme.WorkerTrackingTheme
import com.workertracking.app.viewmodel.MainViewModel

enum class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TIMESHEET("Табель", Icons.Default.TableChart),
    WORKERS("Рабочие", Icons.Default.Group),
    ACCOUNTING("Бухгалтерия", Icons.Default.AccountBalanceWallet),
    SUMMARY("Сводка", Icons.Default.Assessment),
    EXCEL("Excel", Icons.Default.Description)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WorkerTrackingTheme {
                var selectedTab by remember { mutableStateOf(AppTab.TIMESHEET) }
                val settings by viewModel.appSettings.collectAsState()
                val syncStatus by viewModel.syncStatus.collectAsState()
                var showSignatureDialog by remember { mutableStateOf(false) }
                var showDriveSyncDialog by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // DreamHaus Logo icon
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(PrimaryBlue.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_dreamhaus_logo),
                                            contentDescription = "DreamHaus Logo",
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Text(
                                            text = settings.companyName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Табель Учёта • Lista Obecności",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            actions = {
                                // Signature Chip (Maksym Terekhov)
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showSignatureDialog = true },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "✍️ ${settings.responsiblePerson}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryBlue
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Изменить подпись",
                                            modifier = Modifier.size(12.dp),
                                            tint = PrimaryBlue
                                        )
                                    }
                                }

                                // Quick Export Button for Timesheet
                                IconButton(
                                    onClick = {
                                        viewModel.exportToExcel { exportedFile ->
                                            val shareIntent = viewModel.excelManager.getShareIntent(exportedFile)
                                            startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться табелем Excel"))
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Быстрый экспорт табеля",
                                        tint = ColorPresent
                                    )
                                }

                                // Google Drive Sync Button
                                IconButton(onClick = { showDriveSyncDialog = true }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CloudSync,
                                            contentDescription = "Синхронизация с Google Диском",
                                            tint = if (syncStatus.isSignedIn) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (syncStatus.isSignedIn) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(ColorPresent)
                                                    .align(Alignment.TopEnd)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            AppTab.values().forEach { tab ->
                                val selected = selectedTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                                    label = { Text(text = tab.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (selectedTab) {
                            AppTab.TIMESHEET -> TimesheetScreen(viewModel = viewModel)
                            AppTab.WORKERS -> WorkersScreen(viewModel = viewModel)
                            AppTab.ACCOUNTING -> AccountingScreen(viewModel = viewModel)
                            AppTab.SUMMARY -> SummaryScreen(viewModel = viewModel)
                            AppTab.EXCEL -> ExcelScreen(viewModel = viewModel)
                        }
                    }
                }

                // Signature & Company Settings Dialog
                if (showSignatureDialog) {
                    SignatureSettingsDialog(
                        currentCompany = settings.companyName,
                        currentPerson = settings.responsiblePerson,
                        onDismiss = { showSignatureDialog = false },
                        onSave = { comp, pers ->
                            viewModel.updateSettings(comp, pers)
                            showSignatureDialog = false
                        }
                    )
                }

                // Google Drive Sync Dialog
                if (showDriveSyncDialog) {
                    GoogleDriveSyncDialog(
                        viewModel = viewModel,
                        onDismiss = { showDriveSyncDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
fun SignatureSettingsDialog(
    currentCompany: String,
    currentPerson: String,
    onDismiss: () -> Unit,
    onSave: (company: String, person: String) -> Unit
) {
    var company by remember { mutableStateOf(currentCompany) }
    var person by remember { mutableStateOf(currentPerson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Настройки подписи документов",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Эти данные будут автоматически указываться в шапке и подписях всех экспортируемых документов Excel (Табель и Реестр рабочих):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Название фирмы (Firma)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text("Ответственный за заполнение / Sporządził") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = PrimaryBlue.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Пример в документах:\n«Заполнил / Sporządził: $person ($company)»",
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(company, person) }
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
