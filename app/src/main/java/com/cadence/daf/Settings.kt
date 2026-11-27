package com.cadence.daf

data class Settings(
    val delayMs: Int = 100,
    val micPreset: Int = 1,
    val gateEnabled: Boolean = true,
    val sensitivity: Int = 5,
    val pitchSemitones: Int = 0,
    val outputGainDb: Int = -6,
    val fadeEnabled: Boolean = false,
    val fadeStartMs: Int = 100,
    val fadeStepMs: Int = 10,
    val fadeStartDay: Long = 0,
    val fadeTargetMs: Int = 40,
    val fadeIntervalDays: Int = 7,
    /** Epoch day the program was paused on; -1 = running. */
    val fadePauseDay: Long = -1,
    val disclaimerAccepted: Boolean = false,
    val dailyGoalMin: Int = 10,
    val reminderEnabled: Boolean = false,
    val reminderMinute: Int = 18 * 60,
    val sessionLimitMin: Int = 0,
    val presets: List<Preset?> = List(PRESET_SLOTS) { null },
    val presetNames: List<String> = List(PRESET_SLOTS) { "" },
    val maskingEnabled: Boolean = false,
    val maskingDb: Int = -25,
    val noiseReduction: Boolean = false,
    val widgetStyle: WidgetStyle = WidgetStyle(),
    val lightMode: Boolean = false,
) {
    /** The value for nativeSetMaskingDb: the level, or MASKING_OFF_DB when masking is off. */
    val engineMaskingDb: Int get() = if (maskingEnabled) maskingDb else MASKING_OFF_DB

    /** The fading program's delay for [today]; a paused program stays at its pause day. */
    fun programDelay(today: Long): Int =
        programDelayMs(fadeStartMs, fadeStepMs, fadeStartDay, if (fadePauseDay >= 0) fadePauseDay else today, fadeTargetMs, fadeIntervalDays)
}

const val MASKING_OFF_DB = -100

/** Look of the home screen widgets: index into WidgetAccent, light or dark, background opacity in percent. */
data class WidgetStyle(val accent: Int = 0, val light: Boolean = false, val opacity: Int = 100)

fun clampWidgetAccent(index: Int): Int = if (index in WidgetAccent.entries.indices) index else 0

fun clampWidgetOpacity(percent: Int): Int = percent.coerceIn(0, 100)

fun clampMaskingDb(db: Int): Int = db.coerceIn(-40, -10)

val SESSION_LIMIT_CHOICES = listOf(0, 5, 10, 15, 20, 30)

fun clampSessionLimitMin(min: Int): Int = if (min in SESSION_LIMIT_CHOICES) min else 0

fun clampDelayMs(ms: Int): Int = ms.coerceIn(40, 200)

fun clampSensitivity(level: Int): Int = level.coerceIn(0, 10)

fun clampPitchSemitones(semitones: Int): Int = semitones.coerceIn(-12, 12)

fun clampOutputGainDb(db: Int): Int = db.coerceIn(-30, 12)

fun clampFadeStartMs(ms: Int): Int = ms.coerceIn(40, 200)

fun clampFadeStepMs(ms: Int): Int = ms.coerceIn(0, 50)

fun clampFadeTargetMs(ms: Int): Int = ms.coerceIn(40, 200)

fun clampFadeIntervalDays(days: Int): Int = days.coerceIn(1, 14)

fun clampPresetName(name: String): String = name.take(16)

fun clampMicPreset(index: Int): Int = if (index == 3) 3 else 1

fun clampReminderMinute(minute: Int): Int = minute.coerceIn(0, 24 * 60 - 1)

fun clampDailyGoalMin(min: Int): Int = if (min in listOf(0, 5, 10, 15, 20)) min else 10
