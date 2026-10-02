package com.haptikit.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haptikit.app.data.HaptiRepository
import com.haptikit.app.data.PatternEntity
import com.haptikit.app.vibration.VibrationEngine
import kotlinx.coroutines.launch

/** Longest silence recorded between beats — a long pause while thinking
 *  shouldn't turn into a 5-second gap in the pattern. */
private const val MAX_GAP_MS = 1_200L
private const val MAX_BEAT_MS = 2_000L
private const val MIN_BEAT_MS = 30L

/**
 * Record a pattern by pressing the pad: each press is a beat that lasts
 * as long as you hold it (with the device buzzing live), and the time
 * between presses becomes silence. Strength applies to the next beats.
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
    var strength by remember { mutableFloatStateOf(220f) }
    var lastReleaseMs by remember { mutableStateOf<Long?>(null) }
    var pressing by remember { mutableStateOf(false) }
    // Index boundaries of each recorded beat, for "undo last beat".
    val beatStarts = remember { mutableStateListOf<Int>() }

    LaunchedEffect(patternId) {
        if (patternId >= 0) {
            repository.getPattern(patternId)?.let { existing ->
                name = existing.name
                timings.clear(); timings.addAll(existing.timings)
                amplitudes.clear(); amplitudes.addAll(existing.amplitudes)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { engine.cancel() } }

    fun onPress(): Long {
        pressing = true
        engine.startHold(strength.toInt())
        return System.currentTimeMillis()
    }

    fun onRelease(pressedAt: Long) {
        pressing = false
        engine.cancel()
        val now = System.currentTimeMillis()
        beatStarts.add(timings.size)
        lastReleaseMs?.let { last ->
            val gap = (pressedAt - last).coerceIn(0, MAX_GAP_MS)
            if (gap > 0) { timings.add(gap); amplitudes.add(0) }
        }
        timings.add((now - pressedAt).coerceIn(MIN_BEAT_MS, MAX_BEAT_MS))
        amplitudes.add(strength.toInt())
        lastReleaseMs = now
    }

    fun undoBeat() {
        val start = beatStarts.removeLastOrNull() ?: run {
            // Editing a saved pattern: remove its last on+off pair.
            if (timings.isNotEmpty()) { timings.removeAt(timings.lastIndex); amplitudes.removeAt(amplitudes.lastIndex) }
            if (amplitudes.lastOrNull() == 0) { timings.removeAt(timings.lastIndex); amplitudes.removeAt(amplitudes.lastIndex) }
            return
        }
        while (timings.size > start) { timings.removeAt(timings.lastIndex); amplitudes.removeAt(amplitudes.lastIndex) }
        if (timings.isEmpty()) lastReleaseMs = null
    }

    fun clear() {
        timings.clear(); amplitudes.clear(); beatStarts.clear(); lastReleaseMs = null
    }

    val beats = amplitudes.count { it > 0 }
    val totalMs = timings.sum()
    val padScale by animateFloatAsState(if (pressing) 0.92f else 1f, label = "pad")

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (patternId >= 0) "Edit pattern" else "New pattern") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                TextButton(
                    enabled = name.isNotBlank() && beats > 0,
                    onClick = {
                        scope.launch {
                            repository.savePattern(
                                PatternEntity(
                                    id = if (patternId >= 0) patternId else 0,
                                    name = name.trim(),
                                    timings = timings.toList(),
                                    amplitudes = amplitudes.toList()
                                )
                            )
                            onDone()
                        }
                    }
                ) { Text("Save") }
            }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Pattern name") },
                placeholder = { Text("e.g. Mom, Work chat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (beats == 0) "No beats yet" else "$beats beat${if (beats == 1) "" else "s"} · ${formatDuration(totalMs)}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(enabled = timings.isNotEmpty(), onClick = { undoBeat() }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo last beat") }
                        IconButton(enabled = timings.isNotEmpty(), onClick = { clear() }) { Icon(Icons.Default.DeleteOutline, "Clear") }
                        FilledIconButton(enabled = beats > 0, onClick = { engine.play(timings.toList(), amplitudes.toList()) }) {
                            Icon(Icons.Default.PlayArrow, "Preview")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PatternVisual(
                        timings, amplitudes,
                        color = MaterialTheme.colorScheme.primary,
                        track = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(200.dp)
                        .scale(padScale)
                        .clip(CircleShape)
                        .background(if (pressing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val pressedAt = onPress()
                                waitForUpOrCancellation()
                                onRelease(pressedAt)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TouchApp, null, Modifier.size(48.dp),
                            tint = if (pressing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            if (pressing) "Recording…" else "Tap or hold",
                            textAlign = TextAlign.Center,
                            color = if (pressing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column {
                Text("Strength", style = MaterialTheme.typography.labelLarge)
                Slider(value = strength, onValueChange = { strength = it }, valueRange = 40f..255f)
                if (!engine.hasAmplitudeControl) {
                    Text(
                        "This phone's motor has one fixed strength, so every beat buzzes the same — rhythm still works.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                "Short taps make quick pulses; hold for a long buzz. Pauses between presses are kept (up to 1.2 s).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
