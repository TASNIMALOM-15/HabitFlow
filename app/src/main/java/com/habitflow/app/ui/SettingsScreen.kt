package com.habitflow.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.habitflow.app.AppVM
import com.habitflow.app.notify.Scheduler
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppVM, pad: PaddingValues) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var confirmImport by remember { mutableStateOf<Boolean?>(null) }   // value = isCsv
    var showBackups by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var notifOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> vm.exportTo(u, false) } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.exportTo(u, true) } }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importFrom(u, false) } }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importFrom(u, true) } }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.setBackground(u) } }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        SCard("Appearance") {
            Label("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, l -> Chip(l, p.theme == i, { p.setTheme(i) }, Modifier.weight(1f)) }
            }
            Label("Color palette")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Palettes.forEachIndexed { i, pal ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(pal.primary)
                            .then(if (p.palette == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { p.setPalette(i) }
                    )
                }
            }
            Label("App background")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinePill("Choose image", { pickBg.launch("image/*") })
                if (p.bgPath.isNotBlank()) OutlinePill("Remove", { vm.clearBackground() })
            }
        }

        SCard("Preferences") {
            Label("Week starts on")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Sat" to 6, "Sun" to 7, "Mon" to 1).forEach { (l, v) -> Chip(l, p.weekStart == v, { p.setWeekStart(v) }, Modifier.weight(1f)) }
            }
            Label("Time format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("12-hour", !p.time24, { p.setTime24(false) }, Modifier.weight(1f))
                Chip("24-hour", p.time24, { p.setTime24(true) }, Modifier.weight(1f))
            }
            Label("Date format")
            val today = com.habitflow.app.data.Engine.today()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..2).forEach { i ->
                    Chip(p.fmtDate(today, i), p.dateFmt == i, { p.setDateFmt(i) })
                }
            }
        }

        SCard("Notifications") {
            Text(if (notifOn) "Notifications are enabled ✅" else "Notifications are turned off ⚠️", color = muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!notifOn) OutlinePill("Allow notifications", {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                })
                OutlinePill("Test", { Scheduler.test(ctx) })
                OutlinePill("Refresh reminders", { vm.reschedule() })
                if (Build.VERSION.SDK_INT >= 31) OutlinePill("Exact alarms", {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }
                })
                OutlinePill("Battery", {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                })
            }
            Text("Tip: allow exact alarms and disable battery optimisation so reminders arrive on time.", color = muted, fontSize = 12.sp)
        }

        SCard("Data & Backup") {
            Text("All data stays on this device. A local backup is also created automatically once a day.", color = muted, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinePill("Export JSON", { exportJson.launch("habitflow_backup.json") })
                OutlinePill("Export CSV", { exportCsv.launch("habitflow_entries.csv") })
                OutlinePill("Import JSON", { confirmImport = false })
                OutlinePill("Import CSV", { confirmImport = true })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Backup now", { vm.backupNow() }, filled = true, color = MaterialTheme.colorScheme.primary)
                OutlinePill("Restore backup", { showBackups = true })
            }
        }

        SCard("About") {
            Text("HabitFlow 1.0 · offline · no ads · no subscription", color = muted, fontSize = 13.sp)
        }
    }

    confirmImport?.let { csv ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text(if (csv) "Import CSV" else "Import JSON") },
            text = { Text(if (csv) "Entries are merged into habits with the same name." else "This replaces ALL current data with the backup file.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    if (csv) importCsv.launch(arrayOf("*/*")) else importJson.launch(arrayOf("*/*"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancel") } }
        )
    }

    if (showBackups) {
        val files = remember { vm.repo.listBackups() }
        AlertDialog(
            onDismissRequest = { showBackups = false },
            title = { Text("Local backups") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (files.isEmpty()) Text("No backups yet.")
                    files.forEach { f ->
                        Text(
                            f.name.removePrefix("backup_").removeSuffix(".json").replace('_', ' '),
                            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant).clickable { restoreFile = f; showBackups = false }.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBackups = false }) { Text("Close") } }
        )
    }

    restoreFile?.let { f ->
        AlertDialog(
            onDismissRequest = { restoreFile = null },
            title = { Text("Restore this backup?") },
            text = { Text("All current data will be replaced.") },
            confirmButton = { TextButton(onClick = { vm.restoreLocal(f); restoreFile = null }) { Text("Restore", color = Danger) } },
            dismissButton = { TextButton(onClick = { restoreFile = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SCard(title: String, content: @Composable () -> Unit) {
    HCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
