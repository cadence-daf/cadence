package com.cadence.daf

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val GOAL_CHOICES = listOf(0, 5, 10, 15, 20)

/** Goal reached: the goal is on and today's minutes meet it. */
internal fun goalReached(todayMin: Int, goalMin: Int) = goalMin > 0 && todayMin >= goalMin

/** Grows from 0 to [target] once when the screen opens (unless [animateOpen] is false); instant when system animations are off. */
@Composable
internal fun rememberGrow(target: Float, animateOpen: Boolean = true): Float {
    var opened by remember { mutableStateOf(!animateOpen) }
    LaunchedEffect(Unit) { opened = true }
    val grown by animateFloatAsState(if (opened) target else 0f, motionTween(300), label = "grow")
    return grown
}

@Composable
fun ProgressScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val stats = state.practice
    val goal = state.settings.dailyGoalMin
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ScreenScaffold {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium)
        }
        SectionCard {
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stat_all_time), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (stats.allTimeMin < 60) stringResource(R.string.duration_min, stats.allTimeMin)
                    else stringResource(R.string.duration_h_min, stats.allTimeMin / 60, stats.allTimeMin % 60),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        SectionCard {
            DayChart(state.dayMinutes, goal)
        }
        SectionCard {
            TrendChart(state.dayMinutes, goal)
        }
        SectionCard {
            Text(stringResource(R.string.daily_goal), style = MaterialTheme.typography.titleMedium)
            Caption(R.string.goal_caption)
            MinuteChips(GOAL_CHOICES, goal, viewModel::setDailyGoalMin)
            TextButton(onClick = {
                scope.launch {
                    // shortcut: shared as plain text, not a file; switch to FileProvider if the log outgrows ~500 kB.
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.practiceCsv())
                    context.startActivity(Intent.createChooser(send, null))
                }
            }) {
                Text(stringResource(R.string.export_history), color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { confirmDelete = true }) {
                Text(stringResource(R.string.delete_history), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            text = { Text(stringResource(R.string.delete_history_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPracticeHistory(); confirmDelete = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM")
private val CHART_HEIGHT = 140.dp
private val Y_LABEL_WIDTH = 32.dp

/** Minutes per day as a line: 7 or 30 days, with arrows to step back in time. */
@Composable
private fun TrendChart(dayMinutes: Map<LocalDate, Int>, goal: Int) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var range by rememberSaveable { mutableStateOf(7) }
    var offset by rememberSaveable { mutableStateOf(0) }
    val first = remember(dayMinutes) { dayMinutes.keys.minOrNull() ?: today }
    val end = today.minusDays((offset * range).toLong())
    val days = remember(dayMinutes, end, range) {
        (range - 1 downTo 0).map { back -> end.minusDays(back.toLong()).let { DayTotal(it, dayMinutes[it] ?: 0) } }
    }
    val total = days.sumOf { it.minutes }
    val practised = days.count { it.minutes > 0 }
    val period = "${days.first().date.format(shortDate)} – ${end.format(shortDate)}"
    val detail = stringResource(
        R.string.trend_detail,
        pluralStringResource(R.plurals.minutes_count, total, total),
        pluralStringResource(R.plurals.days_count, practised, practised)
    )
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    var plotWidth by remember { mutableStateOf(0) }
    val picked = selected?.let { days[it] }
    val shownDetail = if (picked == null) detail else
        "${picked.date.format(dayFormat)} · ${pluralStringResource(R.plurals.minutes_count, picked.minutes, picked.minutes)}"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to R.string.range_7, 30 to R.string.range_30).forEach { (r, label) ->
            val selected = range == r
            FilterChip(
                selected = selected,
                onClick = { range = r; offset = 0 },
                label = { Text(stringResource(label)) },
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline)
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { offset++ }, enabled = end.minusDays(range.toLong()) >= first) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_period))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(period, style = MaterialTheme.typography.titleMedium)
            Text(shownDetail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        IconButton(onClick = { offset-- }, enabled = offset > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_period))
        }
    }
    val top = ((maxOf(days.maxOf { it.minutes }, goal, 5) + 4) / 5) * 5
    Row(Modifier.height(CHART_HEIGHT)) {
        Column(Modifier.width(Y_LABEL_WIDTH).fillMaxHeight().clearAndSetSemantics {}, verticalArrangement = Arrangement.SpaceBetween) {
            Text(top.toString(), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, textAlign = TextAlign.End)
            Text("0", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, textAlign = TextAlign.End)
        }
        Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { plotWidth = it.width }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "$period: $detail" }
                // Tap a day, or drag along the line, to read its minutes.
                .pointerInput(days) {
                    detectTapGestures { selected = (it.x / size.width * days.size).toInt().coerceIn(0, days.size - 1) }
                }
                .pointerInput(days) {
                    detectHorizontalDragGestures { change, _ ->
                        selected = (change.position.x / size.width * days.size).toInt().coerceIn(0, days.size - 1)
                    }
                }
        ) {
            // 8 dp top and bottom so the y labels (16 dp tall) centre on the first and last gridline.
            val pad = 8.dp.toPx()
            val plotH = size.height - 2 * pad
            fun y(v: Int) = pad + plotH * (1f - v.toFloat() / top)
            fun x(i: Int) = size.width * (i + 0.5f) / days.size
            listOf(0, top / 2, top).forEach {
                drawLine(colors.outlineVariant, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx())
            }
            if (goal in 1..top) {
                drawLine(
                    colors.primary.copy(alpha = 0.5f), Offset(0f, y(goal)), Offset(size.width, y(goal)), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            }
            val line = Path().apply {
                days.forEachIndexed { i, d -> if (i == 0) moveTo(x(i), y(d.minutes)) else lineTo(x(i), y(d.minutes)) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(days.size - 1), y(0))
                lineTo(x(0), y(0))
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.30f), Color.Transparent), startY = pad, endY = pad + plotH))
            drawPath(line, colors.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            days.forEachIndexed { i, d ->
                if (days.size <= 7 || d.minutes > 0) drawCircle(colors.primary, if (days.size <= 7) 4.dp.toPx() else 2.5.dp.toPx(), Offset(x(i), y(d.minutes)))
            }
            selected?.let { i ->
                drawLine(colors.onSurfaceVariant.copy(alpha = 0.4f), Offset(x(i), pad), Offset(x(i), pad + plotH), 1.dp.toPx())
                drawCircle(colors.primary.copy(alpha = 0.3f), 9.dp.toPx(), Offset(x(i), y(days[i].minutes)))
                drawCircle(colors.primary, 5.dp.toPx(), Offset(x(i), y(days[i].minutes)))
            }
        }
        if (picked != null && plotWidth > 0) {
            val density = LocalDensity.current
            val i = selected ?: 0
            val tipWidth = 56.dp
            val px = with(density) { (plotWidth * (i + 0.5f) / days.size).toDp() }
            val py = 8.dp + (CHART_HEIGHT - 16.dp) * (1f - picked.minutes.toFloat() / top)
            Text(
                stringResource(R.string.duration_min, picked.minutes),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inverseOnSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(
                        x = (px - tipWidth / 2).coerceIn(0.dp, with(density) { plotWidth.toDp() } - tipWidth),
                        y = if (py > 36.dp) py - 34.dp else py + 12.dp
                    )
                    .width(tipWidth)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.inverseSurface)
                    .padding(vertical = 3.dp)
            )
        }
        }
    }
    // Same width as the canvas, so each label sits under its point.
    Row(Modifier.fillMaxWidth().padding(start = Y_LABEL_WIDTH).clearAndSetSemantics {}) {
        if (days.size <= 7) {
            days.forEach { d ->
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                )
            }
        } else {
            listOf(days.first(), days[days.size / 2], days.last()).forEachIndexed { i, d ->
                Text(
                    d.date.format(shortDate), Modifier.weight(1f),
                    textAlign = when (i) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End },
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun MinuteChips(choices: List<Int>, goal: Int, onSelect: (Int) -> Unit) {
    ChoiceChips(choices, goal, { stringResource(if (it == 0) R.string.goal_off else R.string.goal_minutes, it) }, onSelect)
}

/** One selected chip out of [choices]: border and check mark. [dot] shows a color on the other chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceChips(
    choices: List<T>,
    current: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    dot: ((T) -> Color)? = null
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            val selected = choice == current
            FilterChip(
                selected = selected,
                onClick = { onSelect(choice) },
                label = { Text(label(choice)) },
                leadingIcon = when {
                    selected -> { { Icon(Icons.Filled.Check, contentDescription = null) } }
                    dot != null -> { { Box(Modifier.size(14.dp).background(dot(choice), CircleShape)) } }
                    else -> null
                },
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline)
            )
        }
    }
}

/** Weeks as rows, Monday first; null pads before the first day and after today. */
internal fun heatmapRows(days: List<DayTotal>): List<List<DayTotal?>> {
    if (days.isEmpty()) return emptyList()
    val padded = List(days.first().date.dayOfWeek.value - 1) { null } + days
    return padded.chunked(7).map { it + List(7 - it.size) { null } }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy")

/** One month at a time, with arrows back to the first logged month. Tap a day for its date and minutes. */
@Composable
private fun DayChart(dayMinutes: Map<LocalDate, Int>, goal: Int) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)
    val firstMonth = remember(dayMinutes) { dayMinutes.keys.minOrNull()?.let { YearMonth.from(it) } ?: thisMonth }
    var monthText by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    val month = YearMonth.parse(monthText)
    val days = remember(dayMinutes, month) { monthDays(dayMinutes, month, today) }
    val monthMin = days.sumOf { it.minutes }
    val monthTitle = month.format(monthFormat)
    val description = stringResource(
        R.string.chart_description,
        monthTitle,
        pluralStringResource(R.plurals.minutes_count, monthMin, monthMin),
        pluralStringResource(R.plurals.days_count, days.count { it.minutes > 0 }, days.count { it.minutes > 0 })
    )
    val rows = remember(days) { heatmapRows(days) }
    var selected by remember(month) { mutableStateOf<LocalDate?>(null) }
    val picked = days.firstOrNull { it.date == selected }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { monthText = month.minusMonths(1).toString() }, enabled = month > firstMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
        }
        Text(monthTitle, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < thisMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
    }
    // Monday first, like the grid; letters follow the phone's language.
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        DayOfWeek.values().forEach { d ->
            Text(
                d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(7f / rows.size.coerceAtLeast(1))
            .semantics { contentDescription = description }
            .pointerInput(rows) {
                detectTapGestures { tap ->
                    val cell = size.width / 7f
                    selected = rows.getOrNull((tap.y / cell).toInt())?.getOrNull((tap.x / cell).toInt())?.date
                }
            }
    ) {
        val cell = size.width / 7f
        val gap = 3.dp.toPx()
        val corner = CornerRadius(4.dp.toPx())
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, day ->
                if (day == null) return@forEachIndexed
                val topLeft = Offset(c * cell + gap, r * cell + gap)
                val box = Size(cell - 2 * gap, cell - 2 * gap)
                val atGoal = goal > 0 && day.minutes >= goal
                val fill = when {
                    day.minutes == 0 -> colors.surfaceVariant
                    atGoal -> colors.primary
                    else -> colors.primaryContainer
                }
                drawRoundRect(fill, topLeft, box, corner)
                if (day.minutes > 0 && !atGoal) drawRoundRect(colors.primary, topLeft, box, corner, Stroke(1.dp.toPx()))
                if (day.date == selected) drawRoundRect(colors.onSurface, topLeft, box, corner, Stroke(2.dp.toPx()))
            }
        }
    }
    Text(
        if (picked == null) {
            stringResource(R.string.heatmap_hint)
        } else {
            stringResource(
                R.string.heatmap_day,
                picked.date.format(dayFormat),
                pluralStringResource(R.plurals.minutes_count, picked.minutes, picked.minutes)
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant
    )
}
