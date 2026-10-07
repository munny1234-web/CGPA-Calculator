package com.ferdousmunny.cgpacalculator

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferdousmunny.cgpacalculator.data.ThemeMode
import com.ferdousmunny.cgpacalculator.model.Departments
import com.ferdousmunny.cgpacalculator.util.AppLanguage
import com.ferdousmunny.cgpacalculator.util.LocalAppStrings
import com.ferdousmunny.cgpacalculator.util.Reminder
import com.ferdousmunny.cgpacalculator.util.ReminderManager
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    department: String,
    onDepartmentChange: (String) -> Unit,
    university: String,
    onUniversityChange: (String) -> Unit,
    totalCreditsRequired: Double,
    onTotalCreditsRequiredChange: (Double) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    biometricLockEnabled: Boolean,
    onBiometricLockChange: (Boolean) -> Unit,
    gradingScale: String,
    onGradingScaleChange: (String) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onClearData: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var departmentExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var universityText by remember { mutableStateOf(university) }
    var creditsText by remember { mutableStateOf(totalCreditsRequired.toInt().toString()) }

    var semesterReminderEnabled by remember { mutableStateOf(ReminderManager.isSemesterReminderEnabled(context)) }
    var customReminders by remember { mutableStateOf(ReminderManager.loadReminders(context)) }
    var pendingReminderTimestamp by remember { mutableStateOf<Long?>(null) }
    var newReminderTitle by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            semesterReminderEnabled = true
            ReminderManager.setSemesterReminderEnabled(context, true)
        } else {
            Toast.makeText(context, "Notification permission is needed for reminders", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SettingsSection(title = s.universitySetting) {
            OutlinedTextField(
                value = universityText,
                onValueChange = {
                    universityText = it
                    onUniversityChange(it)
                },
                placeholder = { Text(s.universityHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.departmentSetting) {
            SimpleDropdownField(
                label = s.departmentSetting,
                selectedText = department.ifBlank { s.departmentSetting },
                options = Departments.list,
                expanded = departmentExpanded,
                onExpandedChange = { departmentExpanded = it },
                onOptionSelected = { onDepartmentChange(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.totalCreditsRequiredSetting) {
            OutlinedTextField(
                value = creditsText,
                onValueChange = { text ->
                    creditsText = text
                    text.toDoubleOrNull()?.let { onTotalCreditsRequiredChange(it) }
                },
                placeholder = { Text(s.totalCreditsRequiredHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.remindersSetting) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(s.semesterReminderLabel, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(
                    checked = semesterReminderEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                semesterReminderEnabled = true
                                ReminderManager.setSemesterReminderEnabled(context, true)
                            }
                        } else {
                            semesterReminderEnabled = false
                            ReminderManager.setSemesterReminderEnabled(context, false)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider()
            Spacer(modifier = Modifier.height(14.dp))

            Text(s.customRemindersLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            customReminders.forEach { reminder ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(reminder.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                .format(Date(reminder.timestamp)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        ReminderManager.cancelCustomReminder(context, reminder)
                        customReminders = ReminderManager.loadReminders(context)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val cal = Calendar.getInstance()
                                    cal.set(year, month, day, hour, minute, 0)
                                    pendingReminderTimestamp = cal.timeInMillis
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(s.addReminderButton)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.themeModeSetting) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                    label = { Text(s.themeModeSystem, fontSize = 12.sp) }
                )
                FilterChip(
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                    label = { Text(s.themeModeLight, fontSize = 12.sp) }
                )
                FilterChip(
                    selected = themeMode == ThemeMode.DARK,
                    onClick = { onThemeModeChange(ThemeMode.DARK) },
                    label = { Text(s.themeModeDark, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.biometricLockSetting) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(s.biometricLockLabel, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = biometricLockEnabled, onCheckedChange = onBiometricLockChange)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.gradingScaleSetting) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = gradingScale == "4.00",
                    onClick = { onGradingScaleChange("4.00") },
                    label = { Text(s.gradingScale4, fontSize = 12.sp) }
                )
                FilterChip(
                    selected = gradingScale == "5.00",
                    onClick = { onGradingScaleChange("5.00") },
                    label = { Text(s.gradingScale5, fontSize = 12.sp) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(s.gradingScaleNote, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && !currentUser.isEmailVerified) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSection(title = s.emailVerificationSetting) {
                Text(s.emailNotVerifiedMessage, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        currentUser.sendEmailVerification()
                            .addOnSuccessListener {
                                Toast.makeText(context, s.verificationEmailSentMessage, Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, it.localizedMessage ?: "Failed", Toast.LENGTH_LONG).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(s.resendVerificationButton) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.languageSetting) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = language == AppLanguage.ENGLISH,
                    onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                    label = { Text("English") }
                )
                FilterChip(
                    selected = language == AppLanguage.BANGLA,
                    onClick = { onLanguageChange(AppLanguage.BANGLA) },
                    label = { Text("বাংলা") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.changePassword) {
            OutlinedButton(
                onClick = {
                    val email = FirebaseAuth.getInstance().currentUser?.email
                    if (email != null) {
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                            .addOnSuccessListener {
                                Toast.makeText(context, s.changePasswordSent, Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, it.localizedMessage ?: "Failed", Toast.LENGTH_LONG).show()
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(s.changePassword) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.clearData) {
            OutlinedButton(
                onClick = { showClearConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text(s.clearData) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = s.deleteAccountSetting) {
            OutlinedButton(
                onClick = { showDeleteAccountConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text(s.deleteAccountButton) }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "${s.appVersion}: 1.0.0",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(s.clearData) },
            text = { Text(s.clearDataConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearData()
                    Toast.makeText(context, s.dataClearedMessage, Toast.LENGTH_LONG).show()
                }) { Text(s.yes) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(s.no) }
            }
        )
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text(s.deleteAccountButton) },
            text = { Text(s.deleteAccountConfirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountConfirm = false
                    val user = FirebaseAuth.getInstance().currentUser
                    user?.delete()
                        ?.addOnSuccessListener {
                            onClearData()
                            onAccountDeleted()
                        }
                        ?.addOnFailureListener {
                            Toast.makeText(context, s.deleteAccountFailedMessage, Toast.LENGTH_LONG).show()
                        }
                }) { Text(s.yes) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) { Text(s.no) }
            }
        )
    }

    pendingReminderTimestamp?.let { timestamp ->
        AlertDialog(
            onDismissRequest = { pendingReminderTimestamp = null },
            title = { Text(s.addReminderButton) },
            text = {
                OutlinedTextField(
                    value = newReminderTitle,
                    onValueChange = { newReminderTitle = it },
                    label = { Text(s.reminderTitleHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newReminderTitle.isNotBlank()) {
                        ReminderManager.addCustomReminder(context, newReminderTitle.trim(), s.reminderDefaultMessage, timestamp)
                        customReminders = ReminderManager.loadReminders(context)
                        newReminderTitle = ""
                        pendingReminderTimestamp = null
                    }
                }) { Text(s.save) }
            },
            dismissButton = {
                TextButton(onClick = { pendingReminderTimestamp = null; newReminderTitle = "" }) { Text(s.cancel) }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}
