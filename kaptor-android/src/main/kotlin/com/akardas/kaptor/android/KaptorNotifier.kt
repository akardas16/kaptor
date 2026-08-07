package com.akardas.kaptor.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.akardas.kaptor.model.HttpTransaction
import com.akardas.kaptor.store.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Observes the [TransactionRepository] and keeps a notification up to date with the most recent
 * requests. Tapping the notification opens [KaptorActivity]. This is the Ktor analogue of
 * Chucker's notification.
 *
 * The host app is responsible for holding the `POST_NOTIFICATIONS` runtime permission on API 33+;
 * if it isn't granted the notification is silently skipped (the in-app UI still works).
 */
class KaptorNotifier(
    context: Context,
    private val repository: TransactionRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)
    private var job: Job? = null

    /** Starts observing and posting notifications. Idempotent. */
    fun start() {
        if (job?.isActive == true) return
        createChannel()
        job = scope.launch {
            repository.transactions.collectLatest { transactions ->
                showNotification(transactions)
            }
        }
    }

    /** Stops observing and cancels the coroutine scope. */
    fun stop() {
        job?.cancel()
        job = null
        manager.cancel(NOTIFICATION_ID)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kaptor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Captured HTTP traffic" }
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(transactions: List<HttpTransaction>) {
        if (!manager.areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kaptor)
            .setContentTitle("Kaptor")
            .setContentText("${transactions.size} request${if (transactions.size == 1) "" else "s"}")
            .setContentIntent(contentIntent())
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (transactions.isNotEmpty()) {
            val inbox = NotificationCompat.InboxStyle()
            transactions.take(MAX_LINES).forEach { inbox.addLine(it.notificationLine()) }
            builder.setStyle(inbox)
        }

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — ignore, the in-app UI is unaffected.
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(appContext, KaptorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(appContext, 0, intent, flags)
    }

    private fun HttpTransaction.notificationLine(): String =
        "$statusLabel ${method.orEmpty()} ${path.orEmpty()}".trim()

    private companion object {
        const val CHANNEL_ID = "ktor_inspector"
        const val NOTIFICATION_ID = 8_421
        const val MAX_LINES = 5
    }
}
