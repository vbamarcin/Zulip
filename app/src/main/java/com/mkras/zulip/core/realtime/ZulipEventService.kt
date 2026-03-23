package com.mkras.zulip.core.realtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ZulipEventService : Service() {

    @Inject
    lateinit var notificationHelper: ZulipNotificationHelper

    @Inject
    lateinit var eventQueueManager: EventQueueManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelfSafely()
            ACTION_START, null -> startPollingIfNeeded()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPollingIfNeeded() {
        notificationHelper.ensureChannels()
        startForeground(
            ZulipNotificationHelper.FOREGROUND_NOTIFICATION_ID,
            notificationHelper.buildForegroundNotification()
        )

        if (pollingStarted) {
            return
        }

        pollingStarted = true
        serviceScope.launch {
            eventQueueManager.runLoop()
        }
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.mkras.zulip.action.START_EVENTS"
        const val ACTION_STOP = "com.mkras.zulip.action.STOP_EVENTS"
    }
}
