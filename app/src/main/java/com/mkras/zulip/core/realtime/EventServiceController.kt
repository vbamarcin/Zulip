package com.mkras.zulip.core.realtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object EventServiceController {

    fun start(context: Context) {
        val intent = Intent(context, ZulipEventService::class.java).apply {
            action = ZulipEventService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, ZulipEventService::class.java).apply {
            action = ZulipEventService.ACTION_STOP
        }
        context.stopService(intent)
    }
}
