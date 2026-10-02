package com.haptikit.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haptikit.app.data.HaptiRepository
import com.haptikit.app.data.PatternEntity
import com.haptikit.app.service.HaptiNotificationListenerService
import com.haptikit.app.vibration.VibrationEngine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternListScreen(
    repository: HaptiRepository,
    onCreateNew: () -> Unit,
    onEditPattern: (Long) -> Unit,
    onGoToAssignments: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val patterns by repository.observePatterns().collectAsState(initial = emptyList())
    val assignments by repository.observeAssignments().collectAsState(initial = emptyList())
    val listening by HaptiNotificationListenerService.connected.collectAsState()
    val engine = remember { VibrationEngine(context) }
    var menuOpen by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<PatternEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = repository.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("empty")
                val (p, a) = repository.importJson(json)
                "Imported $p pattern${if (p == 1) "" else "s"} and $a assignment${if (a == 1) "" else "s"}"
            }.getOrElse { "That file isn't a HaptiKit backup" }
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("HaptiKit") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export backup") }, leadingIcon = { Icon(Icons.Default.Upload, null) },
                            onClick = { menuOpen = false; exportLauncher.launch("haptikit_backup.json") }
                        )
                        DropdownMenuItem(
                            text = { Text("Import backup") }, leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateNew, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New pattern") })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    onClick = onGoToAssignments,
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (listening) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (listening) Icons.Default.Vibration else Icons.Default.NotificationsOff, null)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Who gets which vibration", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (listening) "${assignments.size} assignment${if (assignments.size == 1) "" else "s"} · active"
                                else "Setup needed — notification access is off",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }

            item {
                Text("Patterns", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }

            items(patterns, key = { it.id }) { pattern ->
                val usedBy = assignments.count { it.patternId == pattern.id }
                PatternCard(
                    pattern = pattern,
                    usedBy = usedBy,
                    onPlay = { engine.play(pattern) },
                    onEdit = { onEditPattern(pattern.id) },
                    onDuplicate = { scope.launch { repository.duplicatePattern(pattern) } },
                    onDelete = { deleteCandidate = pattern }
                )
            }
        }
    }

    deleteCandidate?.let { p ->
        val usedBy = assignments.count { it.patternId == p.id }
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete \"${p.name}\"?") },
            text = { Text(if (usedBy > 0) "It's assigned to $usedBy contact/app${if (usedBy == 1) "" else "s"} — those go back to the default vibration." else "This can't be undone.") },
            confirmButton = { TextButton(onClick = { scope.launch { repository.deletePattern(p) }; deleteCandidate = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PatternCard(
    pattern: PatternEntity,
    usedBy: Int,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onEdit, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pattern.name, style = MaterialTheme.typography.titleMedium)
                    if (pattern.isBuiltIn) {
                        Spacer(Modifier.width(8.dp))
                        Text("built-in", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(6.dp))
                PatternVisual(
                    pattern.timings, pattern.amplitudes,
                    color = MaterialTheme.colorScheme.primary,
                    track = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxWidth().height(22.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatDuration(pattern.timings.sum()) + if (usedBy > 0) " · used by $usedBy" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FilledTonalIconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, "Play") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** "40 ms" for tiny patterns (one decimal of seconds would round them to 0.0 s), else "1.2 s". */
internal fun formatDuration(ms: Long): String = if (ms < 1000) "$ms ms" else "%.1f s".format(ms / 1000f)
