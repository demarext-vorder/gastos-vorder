package com.vorder.gastos

import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf

val resumeTick = mutableIntStateOf(0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Db.init(applicationContext)
        setContent { AppTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue = resumeTick.intValue + 1
        try {
            NotificationListenerService.requestRebind(ComponentName(this, NotifService::class.java))
        } catch (e: Exception) {
            // sin permiso todavía
        }
    }
}
