package com.cadence.daf

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val settings: Settings,
    val headset: HeadsetState,
    val running: Boolean,
    val error: String?,

    val practice: PracticeStats = PracticeStats(0, 0, 0, 0, 0, 0),
    val dayMinutes: Map<LocalDate, Int> = emptyMap(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val headsetMonitor = HeadsetMonitor(application)
    private val _telemetry = MutableStateFlow(Telemetry())
    /** Live values, 10 per second while running. Kept out of uiState so only the parts that show them recompose. */
    val telemetry: StateFlow<Telemetry> = _telemetry
    private val practiceLog = PracticeLogStore(application)
    private val practice = MutableStateFlow(PracticeStats(0, 0, 0, 0, 0, 0) to emptyMap<LocalDate, Int>())
    private val sessionEvents = Channel<Int>(Channel.BUFFERED)

    /** Minutes practised today, once per logged session; the screen shows the Snackbar. */
    val sessionSummary: Flow<Int> = sessionEvents.receiveAsFlow()

    val uiState: StateFlow<UiState> = combine(
        repository.settings,
        headsetMonitor.state,
        AudioService.running,
        AudioService.lastError,
        practice,
    ) { settings, headset, running, error, days ->
        UiState(settings, headset, running, error, days.first, days.second)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState(Settings(disclaimerAccepted = true), HeadsetState(), running = false, error = null),
    )

    init {
        viewModelScope.launch { repository.applyFadeProgram() }
        viewModelScope.launch {
            repository.settings.map { it.reminderEnabled to it.reminderMinute }.distinctUntilChanged()
                // Skip the initial value: re-arming on app start would cancel a pending alarm that is late but not yet fired.
                .drop(1)
                .collect { (enabled, minute) -> Reminders.schedule(application, enabled, minute) }
        }
        viewModelScope.launch {
            // The settings the home screen widgets show; the first value also redraws them on app start.
            repository.settings
                .map { listOf(it.delayMs, it.dailyGoalMin, it.fadeEnabled, it.disclaimerAccepted, it.widgetStyle) }
                .distinctUntilChanged()
                .collect { Widgets.refresh(application) }
        }
        viewModelScope.launch {
            // Refreshes on start/stop and every 15 s, so the day rolls over at midnight.
            AudioService.running.collectLatest {
                while (true) {
                    refreshPractice()
                    delay(15_000)
                }
            }
        }
        viewModelScope.launch {
            AudioService.sessionLogged.collect {
                refreshPractice()
                sessionEvents.send(practice.value.first.todayMin)
            }
        }
        viewModelScope.launch {
            repository.settings.collect { settings ->
                NativeEngine.nativeSetDelayMs(settings.delayMs)
                NativeEngine.nativeSetSensitivity(settings.sensitivity)
                NativeEngine.nativeSetGateEnabled(settings.gateEnabled)
                NativeEngine.nativeSetPitchSemitones(settings.pitchSemitones)
                NativeEngine.nativeSetOutputGainDb(settings.outputGainDb)
                NativeEngine.nativeSetMaskingDb(settings.engineMaskingDb)
                NativeEngine.nativeSetNoiseReduction(settings.noiseReduction)
            }
        }
        viewModelScope.launch {
            var peakDb = -90f
            var heardSpeech = false
            while (true) {
                if (AudioService.running.value) {
                    val raw = NativeEngine.nativeGetLatencyMs()
                    val inputDb = NativeEngine.nativeGetInputRmsDb()
                    val speech = NativeEngine.nativeGetSpeechDetected()
                    peakDb = maxOf(peakDb, inputDb)
                    heardSpeech = heardSpeech || speech
                    val elapsedMs = SystemClock.elapsedRealtime() - AudioService.startedAtMs.value
                    _telemetry.value = Telemetry(
                        latencyMs = if (raw >= 0f) smoothLatency(_telemetry.value.latencyMs, raw) else null,
                        inputDb = inputDb,
                        floorDb = NativeEngine.nativeGetFloorDb(),
                        gateOpen = NativeEngine.nativeGetGateOpen(),
                        speechDetected = speech,
                        elapsedSec = (elapsedMs / 1000).toInt(),
                        quietMic = quietMic(elapsedMs, peakDb, heardSpeech),
                    )
                    delay(100)
                } else {
                    peakDb = -90f
                    heardSpeech = false
                    _telemetry.value = Telemetry()
                    delay(250)
                }
            }
        }
    }

    private suspend fun refreshPractice() {
        val lines = practiceLog.readLines()
        val today = LocalDate.now()
        practice.value = practiceStats(lines, today) to minutesByDay(lines, today)
    }

    private val _calibrating = MutableStateFlow(false)
    /** True during the 3 s room-noise measurement. */
    val calibrating: StateFlow<Boolean> = _calibrating

    /** Measures the room noise for 3 s while running and sets the sensitivity from it. */
    fun calibrateSensitivity() {
        if (_calibrating.value || !AudioService.running.value) return
        _calibrating.value = true
        viewModelScope.launch {
            var peakDb = -90f
            repeat(30) {
                peakDb = maxOf(peakDb, NativeEngine.nativeGetInputRmsDb())
                delay(100)
            }
            if (AudioService.running.value) {
                repository.setSensitivity(suggestSensitivity(peakDb, NativeEngine.nativeGetFloorDb()))
            }
            _calibrating.value = false
        }
    }

    /** The practice log as CSV text, for the share sheet. */
    suspend fun practiceCsv(): String = (listOf("date,seconds") + practiceLog.readLines()).joinToString("\n")

    fun setNoiseReduction(enabled: Boolean) {
        viewModelScope.launch { repository.setNoiseReduction(enabled) }
    }

    fun setWidgetStyle(style: WidgetStyle) {
        viewModelScope.launch { repository.setWidgetStyle(style) }
    }

    fun setMaskingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setMaskingEnabled(enabled) }
    }

    fun setMaskingDb(db: Int) {
        viewModelScope.launch { repository.setMaskingDb(db) }
    }

    fun setSessionLimitMin(min: Int) {
        viewModelScope.launch { repository.setSessionLimitMin(min) }
    }

    fun savePreset(slot: Int) {
        viewModelScope.launch { repository.savePreset(slot) }
    }

    fun applyPreset(slot: Int) {
        viewModelScope.launch { repository.applyPreset(slot) }
    }

    fun setDailyGoalMin(min: Int) {
        viewModelScope.launch { repository.setDailyGoalMin(min) }
    }

    fun clearPracticeHistory() {
        viewModelScope.launch {
            practiceLog.clear()
            refreshPractice()
            Widgets.refresh(getApplication())
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(enabled) }
    }

    fun setReminderMinute(minute: Int) {
        viewModelScope.launch { repository.setReminderMinute(minute) }
    }

    fun setDelayMs(ms: Int) {
        viewModelScope.launch { repository.setDelayMs(ms) }
    }

    fun setSensitivity(level: Int) {
        viewModelScope.launch { repository.setSensitivity(level) }
    }

    fun setGateEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setGateEnabled(enabled) }
    }

    fun setPitchSemitones(semitones: Int) {
        viewModelScope.launch { repository.setPitchSemitones(semitones) }
    }

    fun setOutputGainDb(db: Int) {
        viewModelScope.launch { repository.setOutputGainDb(db) }
    }

    fun acceptDisclaimer() {
        viewModelScope.launch { repository.setDisclaimerAccepted() }
    }

    fun setFadeEnabled(enabled: Boolean) = fade { repository.setFadeEnabled(enabled) }

    fun setFadeStartMs(ms: Int) = fade { repository.setFadeStartMs(ms) }

    fun setFadeStepMs(ms: Int) = fade { repository.setFadeStepMs(ms) }

    fun setFadeTargetMs(ms: Int) = fade { repository.setFadeTargetMs(ms) }

    fun setFadeIntervalDays(days: Int) = fade { repository.setFadeIntervalDays(days) }

    fun setFadePaused(paused: Boolean) = fade { repository.setFadePaused(paused) }

    fun stepFadeBack() = fade { repository.stepFadeBack() }

    fun deletePreset(slot: Int) {
        viewModelScope.launch { repository.deletePreset(slot) }
    }

    fun setPresetName(slot: Int, name: String) {
        viewModelScope.launch { repository.setPresetName(slot, name) }
    }

    private fun fade(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            repository.applyFadeProgram()
        }
    }

    fun setMicPreset(index: Int) {
        viewModelScope.launch { repository.setMicPreset(index) }
    }

    fun start() {
        val headset = headsetMonitor.state.value
        val outputId = headset.wiredOutputId ?: return
        viewModelScope.launch {
            val preset = repository.settings.first().micPreset
            AudioService.start(
                getApplication(), preset, headset.headsetInputId ?: 0, outputId, headset.headsetInputIsBluetooth
            )
        }
    }

    fun stop() {
        AudioService.stop(getApplication())
    }

    override fun onCleared() {
        headsetMonitor.close()
        super.onCleared()
    }
}
