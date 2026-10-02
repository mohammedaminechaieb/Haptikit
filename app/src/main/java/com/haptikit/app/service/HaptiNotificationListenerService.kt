package com.haptikit.app.service

import android.Manifest
import android.app.Notification
import android.app.Person
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.haptikit.app.data.AppDatabase
import com.haptikit.app.data.AssignmentEntity
import com.haptikit.app.data.HaptiDao
import com.haptikit.app.data.HaptiPrefs
import com.haptikit.app.vibration.VibrationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fires whenever a notification is posted. If the sender (a contact) or
 * the posting app has a custom pattern assigned, play it.
 *
 * Contact matching, in order of reliability:
 *   1. Person URIs attached by messaging apps (tel:, mailto:, or a contacts
 *      lookup URI) → resolved to the contact's lookup key.
 *   2. The sender's display name (MessagingStyle sender / notification
 *      title) compared with assigned contacts' names.
 *
 * Android doesn't let third-party apps silence another app's own
 * vibration, so the stock buzz may still happen too — HaptiKit plays its
 * pattern right after, which is what makes the sender recognisable.
 */
class HaptiNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _connected = MutableStateFlow(false)
        /** Whether the system currently has this listener bound (access granted + running). */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        /** Don't buzz again for the same app within this window (message bursts). */
        private const val COOLDOWN_MS = 2_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastPlayed = HashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        _connected.value = false
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        _connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        // Progress bars, music players, navigation… update constantly — never buzz for those.
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // Silent updates to an existing notification (e.g. edited message).
        if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && System.currentTimeMillis() - sbn.postTime > 1000) return

        val prefs = HaptiPrefs(applicationContext)
        if (!prefs.enabled) return
        if (prefs.respectSilent) {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        }

        val now = System.currentTimeMillis()
        synchronized(lastPlayed) {
            if (now - (lastPlayed[sbn.packageName] ?: 0L) < COOLDOWN_MS) return
        }

        scope.launch {
            val dao = AppDatabase.get(applicationContext).dao()
            val assignment = findContactAssignment(dao, n) ?: dao.getAssignment(sbn.packageName) ?: return@launch
            val pattern = dao.getPattern(assignment.patternId) ?: return@launch
            synchronized(lastPlayed) { lastPlayed[sbn.packageName] = System.currentTimeMillis() }
            // Let the stock vibration finish first so the two don't blur together.
            kotlinx.coroutines.delay(350)
            VibrationEngine(applicationContext).play(pattern)
        }
    }

    private suspend fun findContactAssignment(dao: HaptiDao, n: Notification): AssignmentEntity? {
        val extras = n.extras
        val people = mutableListOf<Person>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)?.let { people += it }
            // MessagingStyle: the sender of the newest message.
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull()?.let { bundle ->
                (bundle as? android.os.Bundle)?.getParcelable<Person>("sender_person")?.let { people += it }
            }
        }

        // 1. URIs → lookup keys.
        for (person in people) {
            val key = person.uri?.let { lookupKeyFor(it) } ?: continue
            dao.getAssignment(key)?.let { return it }
        }

        // 2. Names.
        val names = buildList {
            people.mapNotNullTo(this) { it.name?.toString() }
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_TITLE)?.let { add(it.toString()) }
        }
        for (name in names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            dao.getContactAssignmentByName(name)?.let { return it }
        }
        return null
    }

    /** Resolves a Person URI (tel:, mailto:, or contacts lookup URI) to a contact lookup key. */
    private fun lookupKeyFor(raw: String): String? {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.parse(raw)
        val queryUri = when (uri.scheme) {
            "tel" -> Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(uri.schemeSpecificPart))
            "mailto" -> Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI, Uri.encode(uri.schemeSpecificPart))
            "content" -> {
                // content://com.android.contacts/contacts/lookup/<key>/<id>
                val segments = uri.pathSegments
                val i = segments.indexOf("lookup")
                if (i >= 0 && i + 1 < segments.size) return segments[i + 1]
                uri
            }
            else -> return null
        }
        return runCatching {
            contentResolver.query(queryUri, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit
}
