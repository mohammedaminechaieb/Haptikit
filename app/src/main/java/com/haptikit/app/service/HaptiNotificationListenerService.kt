package com.haptikit.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.haptikit.app.data.AppDatabase
import com.haptikit.app.vibration.VibrationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires whenever a notification is posted. We look up whether the posting
 * app (or, for messaging apps, the sender extracted from EXTRA_PEOPLE) has
 * a custom pattern assigned, and if so, play it instead of the system default.
 *
 * NOTE: the user must grant "Notification access" for this service in
 * system settings (Settings > Apps > Special access > Notification access).
 * That grant screen can't be triggered by a normal runtime permission
 * request — MainActivity deep-links to it instead.
 */
class HaptiNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return

        scope.launch {
            val dao = AppDatabase.get(applicationContext).dao()

            // 1. Try to match a specific contact if this looks like a messaging
            //    notification (has a person attached).
            val personKey = extractPersonKey(sbn.notification)
            val assignment = personKey?.let { dao.getAssignment(it) }
                ?: dao.getAssignment(packageName) // 2. fall back to per-app assignment

            val patternId = assignment?.patternId ?: return@launch
            val pattern = dao.getPattern(patternId) ?: return@launch

            VibrationEngine(applicationContext).play(pattern)
        }
    }

    /** Pulls a stable identifier for the sender out of MessagingStyle extras, if present. */
    private fun extractPersonKey(notification: Notification): String? {
        val extras = notification.extras
        val people = extras.getParcelableArray(Notification.EXTRA_PEOPLE_LIST)
        return people?.firstOrNull()?.toString()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit
}
