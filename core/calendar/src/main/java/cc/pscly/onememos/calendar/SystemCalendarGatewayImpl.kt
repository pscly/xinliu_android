package cc.pscly.onememos.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.todoCalendarEventDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "todo_calendar_event_state",
)

private object TodoCalendarEventStateKeys {
    val EVENT_ENTRIES = stringSetPreferencesKey("todo_calendar_event_entries")
}

@Singleton
class SystemCalendarGatewayImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemCalendarGateway {
    override fun hasPermissions(): Boolean {
        val readGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        val writeGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    override suspend fun writableCalendars(): Result<List<WritableCalendar>> =
        runCatching {
            if (!hasPermissions()) return@runCatching emptyList()
            val cr = context.contentResolver
            val projection =
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.VISIBLE,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.IS_PRIMARY,
                )
            val result = mutableListOf<WritableCalendar>()
            cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )
                ?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                    val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    val accountIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                    val ownerIdx = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                    val visibleIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
                    val accessIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                    val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        if (id <= 0L) continue
                        val visible = cursor.getInt(visibleIdx)
                        if (visible != 1) continue
                        val accessLevel = cursor.getInt(accessIdx)
                        if (accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue

                        val displayName = cursor.getString(nameIdx)?.trim().orEmpty().ifBlank { "日历 $id" }
                        val accountName = cursor.getString(accountIdx)?.trim().orEmpty()
                        val ownerAccount = cursor.getString(ownerIdx)?.trim().orEmpty()
                        val subtitle =
                            when {
                                accountName.isNotBlank() && ownerAccount.isNotBlank() && accountName != ownerAccount ->
                                    "$accountName · $ownerAccount"
                                accountName.isNotBlank() -> accountName
                                ownerAccount.isNotBlank() -> ownerAccount
                                else -> "本地日历"
                            }
                        val isPrimary = cursor.getInt(primaryIdx) == 1
                        result +=
                            WritableCalendar(
                                id = id,
                                displayName = displayName,
                                subtitle = subtitle,
                                isPrimary = isPrimary,
                            )
                    }
                }

            result
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<WritableCalendar> { it.isPrimary }
                        .thenBy { it.displayName },
                )
        }

    override suspend fun calendarLabel(calendarId: Long): Result<String?> =
        runCatching {
            if (calendarId <= 0L) return@runCatching null
            if (!hasPermissions()) return@runCatching null
            val cr = context.contentResolver
            val projection =
                arrayOf(
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                )
            cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars._ID}=?",
                arrayOf(calendarId.toString()),
                null,
            )
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val name = cursor.getString(0)?.trim().orEmpty()
                    val account = cursor.getString(1)?.trim().orEmpty()
                    val owner = cursor.getString(2)?.trim().orEmpty()
                    val title = name.ifBlank { "日历 $calendarId" }
                    val subtitle =
                        when {
                            account.isNotBlank() && owner.isNotBlank() && account != owner -> "$account · $owner"
                            account.isNotBlank() -> account
                            owner.isNotBlank() -> owner
                            else -> ""
                        }
                    if (subtitle.isBlank()) title else "$title（$subtitle）"
                }
        }

    override suspend fun syncTodoEvents(request: CalendarSyncRequest): CalendarSyncResult {
        if (request.calendarId <= 0L || !hasPermissions()) {
            return CalendarSyncResult(writtenEventCount = 0, deletedEventCount = 0)
        }
        if (!isCalendarWritable(request.calendarId)) {
            Log.w(TAG, "日历不可写或不存在：calendarId=${request.calendarId}")
            return CalendarSyncResult(writtenEventCount = 0, deletedEventCount = 0)
        }

        val ownerKey = request.ownerKey
        val calendarId = request.calendarId
        val rawEntries = loadCalendarEventEntries()
        val parsedEntries = rawEntries.mapNotNull(::parseCalendarEventEntryKey)
        val ownerEntries = parsedEntries.filter { it.ownerKey == ownerKey }
        val ownerEntriesByItemId = ownerEntries.groupBy { it.itemId }
        val desiredItemIds = request.events.asSequence().map { it.itemId }.toSet()
        val newOwnerEntryKeys = linkedSetOf<String>()
        var written = 0

        request.events.forEach { spec ->
            val existingCandidates = ownerEntriesByItemId[spec.itemId].orEmpty()
            val existing =
                existingCandidates.firstOrNull { it.calendarId == calendarId }
                    ?: existingCandidates.firstOrNull()

            var effectiveCalendarId = calendarId
            val eventId =
                when {
                    existing == null -> insertCalendarEventSafe(calendarId = calendarId, spec = spec)
                    existing.calendarId != calendarId -> {
                        val inserted = insertCalendarEventSafe(calendarId = calendarId, spec = spec)
                        if (inserted != null && inserted > 0L) {
                            deleteCalendarEventSafe(existing.eventId)
                            inserted
                        } else {
                            effectiveCalendarId = existing.calendarId
                            existing.eventId
                        }
                    }
                    else -> {
                        val updated = updateCalendarEventSafe(eventId = existing.eventId, spec = spec)
                        if (updated) {
                            existing.eventId
                        } else {
                            val inserted = insertCalendarEventSafe(calendarId = calendarId, spec = spec)
                            if (inserted != null && inserted > 0L) {
                                deleteCalendarEventSafe(existing.eventId)
                                inserted
                            } else {
                                existing.eventId
                            }
                        }
                    }
                }

            if (eventId != null && eventId > 0L) {
                if (request.syncReminders) {
                    replaceCalendarRemindersSafe(eventId = eventId, minutes = spec.reminderMinutes)
                } else {
                    clearCalendarRemindersSafe(eventId = eventId)
                }
                written += 1
                newOwnerEntryKeys +=
                    calendarEventEntryKey(
                        ownerKey = ownerKey,
                        itemId = spec.itemId,
                        calendarId = effectiveCalendarId,
                        eventId = eventId,
                    )
            }
        }

        var deleted = 0
        ownerEntries
            .filter { it.itemId !in desiredItemIds }
            .forEach { entry ->
                deleteCalendarEventSafe(entry.eventId)
                deleted += 1
            }

        val merged = rawEntries.toMutableSet()
        merged.removeAll { parseCalendarEventEntryKey(it)?.ownerKey == ownerKey }
        merged.addAll(newOwnerEntryKeys)
        saveCalendarEventEntries(merged)

        return CalendarSyncResult(writtenEventCount = written, deletedEventCount = deleted)
    }

    private data class CalendarEventEntry(
        val ownerKey: String,
        val itemId: String,
        val calendarId: Long,
        val eventId: Long,
    )

    private fun calendarEventEntryKey(
        ownerKey: String,
        itemId: String,
        calendarId: Long,
        eventId: Long,
    ): String = "${ownerKey.trim()}|${itemId.trim()}|${calendarId.coerceAtLeast(0)}|${eventId.coerceAtLeast(0)}"

    private fun parseCalendarEventEntryKey(raw: String): CalendarEventEntry? {
        val t = raw.trim()
        if (t.isBlank()) return null
        val parts = t.split("|")
        if (parts.size != 4) return null
        val ownerKey = parts[0].trim()
        val itemId = parts[1].trim()
        val calendarId = parts[2].trim().toLongOrNull() ?: return null
        val eventId = parts[3].trim().toLongOrNull() ?: return null
        if (ownerKey.isBlank() || itemId.isBlank()) return null
        if (calendarId <= 0L || eventId <= 0L) return null
        return CalendarEventEntry(
            ownerKey = ownerKey,
            itemId = itemId,
            calendarId = calendarId,
            eventId = eventId,
        )
    }

    private suspend fun loadCalendarEventEntries(): Set<String> =
        runCatching {
            context.todoCalendarEventDataStore.data.first()[TodoCalendarEventStateKeys.EVENT_ENTRIES]
        }.getOrNull() ?: emptySet()

    private suspend fun saveCalendarEventEntries(entries: Set<String>) {
        context.todoCalendarEventDataStore.edit { prefs ->
            if (entries.isEmpty()) {
                prefs.remove(TodoCalendarEventStateKeys.EVENT_ENTRIES)
            } else {
                prefs[TodoCalendarEventStateKeys.EVENT_ENTRIES] = entries
            }
        }
    }

    private fun isCalendarWritable(calendarId: Long): Boolean {
        if (calendarId <= 0L) return false
        return runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
            val projection =
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.VISIBLE,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                )
            context.contentResolver
                .query(uri, projection, null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use false
                    val visible = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE))
                    val access = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL))
                    visible == 1 && access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                }
                ?: false
        }.getOrElse { false }
    }

    private fun insertCalendarEventSafe(
        calendarId: Long,
        spec: CalendarEventSpec,
    ): Long? =
        runCatching {
            val values =
                ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, spec.title)
                    put(CalendarContract.Events.DESCRIPTION, spec.description)
                    put(CalendarContract.Events.DTSTART, spec.startAtMs)
                    put(CalendarContract.Events.DTEND, spec.endAtMs)
                    put(CalendarContract.Events.EVENT_TIMEZONE, spec.timezoneId)
                    put(CalendarContract.Events.HAS_ALARM, if (spec.reminderMinutes.isEmpty()) 0 else 1)
                }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return@runCatching null
            ContentUris.parseId(uri).takeIf { it > 0L }
        }
            .onFailure { Log.w(TAG, "写入日历事件失败（insert）", it) }
            .getOrNull()

    private fun updateCalendarEventSafe(
        eventId: Long,
        spec: CalendarEventSpec,
    ): Boolean {
        if (eventId <= 0L) return false
        return runCatching {
            val values =
                ContentValues().apply {
                    put(CalendarContract.Events.TITLE, spec.title)
                    put(CalendarContract.Events.DESCRIPTION, spec.description)
                    put(CalendarContract.Events.DTSTART, spec.startAtMs)
                    put(CalendarContract.Events.DTEND, spec.endAtMs)
                    put(CalendarContract.Events.EVENT_TIMEZONE, spec.timezoneId)
                    put(CalendarContract.Events.HAS_ALARM, if (spec.reminderMinutes.isEmpty()) 0 else 1)
                }
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.update(uri, values, null, null) > 0
        }
            .onFailure { Log.w(TAG, "写入日历事件失败（update）", it) }
            .getOrElse { false }
    }

    private fun deleteCalendarEventSafe(eventId: Long) {
        if (eventId <= 0L) return
        runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null)
        }.onFailure { Log.w(TAG, "删除日历事件失败（delete）", it) }
    }

    private fun clearCalendarRemindersSafe(eventId: Long) {
        if (eventId <= 0L) return
        runCatching {
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID}=?",
                arrayOf(eventId.toString()),
            )
        }.onFailure { Log.w(TAG, "清理日历提醒失败", it) }
    }

    private fun replaceCalendarRemindersSafe(
        eventId: Long,
        minutes: List<Int>,
    ) {
        if (eventId <= 0L) return
        runCatching {
            clearCalendarRemindersSafe(eventId)
            minutes.asSequence().map { it.coerceAtLeast(0) }.distinct().forEach { m ->
                val values =
                    ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.MINUTES, m)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
            }
        }.onFailure { Log.w(TAG, "写入日历提醒失败", it) }
    }

    private companion object {
        const val TAG = "SystemCalendarGateway"
    }
}
