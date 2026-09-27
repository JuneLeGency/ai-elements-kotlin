package dev.ai.elements.demo

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.harness.device.DeviceTools
import dev.ai.elements.harness.device.PermissionGate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** harness-device against the real providers of the device: calendar, contacts, clipboard, notifications. */
@RunWith(AndroidJUnit4::class)
class DeviceToolsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val granted = PermissionGate { true }
    private var calendarId = -1L

    @Before
    fun grant() {
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS)
            .forEach { runCatching { automation.grantRuntimePermission(context.packageName, it) } }
        // A local calendar to write into (emulators have no account calendars).
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "agent-test")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        calendarId = context.contentResolver.insert(uri, ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "agent-test")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Agent test")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Agent test")
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "agent-test")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.IS_PRIMARY, 1)
        })!!.lastPathSegment!!.toLong()
    }

    @After
    fun cleanUp() {
        context.contentResolver.delete(
            CalendarContract.Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "agent-test")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build(),
            "${CalendarContract.Calendars._ID} = ?", arrayOf(calendarId.toString()),
        )
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS)
        try {
            context.contentResolver.delete(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(), "${ContactsContract.RawContacts.ACCOUNT_NAME} = ?", arrayOf("agent-test"))
        } finally { automation.dropShellPermissionIdentity() }
    }

    private suspend fun DeviceTools.call(name: String, args: JsonObject = JsonObject(emptyMap())) = tools().single { it.name == name }.execute(args)

    @Test
    fun calendarContactsClipboardInfoNotifications() = runBlocking<Unit> {
        val device = DeviceTools(context, granted)
        val tomorrow = LocalDate.now().plusDays(1)
        val zone = ZoneId.systemDefault()
        val start = tomorrow.atTime(9, 0).atZone(zone).toOffsetDateTime().toString()
        val end = tomorrow.atTime(10, 0).atZone(zone).toOffsetDateTime().toString()
        val created = device.call("create_calendar_event", buildJsonObject { put("title", "Design review"); put("start", start); put("end", end); put("location", "Room 3") })
        assertTrue(created, created.startsWith("Created \"Design review\""))
        val listed = device.call("list_calendar_events", buildJsonObject { put("start", tomorrow.atStartOfDay(zone).toOffsetDateTime().toString()); put("end", tomorrow.plusDays(1).atStartOfDay(zone).toOffsetDateTime().toString()) })
        assertTrue(listed, listed.contains("Design review") && listed.contains("Room 3"))

        // Seed a contact with the shell's permission, so the app itself needs no WRITE_CONTACTS.
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS)
        try { context.contentResolver.applyBatch(ContactsContract.AUTHORITY, arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI).withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "agent.test").withValue(ContactsContract.RawContacts.ACCOUNT_NAME, "agent-test").build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Ada Lovelace").build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "+1 555 0100").build(),
        )) } finally { automation.dropShellPermissionIdentity() }
        val found = device.call("search_contacts", buildJsonObject { put("query", "Lovelace") })
        assertTrue(found, found.contains("Ada Lovelace") && found.contains("+1 555 0100"))

        assertEquals("Copied 5 characters to the clipboard.", device.call("copy_to_clipboard", buildJsonObject { put("text", "hello") }))
        val info = Json.parseToJsonElement(device.call("get_device_info")).jsonObject
        assertTrue(info.toString(), info["sdk"]!!.jsonPrimitive.content.toInt() >= 26)
        assertEquals("Notification shown.", device.call("post_notification", buildJsonObject { put("title", "Agent"); put("text", "Done") }))

        // A permission the user refuses fails the tool call with a readable reason
        // (location is never granted here; revoking one would kill the app process).
        val denied = runCatching { DeviceTools(context, PermissionGate { false }).call("get_location") }.exceptionOrNull()
        assertTrue(denied?.message.orEmpty(), denied?.message.orEmpty().contains("did not grant ACCESS_COARSE_LOCATION"))
    }
}
