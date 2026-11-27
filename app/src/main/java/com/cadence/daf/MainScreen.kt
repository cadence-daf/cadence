package com.cadence.daf

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.background
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

@Composable
fun MainScreen(viewModel: MainViewModel, openSettings: () -> Unit, openProgress: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val goalReachedText = stringResource(R.string.snackbar_goal_reached, state.practice.todayMin)
    val todayText = stringResource(R.string.snackbar_today, state.practice.todayMin)
    val message = if (goalReached(state.practice.todayMin, state.settings.dailyGoalMin)) goalReachedText else todayText
    val latestMessage by rememberUpdatedState(message)
    LaunchedEffect(Unit) {
        viewModel.sessionSummary.collect { snackbar.showSnackbar(latestMessage, duration = SnackbarDuration.Short) }
    }
    ScreenScaffold(snackbarHost = { SnackbarHost(snackbar) }) {
        Row(
            modifier = Modifier.fillMaxWidth().staggerIn(0),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppLogo()
                Wordmark()
            }
            TextButton(onClick = openSettings) {
                Text(stringResource(R.string.settings), color = MaterialTheme.colorScheme.primary)
            }
        }
        Box(Modifier.fillMaxWidth().staggerIn(1)) { NightStage(viewModel) }
        Box(Modifier.fillMaxWidth().staggerIn(2)) { HeadsetNotices(state.headset, viewModel, state.error) }
        Box(Modifier.fillMaxWidth().staggerIn(3)) { ControlPanel(state.settings, state.headset.isBluetooth, viewModel) }
        PracticeRow(state.practice, state.settings.dailyGoalMin, openProgress, Modifier.staggerIn(4))
    }
}

/** The launcher icon at 32 dp. Decorative: the app name next to it is the label. */
@Composable
private fun AppLogo() {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(60.dp))
    }
}

/** "Cadence" with the tagline set small, spaced and in the brand colour beneath it. */
@Composable
private fun Wordmark() {
    Column {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 26.sp, letterSpacing = 0.3.sp)
        )
        Text(
            stringResource(R.string.tagline).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.8.sp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Ring (goal on), today's text and the week count; the whole row opens Progress. */
@Composable
private fun PracticeRow(stats: PracticeStats, goal: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val first = when {
        goal == 0 -> stringResource(R.string.practice_today, stats.todayMin)
        goalReached(stats.todayMin, goal) -> stringResource(R.string.practice_goal_reached, stats.todayMin)
        else -> stringResource(R.string.practice_today_goal, stats.todayMin, goal)
    }
    val second = stringResource(R.string.practice_days, stats.weekDays)
    val label = stringResource(R.string.practice_row_label, first, second)
    val fill = rememberGrow(minOf(1f, stats.todayMin.toFloat() / goal.coerceAtLeast(1)), animateOpen = !introPlayed)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (goal > 0) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = Stroke(3.dp.toPx())
                val inset = stroke.width / 2
                val arc = Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(colors.surfaceVariant, 0f, 360f, false, Offset(inset, inset), arc, style = stroke)
                drawArc(colors.primary, -90f, 360f * fill, false, Offset(inset, inset), arc, style = stroke)
            }
        }
        Column {
            Text(first, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(second, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun rememberStartStopAction(viewModel: MainViewModel): () -> Unit {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.start() }
    val startWithNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Android stops showing the dialog after two refusals, so say where to allow it.
        if (granted) startWithNotifications() else Toast.makeText(context, R.string.mic_denied, Toast.LENGTH_LONG).show()
    }
    val onStartStop: () -> Unit = {
        when {
            state.running -> viewModel.stop()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> startWithNotifications()
        }
    }
    return onStartStop
}

/** Triangle for Start, square for Stop. Decorative: the label carries the meaning. */
@Composable
private fun StartStopShapeIcon(running: Boolean) {
    val tint = LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        if (running) {
            drawRect(tint, size = size)
        } else {
            drawPath(
                Path().apply {
                    moveTo(size.width * 0.15f, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(size.width * 0.15f, size.height)
                    close()
                },
                tint
            )
        }
    }
}

private enum class GateStatus { OFF, OPEN, CLOSED }

/**
 * Night Focus centrepiece: the delay in a ring whose halo follows the input level,
 * the gate status, and the Start/Stop circle.
 */
@Composable
private fun NightStage(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val onStartStop = rememberStartStopAction(viewModel)
    val running = state.running
    val telemetry by viewModel.telemetry.collectAsState()
    val colors = MaterialTheme.colorScheme
    val shortcutStart by startRequested.collectAsState()
    val canShortcutStart = state.headset.canStart && state.settings.disclaimerAccepted
    LaunchedEffect(shortcutStart, canShortcutStart) {
        if (shortcutStart && canShortcutStart) {
            startRequested.value = false
            if (!running) onStartStop()
        }
    }
    val level by animateFloatAsState(
        if (running) levelFraction(telemetry.inputDb) else 0f,
        // Same length as the 10 Hz telemetry interval, so the halo glides between samples.
        motionTween(100, LinearEasing),
        label = "level"
    )
    val levelName = stringResource(R.string.input_level)
    val levelDb = stringResource(R.string.input_level_db, (telemetry.inputDb / 5).roundToInt() * 5)
    val gate = when {
        !state.settings.gateEnabled -> GateStatus.OFF
        telemetry.gateOpen -> GateStatus.OPEN
        else -> GateStatus.CLOSED
    }
    val statusRes = when {
        running -> when (gate) {
            GateStatus.OFF -> R.string.gate_off_live
            GateStatus.OPEN -> R.string.playing_voice
            GateStatus.CLOSED -> R.string.muted_waiting
        }
        state.headset.canStart -> R.string.hero_idle
        else -> R.string.plug_in_to_start
    }
    val container by animateColorAsState(
        if (running) colors.tertiary else colors.primary, motionTween(200), label = "container"
    )
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.96f else 1f, motionSpring(1f, 500f), label = "press")
    val timerAlpha by animateFloatAsState(if (running) 1f else 0f, motionTween(200), label = "timer")
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier
                .size(300.dp)
                .drawBehind {
                    val radius = size.minDimension / 2
                    val ringRadius = radius - 18.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.8f to colors.primary.copy(alpha = 0.10f + 0.30f * level),
                                1f to Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius
                    )
                    drawCircle(colors.primary.copy(alpha = 0.05f + 0.05f * level), radius = ringRadius)
                    drawCircle(colors.primary.copy(alpha = 0.6f), radius = ringRadius, style = Stroke(2.dp.toPx()))
                }
                .semantics {
                    contentDescription = levelName
                    stateDescription = levelDb
                },
            contentAlignment = Alignment.Center
        ) {
            // 44 dp each side keeps the longest status line inside the ring (it wraps to two lines).
            Column(Modifier.padding(horizontal = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.set_delay_title).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.delay_chip, state.settings.delayMs),
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    stringResource(statusRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // Gate flips on every phrase; announcing it would talk over practice.
                    modifier = Modifier.semantics { if (!running) liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        Button(
            onClick = {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.VIRTUAL_KEY
                )
                onStartStop()
            },
            enabled = running || state.headset.canStart,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = container),
            interactionSource = source,
            modifier = Modifier.size(76.dp).graphicsLayer { scaleX = press; scaleY = press }
        ) {
            val iconFade = motionTween<Float>(180)
            AnimatedContent(
                running,
                transitionSpec = {
                    (fadeIn(iconFade) + scaleIn(iconFade, 0.8f)) togetherWith (fadeOut(iconFade) + scaleOut(iconFade, 0.8f))
                },
                label = "icon"
            ) { StartStopShapeIcon(it) }
        }
        Text(stringResource(if (running) R.string.stop else R.string.start), style = MaterialTheme.typography.labelLarge)
        // Always laid out so Start does not push the rest of the screen down.
        Text(
            formatElapsed(telemetry.elapsedSec),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .graphicsLayer { alpha = timerAlpha }
                .then(if (running) Modifier else Modifier.clearAndSetSemantics {})
        )
    }
}

/** Only the notices that apply; "plug in" already shows in the ring's status line. */
@Composable
private fun HeadsetNotices(headset: HeadsetState, viewModel: MainViewModel, error: String?) {
    val quietMic = viewModel.telemetry.collectAsState().value.quietMic
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (headset.canStart && headset.isBluetooth) {
            Text(stringResource(R.string.bluetooth_delay), style = MaterialTheme.typography.bodySmall)
        }
        if (headset.canStart && !headset.hasHeadsetMic) {
            Text(stringResource(R.string.no_headset_mic), style = MaterialTheme.typography.bodySmall)
        }
        if (quietMic) {
            Text(stringResource(R.string.quiet_mic), style = MaterialTheme.typography.bodySmall)
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The "Actual delay" line. Reads the live latency itself, so only this line follows it. */
@Composable
private fun ActualDelayLine(settings: Settings, isBluetooth: Boolean, viewModel: MainViewModel) {
    val latencyMs = viewModel.telemetry.collectAsState().value.latencyMs
    val label = when (val actual = actualDelay(settings.delayMs, latencyMs, isBluetooth, settings.pitchSemitones)) {
        is ActualDelay.Measured -> stringResource(R.string.actual_delay, actual.ms)
        ActualDelay.Bluetooth -> stringResource(R.string.actual_delay_bluetooth)
        ActualDelay.NotMeasured -> stringResource(R.string.actual_delay_not_measured)
    }
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Delay, volume, pitch and presets: one panel, collapsed by default. */
@Composable
private fun ControlPanel(settings: Settings, isBluetooth: Boolean, viewModel: MainViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, motionSpring(1f), label = "chevron")
    val delayLabel = stringResource(R.string.delay_label, settings.delayMs)
    val openState = stringResource(if (open) R.string.expanded else R.string.collapsed)
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = openState },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.adjust), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(motionSpring(1f)) + fadeIn(motionTween(200)),
            exit = shrinkVertically(motionSpring(1f)) + fadeOut(motionTween(150))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(delayLabel, style = MaterialTheme.typography.titleMedium)
                CadenceSlider(
                    value = settings.delayMs.toFloat(),
                    onValueChange = { viewModel.setDelayMs(it.roundToInt()) },
                    valueRange = 40f..200f,
                    steps = 15,
                    enabled = !settings.fadeEnabled,
                    modifier = Modifier.semantics { contentDescription = delayLabel }
                )
                Caption(R.string.delay_caption)
                ActualDelayLine(settings, isBluetooth, viewModel)
                VolumeSection(settings.outputGainDb, viewModel::setOutputGainDb)
                PitchSection(settings.pitchSemitones, viewModel::setPitchSemitones)
                PresetSection(settings.presets, settings.presetNames, viewModel)
            }
        }
    }
}

/** Saved presets as tiles (tap = use); nothing shows until the user saves one. Edit renames or deletes. */
@Composable
private fun PresetSection(presets: List<Preset?>, names: List<String>, viewModel: MainViewModel) {
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    Text(stringResource(R.string.presets_title), style = MaterialTheme.typography.titleMedium)
    presets.forEachIndexed { slot, preset ->
        AnimatedVisibility(
            visible = preset != null,
            enter = expandVertically(motionSpring(1f)) + fadeIn(motionTween(200)),
            exit = shrinkVertically(motionSpring(1f)) + fadeOut(motionTween(150))
        ) {
            if (preset == null) return@AnimatedVisibility
            val title = names[slot].ifBlank { stringResource(R.string.preset_name_hint, slot + 1) }
            val useLabel = stringResource(R.string.preset_use_label, title)
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.Button, onClickLabel = useLabel) { viewModel.applyPreset(slot) }
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.preset_label, preset.delayMs, preset.sensitivity, preset.pitchSemitones, preset.outputGainDb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editing = slot }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.preset_edit_label, title))
                }
            }
        }
    }
    val free = presets.indexOfFirst { it == null }
    if (free >= 0) {
        OutlinedButton(onClick = { viewModel.savePreset(free) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.preset_add))
        }
        Caption(R.string.presets_caption)
    } else {
        Caption(R.string.presets_full)
    }
    editing?.let { slot ->
        var name by remember(slot) { mutableStateOf(names[slot]) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.preset_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = clampPresetName(it) },
                    placeholder = { Text(stringResource(R.string.preset_name_hint, slot + 1)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setPresetName(slot, name); editing = null }) { Text(stringResource(R.string.preset_done)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.deletePreset(slot); editing = null }) { Text(stringResource(R.string.delete)) }
            }
        )
    }
}

@Composable
private fun PitchSection(pitchSemitones: Int, onPitchChange: (Int) -> Unit) {
    val label = if (pitchSemitones == 0) {
        stringResource(R.string.pitch_off)
    } else {
        stringResource(R.string.pitch_label, pitchSemitones)
    }
    Text(label, style = MaterialTheme.typography.titleMedium)
    CadenceSlider(
        value = pitchSemitones.toFloat(),
        onValueChange = { onPitchChange(it.roundToInt()) },
        valueRange = -12f..12f,
        steps = 23,
        modifier = Modifier.semantics { contentDescription = label }
    )
    Caption(R.string.pitch_caption)
}

// Above 0 dB the voice is amplified: yellow up to +6 dB, red beyond.
private const val VOLUME_RED_DB = 6
private val VolumeYellow = Color(0xFFE8B931)
private val VolumeRed = Color(0xFFEF5350)
private val VolumeYellowOnLight = Color(0xFF8A5300)

@Composable
private fun VolumeSection(outputGainDb: Int, onVolumeChange: (Int) -> Unit) {
    val label = stringResource(R.string.volume_label, outputGainDb)
    val colors = MaterialTheme.colorScheme
    val light = colors.background.luminance() > 0.5f
    val yellow = if (light) VolumeYellowOnLight else VolumeYellow
    val red = if (light) colors.error else VolumeRed
    Text(label, style = MaterialTheme.typography.titleMedium)
    CadenceSlider(
        value = outputGainDb.toFloat(),
        onValueChange = { onVolumeChange(it.roundToInt()) },
        valueRange = -30f..12f,
        steps = 41,
        modifier = Modifier.semantics { contentDescription = label },
        accent = when {
            outputGainDb > VOLUME_RED_DB -> red
            outputGainDb > 0 -> yellow
            else -> null
        }
    )
    Caption(R.string.volume_caption)
    if (outputGainDb > 0) {
        Text(
            stringResource(R.string.volume_warning),
            style = MaterialTheme.typography.bodySmall,
            color = if (outputGainDb > VOLUME_RED_DB) red else yellow
        )
    }
}
