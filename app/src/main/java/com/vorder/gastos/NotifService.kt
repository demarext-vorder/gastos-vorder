package com.vorder.gastos

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: return
            if (pkg == packageName) return
            val n = sbn.notification ?: return
            val ctx = applicationContext
            Db.init(ctx)
            if (pkg !in Prefs.monitored(ctx)) return

            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val body = if (big.isNotBlank()) big else text
            val full = listOf(title, body).filter { it.isNotBlank() }.joinToString(" — ")
            if (full.isBlank()) return

            val r = Parser.parse(full) ?: return
            val hash = (pkg + full).hashCode().toString()
            if (Db.recentHash(hash, 120_000L)) return

            val label = Prefs.labelOf(ctx, pkg)
            Db.insert(
                Mov(0, sbn.postTime, r.monto, r.tipo, label, r.comercio, r.categoria, "", "auto", full),
                hash
            )
        } catch (e: Exception) {
            // Nunca romper el servicio por una notificación rara.
        }
    }
}
