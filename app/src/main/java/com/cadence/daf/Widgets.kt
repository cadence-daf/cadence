package com.cadence.daf

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private const val ACTION_WIDGET_DELAY = "com.cadence.daf.action.WIDGET_NEXT_DELAY"
private const val DIAL_DOTS = 7
private const val DIAL_DP = 144
private const val BARS_WIDTH_DP = 210
private const val BARS_HEIGHT_DP = 120

/** Dial steps lit: 0..7 are the dots, 8 adds the echo ring (goal reached). Goal off: none. */
fun litSteps(todayMin: Int, goalMin: Int): Int = when {
    goalMin == 0 || todayMin <= 0 -> 0
    todayMin >= goalMin -> DIAL_DOTS + 1
    else -> (todayMin * DIAL_DOTS / goalMin).coerceAtLeast(1)
}

/** The last 7 days, oldest first, today last. */
fun lastWeek(minutes: Map<LocalDate, Int>, today: LocalDate): List<DayTotal> =
    (6 downTo 0).map { today.minusDays(it.toLong()) }.map { DayTotal(it, minutes[it] ?: 0) }

/** The accent colors a user can pick; each has a shade for the dark and for the light background. */
enum class WidgetAccent(@StringRes val label: Int, val onDark: Long, val onLight: Long) {
    TEAL(R.string.color_teal, 0xFF7FD3C6, 0xFF1F5F66),
    BLUE(R.string.color_blue, 0xFF8AB4F8, 0xFF1A5BB8),
    VIOLET(R.string.color_violet, 0xFFC4B5FD, 0xFF5B3FB5),
    AMBER(R.string.color_amber, 0xFFF5C469, 0xFF8A5300),
    ROSE(R.string.color_rose, 0xFFF4A6B8, 0xFFA8324F),
    PLAIN(R.string.color_plain, 0xFFE8EEEC, 0xFF1B1D1E);

    fun argb(light: Boolean): Int = (if (light) onLight else onDark).toInt()
}

/** Every color a widget draws with, for one [WidgetStyle]. Tracks are see-through, so they suit any opacity. */
private class Palette(style: WidgetStyle) {
    val background = (if (style.light) 0xFFF7F5F0 else 0xFF0F1416).toInt()
    val text = (if (style.light) 0xFF1B1D1E else 0xFFE8EEEC).toInt()
    val textMuted = (if (style.light) 0xFF5A6466 else 0xFF8DA19C).toInt()
    val track = (if (style.light) 0x14000000 else 0x1FFFFFFF).toInt()
    val dim = (if (style.light) 0x33000000 else 0x40FFFFFF).toInt()
    val accent = WidgetAccent.entries[style.accent].argb(style.light)
    val accentSoft = (accent and 0x00FFFFFF) or 0x40000000
}

/** The system asks for a redraw when a widget is added, after a reboot and every 30 minutes. */
abstract class CadenceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        Widgets.refresh(context) { result.finish() }
    }
}

/** 1x1 and up: the dotted-C dial. The whole widget is the Start/Stop button. */
class PracticeWidget : CadenceWidget()

/** 4x2: the dial, the last 7 days and the delay preset button. */
class WeekWidget : CadenceWidget() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_DELAY) return super.onReceive(context, intent)
        val result = goAsync()
        Widgets.nextDelay(context) { result.finish() }
    }
}

object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One redraw at a time, so an older one can never overwrite a newer one.
    private val lock = Mutex()

    /** Redraws every placed widget from the current state. Safe to call from any thread. */
    fun refresh(context: Context, done: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try {
                lock.withLock { draw(app) }
            } finally {
                done()
            }
        }
    }

    fun nextDelay(context: Context, done: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            try {
                val repository = SettingsRepository(app)
                val settings = repository.settings.first()
                // The fading program owns the delay (DESIGN.md section 18).
                if (!settings.fadeEnabled) repository.setDelayMs(nextDelayPreset(settings.delayMs))
                lock.withLock { draw(app) }
            } finally {
                done()
            }
        }
    }

    private suspend fun draw(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val small = manager.getAppWidgetIds(ComponentName(context, PracticeWidget::class.java))
        val wide = manager.getAppWidgetIds(ComponentName(context, WeekWidget::class.java))
        if (small.isEmpty() && wide.isEmpty()) return

        val settings = SettingsRepository(context).settings.first()
        val lines = PracticeLogStore(context).readLines()
        val today = LocalDate.now()
        val stats = practiceStats(lines, today)
        val goal = settings.dailyGoalMin
        val todayText = when {
            goal == 0 -> context.getString(R.string.practice_today, stats.todayMin)
            goalReached(stats.todayMin, goal) -> context.getString(R.string.practice_goal_reached, stats.todayMin)
            else -> context.getString(R.string.practice_today_goal, stats.todayMin, goal)
        }
        val running = AudioService.running.value
        val palette = Palette(settings.widgetStyle)
        val dial = drawDial(context, palette, running, litSteps(stats.todayMin, goal))
        val action = context.getString(if (running) R.string.stop else R.string.start)
        val dialLabel = context.getString(R.string.practice_row_label, action, todayText)
        val toggle = if (running) stop(context) else start(context, settings)

        fun views(layout: Int) = RemoteViews(context.packageName, layout).apply {
            setInt(R.id.widget_bg, "setColorFilter", palette.background)
            setInt(R.id.widget_bg, "setImageAlpha", settings.widgetStyle.opacity * 255 / 100)
            setImageViewBitmap(R.id.widget_dial, dial)
            setContentDescription(R.id.widget_dial, dialLabel)
        }

        // The layouts with text; the 1x1 layout has only the dial.
        fun RemoteViews.bindText() {
            setTextViewText(R.id.widget_today, todayText)
            setTextColor(R.id.widget_today, palette.text)
            setTextColor(R.id.widget_timer, palette.accent)
            setChronometer(R.id.widget_timer, AudioService.startedAtMs.value, null, running)
            setViewVisibility(R.id.widget_timer, if (running) View.VISIBLE else View.GONE)
        }

        if (small.isNotEmpty()) {
            val tiny = views(R.layout.widget_practice_tiny)
            tiny.setOnClickPendingIntent(android.R.id.background, toggle)
            // Before Android 12 a widget cannot offer a layout per size, so it stays dial-only.
            val sized = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) tiny else {
                val card = views(R.layout.widget_practice)
                card.bindText()
                // Little room here: the timer replaces the today line while running.
                card.setViewVisibility(R.id.widget_today, if (running) View.GONE else View.VISIBLE)
                card.setOnClickPendingIntent(android.R.id.background, toggle)
                RemoteViews(mapOf(SizeF(40f, 40f) to tiny, SizeF(120f, 120f) to card))
            }
            manager.updateAppWidget(small, sized)
        }
        if (wide.isNotEmpty()) {
            val card = views(R.layout.widget_week)
            card.bindText()
            card.setOnClickPendingIntent(android.R.id.background, openApp(context))
            card.setOnClickPendingIntent(R.id.widget_dial, toggle)
            val week = lastWeek(minutesByDay(lines, today), today)
            card.setImageViewBitmap(R.id.widget_bars, drawBars(context, palette, week, goal))
            card.setContentDescription(R.id.widget_bars, context.getString(R.string.practice_days, stats.weekDays))
            card.setTextViewText(R.id.widget_delay, context.getString(R.string.delay_action, settings.delayMs))
            card.setTextColor(R.id.widget_delay, palette.text)
            val delayTap = if (settings.fadeEnabled) openApp(context) else PendingIntent.getBroadcast(
                context, 0, Intent(context, WeekWidget::class.java).setAction(ACTION_WIDGET_DELAY),
                PendingIntent.FLAG_IMMUTABLE
            )
            card.setOnClickPendingIntent(R.id.widget_delay, delayTap)
            manager.updateAppWidget(wide, card)
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE
    )

    // Start runs without a screen. The disclaimer and the microphone question need one, so until
    // both are done Start opens the app, like the launcher shortcut.
    private fun start(context: Context, settings: Settings): PendingIntent {
        val ready = settings.disclaimerAccepted &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (ready) {
            return PendingIntent.getService(context, 0, AudioService.quickStartIntent(context), PendingIntent.FLAG_IMMUTABLE)
        }
        return PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction(ACTION_START_PRACTICE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stop(context: Context): PendingIntent = PendingIntent.getService(
        context, 0, AudioService.stopIntent(context), PendingIntent.FLAG_IMMUTABLE
    )

    /** The logo's dotted C as a progress dial around a round Start (triangle) or Stop (square) button. */
    private fun drawDial(context: Context, palette: Palette, running: Boolean, lit: Int): Bitmap {
        val size = (DIAL_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(size / 100f, size / 100f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dots fill clockwise from below the gap (screen angle 315 degrees down to 45); the ring closes the C.
        for (i in 0 until DIAL_DOTS) {
            val angle = Math.toRadians(315.0 - 45.0 * i)
            paint.color = if (i < lit) palette.text else palette.dim
            canvas.drawCircle(50f + 42f * cos(angle).toFloat(), 50f - 42f * sin(angle).toFloat(), 5.5f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = if (lit > DIAL_DOTS) palette.accent else palette.dim
        canvas.drawCircle(92f, 50f, 6f, paint)

        paint.style = Paint.Style.FILL
        if (running) {
            paint.color = palette.accentSoft
            canvas.drawCircle(50f, 50f, 34f, paint)
        }
        paint.color = if (running) palette.text else palette.accent
        canvas.drawCircle(50f, 50f, 28f, paint)

        paint.color = palette.background
        if (running) {
            canvas.drawRoundRect(RectF(41f, 41f, 59f, 59f), 3f, 3f, paint)
        } else {
            val triangle = Path().apply {
                moveTo(42.5f, 38f)
                lineTo(62.5f, 50f)
                lineTo(42.5f, 62f)
                close()
            }
            canvas.drawPath(triangle, paint)
        }
        return bitmap
    }

    /** Seven bars on faint tracks, with weekday letters. Filled = goal reached; outlined = below it; dot = no practice. */
    private fun drawBars(context: Context, palette: Palette, week: List<DayTotal>, goal: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(
            (BARS_WIDTH_DP * density).toInt(), (BARS_HEIGHT_DP * density).toInt(), Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 11f
        paint.textAlign = Paint.Align.CENTER

        val slot = BARS_WIDTH_DP / 7f
        val barBottom = BARS_HEIGHT_DP - 18f
        val top = maxOf(goal, week.maxOf { it.minutes }, 1)
        week.forEachIndexed { i, day ->
            val x = slot * (i + 0.5f)
            paint.style = Paint.Style.FILL
            paint.color = palette.track
            canvas.drawRoundRect(RectF(x - 8f, 2f, x + 8f, barBottom), 8f, 8f, paint)
            // At least as tall as wide, so the shortest bar is still a dot.
            val height = maxOf(16f, (barBottom - 2f) * day.minutes / top)
            val bar = RectF(x - 8f, barBottom - height, x + 8f, barBottom)
            paint.color = when {
                day.minutes == 0 -> palette.dim
                goalReached(day.minutes, goal) -> palette.accent
                else -> palette.accentSoft
            }
            canvas.drawRoundRect(bar, 8f, 8f, paint)
            if (day.minutes > 0 && !goalReached(day.minutes, goal)) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = palette.accent
                bar.inset(0.5f, 0.5f)
                canvas.drawRoundRect(bar, 7.5f, 7.5f, paint)
            }
            val isToday = i == week.lastIndex
            paint.style = Paint.Style.FILL
            paint.color = if (isToday) palette.text else palette.textMuted
            paint.typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(
                day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                x, BARS_HEIGHT_DP - 4f, paint
            )
        }
        return bitmap
    }
}
