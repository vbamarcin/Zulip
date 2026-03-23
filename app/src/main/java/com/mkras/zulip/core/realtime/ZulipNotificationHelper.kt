package com.mkras.zulip.core.realtime

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.text.HtmlCompat
import androidx.core.content.ContextCompat
import com.mkras.zulip.MainActivity
import com.mkras.zulip.R
import com.mkras.zulip.core.chat.DmConversationKey
import com.mkras.zulip.data.remote.dto.EventMessageDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZulipNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Zulip Synchronizacja",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Utrzymuje połączenie long-polling z serwerem Zulip"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val messageChannel = NotificationChannel(
            CHANNEL_DM_MESSAGES,
            "Zulip DM",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Powiadomienia o nowych wiadomościach prywatnych"
            setShowBadge(true)
        }

        val streamChannel = NotificationChannel(
            CHANNEL_STREAM_MESSAGES,
            "Zulip Kanały",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Powiadomienia o nowych wiadomościach na kanałach"
            setShowBadge(true)
        }

        // Migrate older installs where channel had lower importance and no heads-up alerts.
        manager.getNotificationChannel(CHANNEL_STREAM_MESSAGES)?.let { existing ->
            if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                manager.deleteNotificationChannel(CHANNEL_STREAM_MESSAGES)
            }
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(messageChannel)
        manager.createNotificationChannel(streamChannel)
        reconcileGroupSummaries()
    }

    fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ikona)
            .setContentTitle("Zulip Unofficial")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(mainPendingIntent())
            .build()
    }

    fun showMessageNotification(message: EventMessageDto, selfEmail: String) {
        if (!canPostNotifications()) {
            return
        }

        val title = message.senderFullName ?: message.senderEmail ?: "Nowa wiadomość"
        val plainContent = HtmlCompat.fromHtml(
            message.content.orEmpty(),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString().trim().ifBlank { "Nowa wiadomość" }

        val isPrivate = message.type == "private"
        val channelId = if (isPrivate) CHANNEL_DM_MESSAGES else CHANNEL_STREAM_MESSAGES
        val groupKey = if (isPrivate) GROUP_DM else GROUP_STREAM

        val subText = if (isPrivate) {
            "Wiadomość prywatna"
        } else {
            val streamName = message.displayRecipient as? String
            val topic = message.subject
            when {
                !streamName.isNullOrBlank() && !topic.isNullOrBlank() -> "#$streamName > $topic"
                !streamName.isNullOrBlank() -> "#$streamName"
                else -> "Kanał"
            }
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ikona)
            .setContentTitle(title)
            .setSubText(subText)
            .setContentText(plainContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(plainContent))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(groupKey)
            // Keep child notifications at 1 to avoid launcher badge overcounting (e.g. 1+2+3+1).
            .setNumber(1)
            .setContentIntent(mainPendingIntent(message, selfEmail))
            .build()

        notificationManager.notify((message.id and 0x7FFFFFFF).toInt(), notification)
        updateGroupSummary(channelId = channelId, groupKey = groupKey, isPrivate = isPrivate)
    }

    fun onMessagesRead(ids: List<Long>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        val readIds = ids.toSet()
        if (readIds.isEmpty()) {
            return
        }

        systemNotificationManager.activeNotifications
            .filter { it.id.toLong() in readIds }
            .forEach { status ->
                notificationManager.cancel(status.id)
            }

        reconcileGroupSummaries()
    }

    fun onMessagesDeleted(ids: List<Long>) {
        onMessagesRead(ids)
    }

    fun reconcileGroupSummaries() {
        updateGroupSummary(channelId = CHANNEL_DM_MESSAGES, groupKey = GROUP_DM, isPrivate = true)
        updateGroupSummary(channelId = CHANNEL_STREAM_MESSAGES, groupKey = GROUP_STREAM, isPrivate = false)
    }

    private fun updateGroupSummary(channelId: String, groupKey: String, isPrivate: Boolean) {
        val count = activeNotificationCount(groupKey)
        if (count <= 0) {
            notificationManager.cancel(summaryNotificationId(groupKey))
            return
        }

        val title = if (isPrivate) {
            if (count == 1) "1 nowa wiadomość prywatna" else "$count nowych wiadomości prywatnych"
        } else {
            if (count == 1) "1 nowa wiadomość kanałowa" else "$count nowych wiadomości kanałowych"
        }

        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ikona)
            .setContentTitle(title)
            .setContentText("Otwórz aplikację, aby zobaczyć szczegóły")
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setNumber(count)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent())
            .build()

        notificationManager.notify(summaryNotificationId(groupKey), summary)
    }

    private fun activeNotificationCount(groupKey: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return 0
        }

        return systemNotificationManager.activeNotifications.count { statusBarNotification ->
            val notification = statusBarNotification.notification
            notification.group == groupKey &&
                (notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }
    }

    private fun summaryNotificationId(groupKey: String): Int {
        return when (groupKey) {
            GROUP_DM -> SUMMARY_DM
            else -> SUMMARY_STREAM
        }
    }

    private fun canPostNotifications(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, pendingFlags)
    }

    private fun mainPendingIntent(message: EventMessageDto, selfEmail: String): PendingIntent {
        val messageType = message.type.orEmpty()
        val recipients = parsePrivateRecipients(message.displayRecipient)
        val conversationKey = if (messageType == "private") {
            DmConversationKey.fromRecipientMaps(recipients, message.senderEmail.orEmpty(), selfEmail)
        } else {
            null
        }
        val conversationTitle = if (messageType == "private") {
            buildDmDisplayName(recipients, selfEmail, message.senderFullName.orEmpty())
        } else {
            null
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_ID, message.id)
            putExtra(EXTRA_MESSAGE_TYPE, messageType)
            putExtra(EXTRA_STREAM_NAME, if (messageType == "stream") message.displayRecipient?.toString() else null)
            putExtra(EXTRA_TOPIC, message.subject.orEmpty())
            putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
            putExtra(EXTRA_CONVERSATION_TITLE, conversationTitle)
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, (message.id and 0x7FFFFFFF).toInt(), intent, pendingFlags)
    }

    private fun parsePrivateRecipients(displayRecipient: Any?): List<Map<*, *>> {
        return (displayRecipient as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            .orEmpty()
    }

    private fun buildDmDisplayName(recipients: List<Map<*, *>>, selfEmail: String, fallback: String): String {
        val names = recipients
            .filter { (it["email"] as? String)?.equals(selfEmail, ignoreCase = true) == false }
            .mapNotNull { it["full_name"] as? String }
        return names.joinToString(", ").ifBlank { fallback }
    }

    companion object {
        const val CHANNEL_SERVICE = "zulip_sync_service"
        const val CHANNEL_DM_MESSAGES = "zulip_dm_messages"
        const val CHANNEL_STREAM_MESSAGES = "zulip_stream_messages"
        const val FOREGROUND_NOTIFICATION_ID = 1101
        const val GROUP_DM = "zulip_group_dm"
        const val GROUP_STREAM = "zulip_group_stream"
        const val SUMMARY_DM = 2101
        const val SUMMARY_STREAM = 2102
        const val EXTRA_MESSAGE_ID = "zulip_extra_message_id"
        const val EXTRA_MESSAGE_TYPE = "zulip_extra_message_type"
        const val EXTRA_STREAM_NAME = "zulip_extra_stream_name"
        const val EXTRA_TOPIC = "zulip_extra_topic"
        const val EXTRA_CONVERSATION_KEY = "zulip_extra_conversation_key"
        const val EXTRA_CONVERSATION_TITLE = "zulip_extra_conversation_title"
    }
}
