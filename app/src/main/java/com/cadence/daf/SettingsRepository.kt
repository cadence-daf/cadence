package com.cadence.daf

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "cadence_settings")

class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    private val defaults = Settings()

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            delayMs = clampDelayMs(prefs[DELAY_MS] ?: defaults.delayMs),
            micPreset = clampMicPreset(prefs[MIC_PRESET] ?: defaults.micPreset),
            gateEnabled = prefs[GATE_ENABLED] ?: defaults.gateEnabled,
            sensitivity = clampSensitivity(prefs[SENSITIVITY] ?: defaults.sensitivity),
            pitchSemitones = clampPitchSemitones(prefs[PITCH_SEMITONES] ?: defaults.pitchSemitones),
            outputGainDb = clampOutputGainDb(prefs[OUTPUT_GAIN_DB] ?: defaults.outputGainDb),
            fadeEnabled = prefs[FADE_ENABLED] ?: defaults.fadeEnabled,
            fadeStartMs = clampFadeStartMs(prefs[FADE_START_MS] ?: defaults.fadeStartMs),
            fadeStepMs = clampFadeStepMs(prefs[FADE_STEP_MS] ?: defaults.fadeStepMs),
            fadeStartDay = prefs[FADE_START_DAY] ?: defaults.fadeStartDay,
            fadeTargetMs = clampFadeTargetMs(prefs[FADE_TARGET_MS] ?: defaults.fadeTargetMs),
            fadeIntervalDays = clampFadeIntervalDays(prefs[FADE_INTERVAL_DAYS] ?: defaults.fadeIntervalDays),
            fadePauseDay = prefs[FADE_PAUSE_DAY] ?: defaults.fadePauseDay,
            disclaimerAccepted = prefs[DISCLAIMER_ACCEPTED] ?: defaults.disclaimerAccepted,
            dailyGoalMin = clampDailyGoalMin(prefs[DAILY_GOAL_MIN] ?: defaults.dailyGoalMin),
            reminderEnabled = prefs[REMINDER_ENABLED] ?: defaults.reminderEnabled,
            reminderMinute = clampReminderMinute(prefs[REMINDER_MINUTE] ?: defaults.reminderMinute),
            sessionLimitMin = clampSessionLimitMin(prefs[SESSION_LIMIT_MIN] ?: defaults.sessionLimitMin),
            presets = PRESETS.map { parsePreset(prefs[it]) },
            presetNames = PRESET_NAMES.map { prefs[it] ?: "" },
            maskingEnabled = prefs[MASKING_ENABLED] ?: defaults.maskingEnabled,
            maskingDb = clampMaskingDb(prefs[MASKING_DB] ?: defaults.maskingDb),
            noiseReduction = prefs[NOISE_REDUCTION] ?: defaults.noiseReduction,
            lightMode = prefs[LIGHT_MODE] ?: defaults.lightMode,
            widgetStyle = WidgetStyle(
                clampWidgetAccent(prefs[WIDGET_ACCENT] ?: defaults.widgetStyle.accent),
                prefs[WIDGET_LIGHT] ?: defaults.widgetStyle.light,
                clampWidgetOpacity(prefs[WIDGET_OPACITY] ?: defaults.widgetStyle.opacity),
            ),
        )
    }

    suspend fun setNoiseReduction(enabled: Boolean) {
        store.edit { it[NOISE_REDUCTION] = enabled }
    }

    suspend fun setLightMode(enabled: Boolean) {
        store.edit { it[LIGHT_MODE] = enabled }
    }

    suspend fun setWidgetStyle(style: WidgetStyle) {
        store.edit {
            it[WIDGET_ACCENT] = clampWidgetAccent(style.accent)
            it[WIDGET_LIGHT] = style.light
            it[WIDGET_OPACITY] = clampWidgetOpacity(style.opacity)
        }
    }

    suspend fun setMaskingEnabled(enabled: Boolean) {
        store.edit { it[MASKING_ENABLED] = enabled }
    }

    suspend fun setMaskingDb(db: Int) {
        store.edit { it[MASKING_DB] = clampMaskingDb(db) }
    }

    suspend fun setSessionLimitMin(min: Int) {
        store.edit { it[SESSION_LIMIT_MIN] = clampSessionLimitMin(min) }
    }

    /** Stores the current delay, sensitivity, pitch and volume in slot 0..2. */
    suspend fun savePreset(slot: Int) {
        val s = settings.first()
        store.edit { it[PRESETS[slot]] = encodePreset(Preset(s.delayMs, s.sensitivity, s.pitchSemitones, s.outputGainDb)) }
    }

    /** Applies slot 0..2. The delay is left alone while the fading program owns it. */
    suspend fun applyPreset(slot: Int) {
        val s = settings.first()
        val preset = s.presets[slot] ?: return
        store.edit {
            if (!s.fadeEnabled) it[DELAY_MS] = preset.delayMs
            it[SENSITIVITY] = preset.sensitivity
            it[PITCH_SEMITONES] = preset.pitchSemitones
            it[OUTPUT_GAIN_DB] = preset.outputGainDb
        }
    }

    suspend fun setDelayMs(ms: Int) {
        store.edit { it[DELAY_MS] = clampDelayMs(ms) }
    }

    suspend fun setMicPreset(index: Int) {
        store.edit { it[MIC_PRESET] = clampMicPreset(index) }
    }

    suspend fun setGateEnabled(enabled: Boolean) {
        store.edit { it[GATE_ENABLED] = enabled }
    }

    suspend fun setSensitivity(level: Int) {
        store.edit { it[SENSITIVITY] = clampSensitivity(level) }
    }

    suspend fun setPitchSemitones(semitones: Int) {
        store.edit { it[PITCH_SEMITONES] = clampPitchSemitones(semitones) }
    }

    suspend fun setOutputGainDb(db: Int) {
        store.edit { it[OUTPUT_GAIN_DB] = clampOutputGainDb(db) }
    }

    suspend fun setFadeEnabled(enabled: Boolean) {
        store.edit {
            it[FADE_ENABLED] = enabled
            if (enabled) it[FADE_START_DAY] = LocalDate.now().toEpochDay()
            it.remove(FADE_PAUSE_DAY)
        }
    }

    suspend fun setFadeStartMs(ms: Int) {
        store.edit { it[FADE_START_MS] = clampFadeStartMs(ms) }
    }

    suspend fun setFadeStepMs(ms: Int) {
        store.edit { it[FADE_STEP_MS] = clampFadeStepMs(ms) }
    }

    suspend fun setFadeTargetMs(ms: Int) {
        store.edit { it[FADE_TARGET_MS] = clampFadeTargetMs(ms) }
    }

    suspend fun setFadeIntervalDays(days: Int) {
        store.edit { it[FADE_INTERVAL_DAYS] = clampFadeIntervalDays(days) }
    }

    /** Pausing freezes the program on today's delay; resuming moves the start day forward by the days paused. */
    suspend fun setFadePaused(paused: Boolean) {
        val today = LocalDate.now().toEpochDay()
        store.edit {
            val pausedOn = it[FADE_PAUSE_DAY] ?: -1L
            if (paused && pausedOn < 0) it[FADE_PAUSE_DAY] = today
            if (!paused && pausedOn >= 0) {
                it[FADE_START_DAY] = (it[FADE_START_DAY] ?: 0L) + (today - pausedOn)
                it.remove(FADE_PAUSE_DAY)
            }
        }
    }

    /** Raises the delay by one step (the program keeps going from there). */
    suspend fun stepFadeBack() {
        val s = settings.first()
        val day = if (s.fadePauseDay >= 0) s.fadePauseDay else LocalDate.now().toEpochDay()
        store.edit {
            it[FADE_START_DAY] = stepBackStartDay(s.fadeStartMs, s.fadeStepMs, s.fadeStartDay, day, s.fadeTargetMs, s.fadeIntervalDays)
        }
    }

    suspend fun deletePreset(slot: Int) {
        store.edit {
            it.remove(PRESETS[slot])
            it.remove(PRESET_NAMES[slot])
        }
    }

    suspend fun setPresetName(slot: Int, name: String) {
        store.edit { it[PRESET_NAMES[slot]] = clampPresetName(name) }
    }

    suspend fun setDisclaimerAccepted() {
        store.edit { it[DISCLAIMER_ACCEPTED] = true }
    }

    suspend fun setDailyGoalMin(min: Int) {
        store.edit { it[DAILY_GOAL_MIN] = clampDailyGoalMin(min) }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        store.edit { it[REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderMinute(minute: Int) {
        store.edit { it[REMINDER_MINUTE] = clampReminderMinute(minute) }
    }

    suspend fun applyFadeProgram() {
        val s = settings.first()
        if (s.fadeEnabled) {
            setDelayMs(s.programDelay(LocalDate.now().toEpochDay()))
        }
    }

    private companion object {
        val DELAY_MS = intPreferencesKey("delay_ms")
        val MIC_PRESET = intPreferencesKey("mic_preset")
        val GATE_ENABLED = booleanPreferencesKey("gate_enabled")
        val SENSITIVITY = intPreferencesKey("sensitivity")
        val PITCH_SEMITONES = intPreferencesKey("pitch_semitones")
        val OUTPUT_GAIN_DB = intPreferencesKey("output_gain_db")
        val FADE_ENABLED = booleanPreferencesKey("fade_enabled")
        val FADE_START_MS = intPreferencesKey("fade_start_ms")
        val FADE_STEP_MS = intPreferencesKey("fade_step_ms")
        val FADE_START_DAY = longPreferencesKey("fade_start_day")
        val FADE_TARGET_MS = intPreferencesKey("fade_target_ms")
        val FADE_INTERVAL_DAYS = intPreferencesKey("fade_interval_days")
        val FADE_PAUSE_DAY = longPreferencesKey("fade_pause_day")
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val DAILY_GOAL_MIN = intPreferencesKey("daily_goal_min")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val NOISE_REDUCTION = booleanPreferencesKey("noise_reduction")
        val MASKING_ENABLED =booleanPreferencesKey("masking_enabled")
        val MASKING_DB = intPreferencesKey("masking_db")
        val LIGHT_MODE = booleanPreferencesKey("light_mode")
        val WIDGET_ACCENT = intPreferencesKey("widget_accent")
        val WIDGET_LIGHT = booleanPreferencesKey("widget_light")
        val WIDGET_OPACITY = intPreferencesKey("widget_opacity")
        val SESSION_LIMIT_MIN =intPreferencesKey("session_limit_min")
        val PRESETS = List(PRESET_SLOTS) { stringPreferencesKey("preset_${it + 1}") }
        val PRESET_NAMES = List(PRESET_SLOTS) { stringPreferencesKey("preset_name_${it + 1}") }
    }
}
