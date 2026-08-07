package com.akardas.kaptor.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akardas.kaptor.ui.KaptorTheme
import com.akardas.kaptor.ui.KaptorScreen
import com.akardas.kaptor.ui.LocalKaptorMockRequests
import com.akardas.kaptor.ui.LocalKaptorRequestRerunner
import com.akardas.kaptor.ui.LocalKaptorShareHandler

/**
 * Full-screen host for the Compose inspector UI. Launched from the inspector notification or via
 * [KaptorAndroid.launch]. Reads the repository from [KaptorRegistry].
 */
class KaptorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val shareHandler = remember { AndroidShareHandler(this) }
            KaptorTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val repository = KaptorRegistry.repository
                    if (repository != null) {
                        CompositionLocalProvider(
                            LocalKaptorShareHandler provides shareHandler,
                            LocalKaptorRequestRerunner provides KaptorRegistry.rerunner,
                            LocalKaptorMockRequests provides KaptorRegistry.mockRequests,
                        ) {
                            KaptorScreen(repository)
                        }
                    } else {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Kaptor is not initialised. Call KaptorAndroid.install(...) first.")
                        }
                    }
                }
            }
        }
    }
}
