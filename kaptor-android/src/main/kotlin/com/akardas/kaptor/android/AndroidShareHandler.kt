package com.akardas.kaptor.android

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.akardas.kaptor.ui.KaptorShareHandler
import java.io.File

/**
 * Android implementation of [KaptorShareHandler] using the system share sheet. Files are shared
 * through a [FileProvider] declared in this module's manifest (authority
 * `${applicationId}.kaptor.fileprovider`).
 */
class AndroidShareHandler(context: Context) : KaptorShareHandler {

    private val appContext = context.applicationContext

    override fun shareText(text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launch(intent)
    }

    override fun shareFile(fileName: String, text: String, mimeType: String) {
        val dir = File(appContext.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.kaptor.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(intent)
    }

    private fun launch(intent: Intent) {
        val chooser = Intent.createChooser(intent, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(chooser)
    }
}
