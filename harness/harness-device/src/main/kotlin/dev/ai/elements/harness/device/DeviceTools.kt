package dev.ai.elements.harness.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Asks the user for runtime permissions (the app implements it with an Activity result launcher). */
fun interface PermissionGate {
    /** True when every permission in [permissions] is granted after asking. */
    suspend fun request(permissions: List<String>): Boolean
}

/** The device features [DeviceTools] can expose. */
enum class DeviceFeature { INFO, CLIPBOARD, CALENDAR, CONTACTS, LOCATION, ALARMS, NOTIFICATIONS }

/**
 * Device integrations as agent tools: device info, clipboard, calendar,
 * contacts, location, alarms / timers and notifications. Tools that change
 * something on the device ask the user first; tools that need a runtime
 * permission request it through [permissions] (the app must declare them in its
 * manifest — this library adds none).
 *
 * Times are ISO 8601 (`2026-09-27T09:00:00+08:00`); the device time zone applies when none is given.
 */
class DeviceTools(
    private val context: Context,
    private val permissions: PermissionGate,
    val features: Set<DeviceFeature> = DeviceFeature.entries.toSet(),
) : Capability {

    override val instructions: String
        get() = "You can use the user's device: ${features.joinToString { it.name.lowercase() }}. " +
            "Use ISO 8601 times; the device time zone is ${TimeZone.getDefault().id}."

    override suspend fun tools(): List<AgentTool> = buildList {
        if (DeviceFeature.INFO in features) add(tool("get_device_info", "Model, Android version, battery, language and time zone of the user's device.") { deviceInfo() })
        if (DeviceFeature.CLIPBOARD in features) {
            add(tool("read_clipboard", "Read the text on the user's clipboard.") { readClipboard() })
            add(tool("copy_to_clipboard", "Copy text to the user's clipboard.", "text" to str("The text to copy."), approval = true, required = listOf("text")) { a -> copy(a.s("text")!!) })
        }
        if (DeviceFeature.CALENDAR in features) {
            add(tool("list_calendar_events", "List calendar events between two times (default: the next 7 days).", "start" to str("ISO 8601 start."), "end" to str("ISO 8601 end.")) { a -> listEvents(a.s("start"), a.s("end")) })
            add(tool("create_calendar_event", "Add an event to the user's calendar.", "title" to str("Event title."), "start" to str("ISO 8601 start."), "end" to str("ISO 8601 end."), "location" to str("Optional location."), "description" to str("Optional notes."), approval = true, required = listOf("title", "start", "end")) { a ->
                createEvent(a.s("title")!!, a.s("start")!!, a.s("end")!!, a.s("location"), a.s("description"))
            })
        }
        if (DeviceFeature.CONTACTS in features) add(tool("search_contacts", "Find contacts by name, with their phone numbers and emails.", "query" to str("Name or part of it."), required = listOf("query")) { a -> searchContacts(a.s("query")!!) })
        if (DeviceFeature.LOCATION in features) add(tool("get_location", "The device's approximate current location (latitude, longitude, accuracy).") { location() })
        if (DeviceFeature.ALARMS in features) {
            add(tool("set_alarm", "Set an alarm in the clock app.", "hour" to int("0-23"), "minute" to int("0-59"), "label" to str("Optional label."), approval = true, required = listOf("hour", "minute")) { a -> setAlarm(a.i("hour")!!, a.i("minute")!!, a.s("label")) })
            add(tool("set_timer", "Start a countdown timer in the clock app.", "seconds" to int("Duration in seconds."), "label" to str("Optional label."), approval = true, required = listOf("seconds")) { a -> setTimer(a.i("seconds")!!, a.s("label")) })
        }
        if (DeviceFeature.NOTIFICATIONS in features) add(tool("post_notification", "Show a notification to the user.", "title" to str("Title."), "text" to str("Body."), required = listOf("title", "text")) { a -> notify(a.s("title")!!, a.s("text")!!) })
    }

    // --- Implementations ------------------------------------------------------------------------

    private fun deviceInfo(): String {
        val battery = context.getSystemService(BatteryManager::class.java)
        return buildJsonObject {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("android", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("battery_percent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            put("charging", battery.isCharging)
            put("language", Locale.getDefault().toLanguageTag())
            put("time_zone", TimeZone.getDefault().id)
            put("local_time", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }.toString()
    }

    private suspend fun readClipboard(): String = withContext(Dispatchers.Main) {
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: "(the clipboard is empty or not readable while the app is in the background)"
    }

    private suspend fun copy(text: String): String = withContext(Dispatchers.Main) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Agent", text))
        "Copied ${text.length} characters to the clipboard."
    }

    private suspend fun listEvents(start: String?, end: String?): String {
        need(Manifest.permission.READ_CALENDAR)
        val from = start?.let(::parseTime) ?: Instant.now()
        val to = end?.let(::parseTime) ?: from.plusSeconds(7 * 86_400)
        return withContext(Dispatchers.IO) {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                android.content.ContentUris.appendId(it, from.toEpochMilli())
                android.content.ContentUris.appendId(it, to.toEpochMilli())
            }.build()
            val columns = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY)
            var count = 0
            val events = buildJsonArray {
                context.contentResolver.query(uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    while (c.moveToNext() && count++ < 100) addJsonObject {
                        put("title", c.getString(0).orEmpty())
                        put("start", format(c.getLong(1)))
                        put("end", format(c.getLong(2)))
                        c.getString(3)?.takeIf { it.isNotBlank() }?.let { put("location", it) }
                        if (c.getInt(4) == 1) put("all_day", true)
                    }
                }
            }
            if (events.isEmpty()) "No events between ${format(from.toEpochMilli())} and ${format(to.toEpochMilli())}." else events.toString()
        }
    }

    private suspend fun createEvent(title: String, start: String, end: String, location: String?, description: String?): String {
        need(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val begin = parseTime(start)
        val finish = parseTime(end)
        require(finish.isAfter(begin)) { "end must be after start" }
        return withContext(Dispatchers.IO) {
            val calendarId = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}", null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: throw IllegalStateException("No writable calendar on this device.")
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, begin.toEpochMilli())
                put(CalendarContract.Events.DTEND, finish.toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: throw IllegalStateException("The calendar refused the event.")
            "Created \"$title\" from ${format(begin.toEpochMilli())} to ${format(finish.toEpochMilli())} (event ${uri.lastPathSegment})."
        }
    }

    private suspend fun searchContacts(query: String): String {
        need(Manifest.permission.READ_CONTACTS)
        return withContext(Dispatchers.IO) {
            val results = linkedMapOf<Long, Triple<String, MutableSet<String>, MutableSet<String>>>()
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                "${ContactsContract.Data.DISPLAY_NAME} LIKE ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)",
                arrayOf("%$query%", ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
                ContactsContract.Data.DISPLAY_NAME,
            )?.use { c ->
                while (c.moveToNext() && results.size < 20) {
                    val entry = results.getOrPut(c.getLong(0)) { Triple(c.getString(1).orEmpty(), mutableSetOf(), mutableSetOf()) }
                    if (c.getString(2) == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) entry.second += c.getString(3).orEmpty() else entry.third += c.getString(3).orEmpty()
                }
            }
            if (results.isEmpty()) "No contacts match \"$query\"." else buildJsonArray {
                results.values.forEach { (name, phones, emails) ->
                    addJsonObject { put("name", name); put("phones", JsonArray(phones.map(::JsonPrimitive))); put("emails", JsonArray(emails.map(::JsonPrimitive))) }
                }
            }.toString()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(): String {
        need(Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val fix: Location? = withTimeoutOrNull(10_000) {
            providers.firstNotNullOfOrNull { provider ->
                if (Build.VERSION.SDK_INT >= 30) suspendCancellableCoroutine<Location?> { cont ->
                    manager.getCurrentLocation(provider, null, Executors.newSingleThreadExecutor()) { cont.resume(it) }
                } else @Suppress("DEPRECATION") manager.getLastKnownLocation(provider)
            }
        } ?: providers.firstNotNullOfOrNull { manager.getLastKnownLocation(it) }
        fix ?: throw IllegalStateException("Location is unavailable (turn on location services).")
        return buildJsonObject {
            put("latitude", fix.latitude)
            put("longitude", fix.longitude)
            put("accuracy_m", fix.accuracy.toDouble())
            put("time", format(fix.time))
        }.toString()
    }

    private suspend fun setAlarm(hour: Int, minute: Int, label: String?): String {
        require(hour in 0..23 && minute in 0..59) { "hour must be 0-23 and minute 0-59" }
        start(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_SKIP_UI, true).apply { label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } })
        return "Alarm set for %02d:%02d%s.".format(hour, minute, label?.let { " ($it)" }.orEmpty())
    }

    private suspend fun setTimer(seconds: Int, label: String?): String {
        require(seconds in 1..86_400) { "seconds must be between 1 and 86400" }
        start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true).apply { label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } })
        return "Timer started for $seconds seconds${label?.let { " ($it)" }.orEmpty()}."
    }

    private suspend fun start(intent: Intent) = withContext(Dispatchers.Main) {
        val resolved = intent.resolveActivity(context.packageManager) ?: throw IllegalStateException("No clock app handles this on the device.")
        context.startActivity(intent.setComponent(resolved).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun notify(title: String, text: String): String {
        if (Build.VERSION.SDK_INT >= 33) need(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Agent", NotificationManager.IMPORTANCE_DEFAULT))
        val icon = context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        val notification = android.app.Notification.Builder(context, CHANNEL).setSmallIcon(icon).setContentTitle(title).setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        return "Notification shown."
    }

    /** Ask for [names] unless granted; fail the tool call with a clear reason otherwise. */
    private suspend fun need(vararg names: String) {
        val missing = names.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return
        val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        check(missing.all { it in declared }) { "The app does not declare ${missing.filter { it !in declared }.joinToString()}." }
        check(permissions.request(missing)) { "The user did not grant ${missing.joinToString { it.substringAfterLast('.') }}." }
    }

    private fun tool(name: String, description: String, vararg params: Pair<String, JsonObject>, approval: Boolean = false, required: List<String> = emptyList(), run: suspend (JsonObject) -> String) = object : AgentTool {
        override val name = name
        override val description = description
        override val parameters = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(params.toMap()))
            put("required", JsonArray(required.map(::JsonPrimitive)))
        }
        override val requiresApproval = approval
        override fun titleFor(arguments: JsonObject) = arguments.s("title") ?: arguments.s("query")
        override suspend fun execute(arguments: JsonObject) = run(arguments)
    }

    private companion object {
        const val CHANNEL = "agent"
        fun parseTime(value: String): Instant = runCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { java.time.LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant() }
            .recoverCatching { java.time.LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant() }
            .getOrElse { throw IllegalArgumentException("Not an ISO 8601 time: $value") }
        fun format(epochMs: Long): String = OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        fun str(description: String) = buildJsonObject { put("type", "string"); put("description", description) }
        fun int(description: String) = buildJsonObject { put("type", "integer"); put("description", description) }
        fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        fun JsonObject.i(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
    }
}
