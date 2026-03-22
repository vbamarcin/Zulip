package com.mkras.zulip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mkras.zulip.core.realtime.ZulipNotificationHelper
import com.mkras.zulip.presentation.navigation.ZulipRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var notificationTarget by mutableStateOf<NotificationNavigationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        notificationTarget = parseNotificationTarget(intent)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ZulipRoot(
                    notificationTarget = notificationTarget,
                    onNotificationTargetConsumed = { notificationTarget = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget = parseNotificationTarget(intent)
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun parseNotificationTarget(intent: android.content.Intent?): NotificationNavigationTarget? {
        intent ?: return null
        val messageId = intent.getLongExtra(ZulipNotificationHelper.EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0L) return null

        val messageType = intent.getStringExtra(ZulipNotificationHelper.EXTRA_MESSAGE_TYPE).orEmpty()
        val streamName = intent.getStringExtra(ZulipNotificationHelper.EXTRA_STREAM_NAME)
        val topic = intent.getStringExtra(ZulipNotificationHelper.EXTRA_TOPIC)
        val conversationKey = intent.getStringExtra(ZulipNotificationHelper.EXTRA_CONVERSATION_KEY)
        val conversationTitle = intent.getStringExtra(ZulipNotificationHelper.EXTRA_CONVERSATION_TITLE)

        return NotificationNavigationTarget(
            messageId = messageId,
            messageType = messageType,
            streamName = streamName,
            topic = topic,
            conversationKey = conversationKey,
            conversationTitle = conversationTitle
        )
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).hide(
            WindowInsetsCompat.Type.navigationBars()
        )
    }
}

data class NotificationNavigationTarget(
    val messageId: Long,
    val messageType: String,
    val streamName: String?,
    val topic: String?,
    val conversationKey: String?,
    val conversationTitle: String?
)
