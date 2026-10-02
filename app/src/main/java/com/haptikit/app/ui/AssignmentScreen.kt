package com.haptikit.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.haptikit.app.data.*
import com.haptikit.app.service.HaptiNotificationListenerService
import com.haptikit.app.vibration.VibrationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Target(val id: String, val label: String, val type: TargetType, val icon: Drawable? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentScreen(repository: HaptiRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { HaptiPrefs(context) }
    val engine = remember { VibrationEngine(context) }
    val patterns by repository.observePatterns().collectAsState(initial = emptyList())
    val assignments by repository.observeAssignments().collectAsState(initial = emptyList())
    val listening by HaptiNotificationListenerService.connected.collectAsState()

    var hasAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    var hasContactsPermission by remember { mutableStateOf(hasContactsPermission(context)) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var respectSilent by remember { mutableStateOf(prefs.respectSilent) }

    // Re-check permissions whenever we come back from a settings screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                hasAccess = hasNotificationAccess(context)
                hasContactsPermission = hasContactsPermission(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasContactsPermission = granted
    }

    var tab by remember { mutableIntStateOf(0) } // 0 = contacts, 1 = apps
    var query by remember { mutableStateOf("") }
    var onlyAssigned by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<Target?>(null) }

    var contacts by remember { mutableStateOf<List<Target>?>(null) }
    var apps by remember { mutableStateOf<List<Target>?>(null) }
    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission) contacts = withContext(Dispatchers.IO) { loadContacts(context) }
    }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadInstalledApps(context) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Assignments") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ---- Status / setup ----
            if (!hasAccess) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Turn on notification access", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "HaptiKit needs to see when notifications arrive so it can play the right pattern. Find HaptiKit in the list and switch it on.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Open settings") }
                    }
                }
            } else {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Custom vibrations", style = MaterialTheme.typography.titleSmall)
                        Text(if (enabled && listening) "Active" else if (enabled) "Waiting for Android to start the listener…" else "Paused", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it; prefs.enabled = it })
                }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Stay quiet when the phone is on silent", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = respectSilent, onCheckedChange = { respectSilent = it; prefs.respectSilent = it })
                }
            }

            TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Contacts") }, icon = { Icon(Icons.Default.Person, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Apps") }, icon = { Icon(Icons.Default.Apps, null) })
            }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = onlyAssigned, onClick = { onlyAssigned = !onlyAssigned }, label = { Text("Assigned") })
            }

            val source = if (tab == 0) contacts else apps
            when {
                tab == 0 && !hasContactsPermission -> CenterMessage(
                    "Allow contacts access to give people their own vibration.",
                    "Allow contacts"
                ) { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                source == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val byTarget = assignments.associateBy { it.targetId }
                    val filtered = source.filter {
                        (query.isBlank() || it.label.contains(query, ignoreCase = true)) && (!onlyAssigned || it.id in byTarget)
                    }.sortedByDescending { it.id in byTarget }
                    if (filtered.isEmpty()) {
                        CenterMessage(if (onlyAssigned) "Nothing assigned yet — tap a contact or app to pick a pattern." else "No matches.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(filtered, key = { it.type.name + it.id }) { target ->
                                val patternName = byTarget[target.id]?.let { a -> patterns.firstOrNull { it.id == a.patternId }?.name }
                                TargetRow(target, patternName) { pickerTarget = target }
                            }
                        }
                    }
                }
            }
        }
    }

    pickerTarget?.let { target ->
        val currentId = assignments.firstOrNull { it.targetId == target.id }?.patternId
        AlertDialog(
            onDismissRequest = { pickerTarget = null },
            title = { Text(target.label) },
            text = {
                LazyColumn {
                    items(patterns, key = { it.id }) { pattern ->
                        ListItem(
                            leadingContent = { RadioButton(selected = pattern.id == currentId, onClick = null) },
                            headlineContent = { Text(pattern.name) },
                            trailingContent = {
                                IconButton(onClick = { engine.play(pattern) }) { Icon(Icons.Default.PlayArrow, "Preview") }
                            },
                            modifier = Modifier.clickable {
                                scope.launch { repository.assign(target.id, target.type, target.label, pattern.id) }
                                pickerTarget = null
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerTarget = null }) { Text("Close") } },
            dismissButton = {
                if (currentId != null) {
                    TextButton(onClick = {
                        scope.launch { repository.clearAssignment(target.id) }
                        pickerTarget = null
                    }) { Text("Use default") }
                }
            }
        )
    }
}

@Composable
private fun TargetRow(target: Target, patternName: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            val bitmap = remember(target.id) { target.icon?.toBitmap(96, 96)?.asImageBitmap() }
            if (bitmap != null) {
                Image(bitmap, null, Modifier.size(40.dp))
            } else {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(target.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium) }
            }
        },
        headlineContent = { Text(target.label) },
        supportingContent = {
            if (patternName != null) {
                Text("♪ $patternName", color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Default vibration")
            }
        },
        trailingContent = { Icon(Icons.Default.Vibration, null, tint = if (patternName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) }
    )
}

@Composable
private fun CenterMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

private fun hasContactsPermission(context: Context) =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

private fun hasNotificationAccess(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any { it.startsWith("${context.packageName}/") }
}

private fun loadContacts(context: Context): List<Target> {
    val results = mutableListOf<Target>()
    context.contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
        null, null,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE NOCASE ASC"
    )?.use {
        val lookupIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
        val nameIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        while (it.moveToNext()) {
            val key = it.getString(lookupIdx) ?: continue
            results.add(Target(key, it.getString(nameIdx) ?: "Unknown", TargetType.CONTACT))
        }
    }
    return results.distinctBy { it.id }
}

private fun loadInstalledApps(context: Context): List<Target> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info -> Target(info.activityInfo.packageName, info.loadLabel(pm).toString(), TargetType.APP, info.loadIcon(pm)) }
        .distinctBy { it.id }
        .sortedBy { it.label.lowercase() }
}
