package com.cadence.daf

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AudioService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Not cancelled by shutDown, so the practice line is always written completely.
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var callLinkOn = false
    private var delayMs = Settings().delayMs
    private var fadeEnabled = false
    private var focusRequest: AudioFocusRequest? = null
    private val practiceLog by lazy { PracticeLogStore(this) }
    private val repository by lazy { SettingsRepository(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAudio(intent)
            ACTION_QUICK_START -> quickStart()
            ACTION_STOP -> shutDown()
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_NEXT_DELAY -> scope.launch { repository.setDelayMs(nextDelayPreset(repository.settings.first().delayMs)) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        shutDown()
    }

    private fun startAudio(intent: Intent) {
        if (_running.value) return
        _lastError.value = null
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        if (!requestFocus()) {
            _lastError.value = getString(R.string.start_blocked_call)
            shutDown()
            return
        }
        val outputId = (if (intent.getBooleanExtra(EXTRA_CALL_LINK, false)) startCallLink() else null)
            ?: intent.getIntExtra(EXTRA_OUTPUT_ID, 0)
        val code = NativeEngine.nativeStart(
            intent.getIntExtra(EXTRA_PRESET, 0),
            intent.getIntExtra(EXTRA_INPUT_ID, 0),
            outputId
        )
        if (code != 0) {
            _lastError.value = getString(R.string.start_failed, code)
            shutDown()
            return
        }
        _running.value = true
        _startedAtMs.value = SystemClock.elapsedRealtime()
        _muted.value = false
        scope.launch {
            repository.applyFadeProgram()
            repository.settings.collect { settings ->
                NativeEngine.nativeSetDelayMs(settings.delayMs)
                NativeEngine.nativeSetSensitivity(settings.sensitivity)
                NativeEngine.nativeSetGateEnabled(settings.gateEnabled)
                NativeEngine.nativeSetPitchSemitones(settings.pitchSemitones)
                NativeEngine.nativeSetOutputGainDb(settings.outputGainDb)
                NativeEngine.nativeSetMaskingDb(settings.engineMaskingDb)
                NativeEngine.nativeSetNoiseReduction(settings.noiseReduction)
                delayMs = settings.delayMs
                fadeEnabled = settings.fadeEnabled
                refreshNotification()
            }
        }
        scope.launch {
            // Auto-stop: the limit is read once, at Start.
            val limitMin = repository.settings.first().sessionLimitMin
            if (limitMin > 0) {
                delay(limitMin * 60_000L)
                shutDown()
            }
        }
        scope.launch {
            while (true) {
                delay(250)
                // Usually the headset was unplugged: stop quietly.
                if (NativeEngine.nativeGetState() == 2) {
                    shutDown()
                    return@launch
                }
            }
        }
    }

    // Start from a widget, with no screen open: the checks the Start button makes, answered with a Toast.
    private fun quickStart() {
        if (_running.value) return
        val headset = HeadsetMonitor(this).run { close(); state.value }
        val outputId = headset.wiredOutputId
        val allowed = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!allowed || outputId == null) {
            toast(getString(if (!allowed) R.string.mic_denied else R.string.plug_in_to_start))
            // Redraw, so that without the permission the next tap opens the app and asks for it.
            Widgets.refresh(this)
            stopSelf()
            return
        }
        // One small preferences read; blocking keeps startForeground inside this call.
        val preset = runBlocking { repository.settings.first().micPreset }
        startAudio(startIntent(this, preset, headset.headsetInputId ?: 0, outputId, headset.headsetInputIsBluetooth))
        _lastError.value?.let(::toast)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    // A call or another app taking the sound stops practice, like unplugging does.
    private fun requestFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { if (stopsOnFocusChange(it)) shutDown() }
            .build()
        focusRequest = request
        return getSystemService(AudioManager::class.java).requestAudioFocus(request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun toggleMute() {
        if (!_running.value) return
        _muted.value = !_muted.value
        NativeEngine.nativeSetMuted(_muted.value)
        refreshNotification()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        Widgets.refresh(this)
    }

    private fun actionIntent(action: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode, Intent(this, AudioService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE
    )

    // A Bluetooth headset mic only delivers audio while the call link (SCO) is on.
    // shortcut: Android 8 to 11 need startBluetoothSco, not done here, so the earbud mic stays silent there.
    // Returns the SCO output device id: while the link is on, the A2DP output is silent.
    private fun startCallLink(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val audio = getSystemService(AudioManager::class.java)
        val device = audio.availableCommunicationDevices
            .firstOrNull { it.type == HeadsetLogic.TYPE_BLUETOOTH_SCO } ?: return null
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        callLinkOn = audio.setCommunicationDevice(device)
        if (!callLinkOn) audio.mode = AudioManager.MODE_NORMAL
        return if (callLinkOn) device.id else null
    }

    private fun stopCallLink() {
        if (!callLinkOn) return
        callLinkOn = false
        val audio = getSystemService(AudioManager::class.java)
        audio.clearCommunicationDevice()
        audio.mode = AudioManager.MODE_NORMAL
    }

    // Called from the main thread and from the state poll: synchronized so a session is logged once.
    @Synchronized
    private fun shutDown() {
        scope.coroutineContext.cancelChildren()
        NativeEngine.nativeStop()
        stopCallLink()
        abandonFocus()
        if (_running.value) logPractice()
        _running.value = false
        Widgets.refresh(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun logPractice() {
        val seconds = (SystemClock.elapsedRealtime() - _startedAtMs.value) / 1000
        if (seconds < 5) return
        val line = "${LocalDate.now()},$seconds"
        logScope.launch {
            withContext(NonCancellable + Dispatchers.IO) { practiceLog.append(line) }
            Widgets.refresh(applicationContext)
            _sessionLogged.emit(seconds)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setColor(ContextCompat.getColor(this, R.color.brand_teal))
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), actionIntent(ACTION_STOP, 0))
            .addAction(0, getString(if (_muted.value) R.string.unmute else R.string.mute), actionIntent(ACTION_TOGGLE_MUTE, 1))
            .apply {
                // The fading program sets the delay, so a preset button would be overridden on the next start.
                if (!fadeEnabled) addAction(0, getString(R.string.delay_action, delayMs), actionIntent(ACTION_NEXT_DELAY, 2))
            }
            .build()
    }

    companion object {
        private const val ACTION_START = "com.cadence.daf.action.START"
        private const val ACTION_QUICK_START = "com.cadence.daf.action.QUICK_START"
        private const val ACTION_STOP = "com.cadence.daf.action.STOP"
        private const val ACTION_TOGGLE_MUTE = "com.cadence.daf.action.TOGGLE_MUTE"
        private const val ACTION_NEXT_DELAY = "com.cadence.daf.action.NEXT_DELAY"
        private const val EXTRA_PRESET = "preset"
        private const val EXTRA_INPUT_ID = "inputId"
        private const val EXTRA_OUTPUT_ID = "outputId"
        private const val EXTRA_CALL_LINK = "callLink"
        private const val CHANNEL_ID = "cadence_running"
        private const val NOTIFICATION_ID = 1

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _startedAtMs = MutableStateFlow(0L)
        /** elapsedRealtime when the current session started. */
        val startedAtMs: StateFlow<Long> = _startedAtMs.asStateFlow()

        private val _sessionLogged = MutableSharedFlow<Long>()
        /** Seconds of a session, emitted after its practice line is written. */
        val sessionLogged: SharedFlow<Long> = _sessionLogged.asSharedFlow()

        private val _muted = MutableStateFlow(false)
        val muted: StateFlow<Boolean> = _muted.asStateFlow()

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        fun start(context: Context, preset: Int, inputId: Int, outputId: Int, callLink: Boolean) {
            ContextCompat.startForegroundService(context, startIntent(context, preset, inputId, outputId, callLink))
        }

        private fun startIntent(context: Context, preset: Int, inputId: Int, outputId: Int, callLink: Boolean) =
            Intent(context, AudioService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PRESET, preset)
                .putExtra(EXTRA_INPUT_ID, inputId)
                .putExtra(EXTRA_OUTPUT_ID, outputId)
                .putExtra(EXTRA_CALL_LINK, callLink)

        fun quickStartIntent(context: Context): Intent =
            Intent(context, AudioService::class.java).setAction(ACTION_QUICK_START)

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        fun stopIntent(context: Context): Intent = Intent(context, AudioService::class.java).setAction(ACTION_STOP)
    }
}
