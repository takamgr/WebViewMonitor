package com.example.webviewmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenReceiver(
    private val onScreenOff: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            onScreenOff()
        }
    }
}
