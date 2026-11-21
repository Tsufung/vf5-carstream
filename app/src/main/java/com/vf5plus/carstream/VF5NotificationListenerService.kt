package com.vf5plus.carstream

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class VF5NotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName?.lowercase() ?: return

        // Filter navigation apps
        val isNavApp = pkg.contains("google.android.apps.maps") ||
                       pkg.contains("vietmap") ||
                       pkg.contains("grab") ||
                       pkg.contains("gsm") ||
                       pkg.contains("vsmart")

        if (!isNavApp) return

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val distance = if (subText.isNotEmpty()) subText else if (title.contains("m", true) || title.contains("km", true)) title else ""
        val road = if (text.isNotEmpty()) text else if (bigText.isNotEmpty()) bigText else title

        if (road.isNotEmpty() || distance.isNotEmpty()) {
            val combined = "$title $text $bigText".lowercase()
            val icon = when {
                combined.contains("phải") || combined.contains("right") -> "↱"
                combined.contains("trái") || combined.contains("left") -> "↰"
                combined.contains("quay đầu") || combined.contains("u-turn") -> "⮌"
                else -> "⬆"
            }

            SplitScreenPresentation.activeInstance?.updateTurnBanner(icon, distance, road)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
