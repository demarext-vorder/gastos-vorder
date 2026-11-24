package com.vorder.gastos

import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

val resumeTick = mutableIntStateOf(0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
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
