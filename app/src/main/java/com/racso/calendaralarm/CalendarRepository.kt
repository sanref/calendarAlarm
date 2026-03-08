package com.racso.calendaralarm

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.google.api.client.util.DateTime

data class CalendarEvent(
    val id: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val startMillis: Long,
    val calendarName: String = ""
)

data class CalendarInfo(
    val id: String,
    val summary: String,
    val primary: Boolean
)

class CalendarRepository(private val context: Context) {

    private suspend fun getCalendarService(): Result<Calendar> {
        return withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: return@withContext Result.failure(Exception("No hay sesión activa"))

                val googleAccount = account.account
                    ?: return@withContext Result.failure(Exception("No se pudo obtener la cuenta"))

                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf(CalendarScopes.CALENDAR_READONLY)
                ).apply {
                    selectedAccount = googleAccount
                }

                // Forzar refresh del token para verificar permisos
                GoogleAuthUtil.getToken(
                    context,
                    googleAccount,
                    "oauth2:${CalendarScopes.CALENDAR_READONLY}"
                )

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                )
                    .setApplicationName("CalendarAlarm")
                    .build()
                
                Result.success(service)
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCalendarList(): Result<List<CalendarInfo>> {
        val serviceResult = getCalendarService()
        if (serviceResult.isFailure) return Result.failure(serviceResult.exceptionOrNull()!!)
        
        return withContext(Dispatchers.IO) {
            try {
                val service = serviceResult.getOrThrow()
                val calendarList = service.calendarList().list().execute()
                val items = calendarList.items ?: emptyList()
                
                val result = items.map { item ->
                    CalendarInfo(
                        id = item.id ?: "",
                        summary = item.summary ?: "(Sin nombre)",
                        primary = item.primary ?: false
                    )
                }
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUpcomingEvents(calendarIds: List<String>, maxResults: Int): Result<List<CalendarEvent>> {
        val serviceResult = getCalendarService()
        if (serviceResult.isFailure) return Result.failure(serviceResult.exceptionOrNull()!!)

        return withContext(Dispatchers.IO) {
            try {
                val service = serviceResult.getOrThrow()
                val now = DateTime(System.currentTimeMillis())
                val allEvents = mutableListOf<CalendarEvent>()

                val calendarList = service.calendarList().list().execute().items ?: emptyList()
                val calendarMap = calendarList.associate { it.id to (it.summary ?: "Desconocido") }

                val targetIds = if (calendarIds.isEmpty()) listOf("primary") else calendarIds

                for (calendarId in targetIds) {
                    val events = service.events().list(calendarId)
                        .setMaxResults(maxResults)
                        .setTimeMin(now)
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute()

                    val items = events.items ?: emptyList()
                    val calName = calendarMap[calendarId] ?: "Principal"

                    allEvents.addAll(items.map { event ->
                        CalendarEvent(
                            id = event.id ?: "",
                            title = event.summary ?: "(Sin título)",
                            startTime = formatTime(event, isStart = true),
                            endTime = formatTime(event, isStart = false),
                            startMillis = getMillis(event, isStart = true),
                            calendarName = calName
                        )
                    })
                }

                Result.success(allEvents.sortedBy { it.startMillis }.take(maxResults))

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getEventsForRange(calendarIds: List<String>, startDate: LocalDate, days: Int): Result<List<CalendarEvent>> {
        val serviceResult = getCalendarService()
        if (serviceResult.isFailure) return Result.failure(serviceResult.exceptionOrNull()!!)

        return withContext(Dispatchers.IO) {
            try {
                val service = serviceResult.getOrThrow()
                val zoneId = ZoneId.systemDefault()
                val startDateTime = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endDateTime = startDate.plusDays(days.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
                
                val timeMin = DateTime(startDateTime)
                val timeMax = DateTime(endDateTime)
                
                val allEvents = mutableListOf<CalendarEvent>()

                val calendarList = service.calendarList().list().execute().items ?: emptyList()
                val calendarMap = calendarList.associate { it.id to (it.summary ?: "Desconocido") }

                val targetIds = if (calendarIds.isEmpty()) listOf("primary") else calendarIds

                for (calendarId in targetIds) {
                    val events = service.events().list(calendarId)
                        .setTimeMin(timeMin)
                        .setTimeMax(timeMax)
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .execute()

                    val items = events.items ?: emptyList()
                    val calName = calendarMap[calendarId] ?: "Principal"

                    allEvents.addAll(items.map { event ->
                        CalendarEvent(
                            id = event.id ?: "",
                            title = event.summary ?: "(Sin título)",
                            startTime = formatTime(event, isStart = true),
                            endTime = formatTime(event, isStart = false),
                            startMillis = getMillis(event, isStart = true),
                            calendarName = calName
                        )
                    })
                }

                Result.success(allEvents.sortedBy { it.startMillis })

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun formatTime(event: Event, isStart: Boolean): String {
        val dateTime = if (isStart) event.start else event.end
        return try {
            if (dateTime?.dateTime != null) {
                val instant = Instant.ofEpochMilli(dateTime.dateTime.value)
                val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            } else if (dateTime?.date != null) {
                dateTime.date.toString()
            } else {
                "Sin fecha"
            }
        } catch (e: Exception) {
            "Sin fecha"
        }
    }

    private fun getMillis(event: Event, isStart: Boolean): Long {
        val dateTime = if (isStart) event.start else event.end
        return dateTime?.dateTime?.value ?: dateTime?.date?.value ?: 0L
    }
}