package com.haptikit.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haptikit.app.data.HaptiRepository
import com.haptikit.app.data.PatternEntity
import com.haptikit.app.data.TargetType
import kotlinx.coroutines.launch

private data class Target(val id: String, val label: String, val type: TargetType)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentScreen(repository: HaptiRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val patterns by repository.observePatterns().collectAsState(initial = emptyList())
    val assignments by repository.observeAssignments().collectAsState(initial = emptyList())

    var tab by remember { mutableStateOf(0) } // 0 = contacts, 1 = apps
    var hasContactsPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasContactsPermission = granted
    }

    var pickerTarget by remember { mutableStateOf<Target?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Assignments") })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Contacts") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Apps") })
            }

            // Notification-listener access is required for per-app/contact
            // overrides to actually fire — deep-link the user to that settings screen.
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier.padding(16.dp)
            ) { Text("Grant notification access (required)") }

            if (tab == 0) {
                if (!hasContactsPermission) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.padding(16.dp)
                    ) { Text("Grant contacts permission") }
                } else {
                    val contacts = remember { loadContacts(context) }
                    TargetList(
                        targets = contacts,
                        assignments = assignments,
                        patterns = patterns,
                        onPick = { pickerTarget = it }
                    )
                }
            } else {
                val apps = remember { loadInstalledApps(context) }
                TargetList(
                    targets = apps,
                    assignments = assignments,
                    patterns = patterns,
                    onPick = { pickerTarget = it }
                )
            }
        }
    }

    pickerTarget?.let { target ->
        PatternPickerDialog(
            patterns = patterns,
            onDismiss = { pickerTarget = null },
            onSelect = { pattern ->
                scope.launch { repository.assign(target.id, target.type, target.label, pattern.id) }
                pickerTarget = null
            },
            onClear = {
                scope.launch { repository.clearAssignment(target.id) }
                pickerTarget = null
            }
        )
    }
}

@Composable
private fun TargetList(
    targets: List<Target>,
    assignments: List<com.haptikit.app.data.AssignmentEntity>,
    patterns: List<PatternEntity>,
    onPick: (Target) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(targets, key = { it.id }) { target ->
            val assignedPatternName = assignments
                .firstOrNull { it.targetId == target.id }
                ?.let { a -> patterns.firstOrNull { it.id == a.patternId }?.name }

            ListItem(
                headlineContent = { Text(target.label) },
                supportingContent = { Text(assignedPatternName ?: "Default vibration") },
                modifier = Modifier.clickable { onPick(target) }
            )
            Divider()
        }
    }
}

@Composable
private fun PatternPickerDialog(
    patterns: List<PatternEntity>,
    onDismiss: () -> Unit,
    onSelect: (PatternEntity) -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a pattern") },
        text = {
            LazyColumn {
                items(patterns, key = { it.id }) { pattern ->
                    ListItem(
                        headlineContent = { Text(pattern.name) },
                        modifier = Modifier.clickable { onSelect(pattern) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClear) { Text("Use default") } }
    )
}

private fun loadContacts(context: android.content.Context): List<Target> {
    val results = mutableListOf<Target>()
    val cursor = context.contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
        null, null,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
    )
    cursor?.use {
        val lookupIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
        val nameIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        while (it.moveToNext()) {
            results.add(Target(it.getString(lookupIdx), it.getString(nameIdx) ?: "Unknown", TargetType.CONTACT))
        }
    }
    return results
}

private fun loadInstalledApps(context: android.content.Context): List<Target> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return pm.queryIntentActivities(intent, 0).map { info ->
        Target(
            id = info.activityInfo.packageName,
            label = info.loadLabel(pm).toString(),
            type = TargetType.APP
        )
    }.distinctBy { it.id }.sortedBy { it.label }
}
