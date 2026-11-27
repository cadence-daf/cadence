package com.cadence.daf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal const val ACTION_START_PRACTICE = "com.cadence.daf.action.START_PRACTICE"

/** True after the "Start practice" launcher shortcut was used; Main starts practice and clears it. */
internal val startRequested = MutableStateFlow(false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) noteStartRequest(intent)
        val lightMode = SettingsRepository(applicationContext).settings.map { it.lightMode }.distinctUntilChanged()
        setContent {
            // Nothing is drawn until the saved theme is known, so light mode never flashes dark first.
            val light = lightMode.collectAsState(initial = null).value ?: return@setContent
            CadenceTheme(light) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CadenceApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        noteStartRequest(intent)
    }

    // A request that could not start (no headphones) must not fire later by surprise.
    override fun onStop() {
        super.onStop()
        startRequested.value = false
    }

    private fun noteStartRequest(intent: Intent?) {
        if (intent?.action == ACTION_START_PRACTICE) startRequested.value = true
    }
}
