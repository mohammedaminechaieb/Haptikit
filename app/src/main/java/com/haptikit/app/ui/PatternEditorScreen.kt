package com.haptikit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haptikit.app.data.HaptiRepository
import com.haptikit.app.data.PatternEntity
import com.haptikit.app.vibration.VibrationEngine
import kotlinx.coroutines.launch

/**
 * Lets the user "record" a pattern by tapping a button — each tap adds a
 * vibration segment, each pause between taps adds a silent segment.
 * This is the simplest possible input method for v0.1; a drag-based
 * waveform editor is a natural post-launch upgrade.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternEditorScreen(
    repository: HaptiRepository,
    patternId: Long,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { VibrationEngine(context) }

    var name by remember { mutableStateOf("") }
    val timings = remember { mutableStateListOf<Long>() }
    val amplitudes = remember { mutableStateListOf<Int>() }
    var lastEventMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(patternId) {
        if (patternId >= 0) {
            repository.getPattern(patternId)?.let { existing ->
                name = existing.name
                timings.clear(); timings.addAll(existing.timings)
                amplitudes.clear(); amplitudes.addAll(existing.amplitudes)
            }
        }
    }

    fun recordTap() {
        val now = System.currentTimeMillis()
        val gap = lastEventMs?.let { now - it } ?: 0L
        lastEventMs = now
        if (gap > 0) { timings.add(gap); amplitudes.add(0) }       // silence before this tap
        timings.add(60L); amplitudes.add(220)                       // the tap itself
        engine.playSingleTap()
    }

    fun clear() {
        timings.clear(); amplitudes.clear(); lastEventMs = null
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (patternId >= 0) "Edit Pattern" else "New Pattern") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Pattern name") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Timeline (${timings.size} segments)")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                items(amplitudes.size) { i ->
                    val on = amplitudes[i] > 0
                    Box(
                        Modifier
                            .height(if (on) 48.dp else 12.dp)
                            .width((timings[i].coerceIn(10, 300) / 5).toInt().dp)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { recordTap() }) { Text("Tap to add beat") }
                OutlinedButton(onClick = { clear() }) { Text("Clear") }
            }

            Button(
                onClick = {
                    if (timings.isNotEmpty()) engine.play(
                        PatternEntity(id = patternId.coerceAtLeast(0), name = name, timings = timings, amplitudes = amplitudes)
                    )
                },
                enabled = timings.isNotEmpty()
            ) { Text("Preview full pattern") }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDone) { Text("Cancel") }
                Button(
                    enabled = name.isNotBlank() && timings.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            repository.savePattern(
                                PatternEntity(
                                    id = if (patternId >= 0) patternId else 0,
                                    name = name,
                                    timings = timings.toList(),
                                    amplitudes = amplitudes.toList()
                                )
                            )
                            onDone()
                        }
                    }
                ) { Text("Save") }
            }
        }
    }
}
