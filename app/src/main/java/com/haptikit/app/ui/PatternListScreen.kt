package com.haptikit.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haptikit.app.data.HaptiRepository
import com.haptikit.app.data.PatternEntity
import com.haptikit.app.vibration.VibrationEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternListScreen(
    repository: HaptiRepository,
    onCreateNew: () -> Unit,
    onEditPattern: (Long) -> Unit,
    onGoToAssignments: () -> Unit
) {
    val context = LocalContext.current
    val patterns by repository.observePatterns().collectAsState(initial = emptyList())
    val engine = remember { VibrationEngine(context) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("HaptiKit") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNew) { Icon(Icons.Default.Add, contentDescription = "New pattern") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Button(
                onClick = onGoToAssignments,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("Contact & App Assignments") }

            LazyColumn(Modifier.fillMaxSize()) {
                items(patterns, key = { it.id }) { pattern: PatternEntity ->
                    ListItem(
                        headlineContent = { Text(pattern.name) },
                        supportingContent = { Text("${pattern.timings.size} segments${if (pattern.isBuiltIn) " · built-in" else ""}") },
                        trailingContent = {
                            IconButton(onClick = { engine.play(pattern) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview")
                            }
                        },
                        modifier = Modifier.clickable { onEditPattern(pattern.id) }
                    )
                    Divider()
                }
            }
        }
    }
}
