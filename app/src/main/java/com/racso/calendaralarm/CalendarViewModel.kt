package com.racso.calendaralarm

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

sealed class CalendarUiState {
    object Loading : CalendarUiState()
    data class Success(val events: List<CalendarEvent>) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
    data class NeedsPermission(val intent: Intent) : CalendarUiState()
    object Empty : CalendarUiState()
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CalendarRepository(application)
    private val context = application.applicationContext
    private val sharedPrefs = application.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _threeDayUiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val threeDayUiState: StateFlow<CalendarUiState> = _threeDayUiState.asStateFlow()

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    private val _selectedCalendarIds = MutableStateFlow<Set<String>>(loadSelectedCalendars())
    val selectedCalendarIds: StateFlow<Set<String>> = _selectedCalendarIds.asStateFlow()

    private val _defaultAlarmMinutes = MutableStateFlow(sharedPrefs.getInt("default_alarm_mins", 10))
    val defaultAlarmMinutes: StateFlow<Int> = _defaultAlarmMinutes.asStateFlow()

    private val _maxListEvents = MutableStateFlow(sharedPrefs.getInt("max_list_events", 15))
    val maxListEvents: StateFlow<Int> = _maxListEvents.asStateFlow()

    private val _activeAlarms = MutableStateFlow<Map<String, Int>>(loadActiveAlarms())
    val activeAlarms: StateFlow<Map<String, Int>> = _activeAlarms.asStateFlow()

    private val _currentStartDate = MutableStateFlow(LocalDate.now())
    val currentStartDate: StateFlow<LocalDate> = _currentStartDate.asStateFlow()

    init {
        loadCalendarList()
    }

    private fun loadSelectedCalendars(): Set<String> {
        return sharedPrefs.getStringSet("selected_calendars", null) ?: emptySet()
    }

    private fun saveSelectedCalendars(ids: Set<String>) {
        sharedPrefs.edit().putStringSet("selected_calendars", ids).apply()
    }

    private fun loadActiveAlarms(): Map<String, Int> {
        val json = sharedPrefs.getString("active_alarms_map", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            obj.keys().forEach { key ->
                map[key] = obj.getInt(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveActiveAlarms(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        sharedPrefs.edit().putString("active_alarms_map", obj.toString()).apply()
    }

    fun setDefaultAlarmMinutes(minutes: Int) {
        _defaultAlarmMinutes.value = minutes
        sharedPrefs.edit().putInt("default_alarm_mins", minutes).apply()
    }

    fun setMaxListEvents(count: Int) {
        _maxListEvents.value = count
        sharedPrefs.edit().putInt("max_list_events", count).apply()
        loadEvents() // Reload list
    }

    fun loadCalendarList() {
        viewModelScope.launch {
            val result = repository.getCalendarList()
            if (result.isSuccess) {
                _calendars.value = result.getOrDefault(emptyList())
                if (_selectedCalendarIds.value.isEmpty()) {
                    val primary = _calendars.value.find { it.primary }
                    primary?.let { toggleCalendarSelection(it.id) }
                }
            }
        }
    }

    fun toggleCalendarSelection(calendarId: String) {
        val current = _selectedCalendarIds.value.toMutableSet()
        if (current.contains(calendarId)) {
            current.remove(calendarId)
        } else {
            current.add(calendarId)
        }
        _selectedCalendarIds.value = current
        saveSelectedCalendars(current)
        loadEvents()
        loadThreeDayEvents()
    }

    fun navigateToDate(date: LocalDate) {
        _currentStartDate.value = date
        loadThreeDayEvents()
    }

    fun nextDays() {
        navigateToDate(_currentStartDate.value.plusDays(3))
    }

    fun previousDays() {
        navigateToDate(_currentStartDate.value.minusDays(3))
    }

    fun loadEvents() {
        _uiState.value = CalendarUiState.Loading
        viewModelScope.launch {
            val selectedIds = _selectedCalendarIds.value.toList()
            val result = repository.getUpcomingEvents(selectedIds, _maxListEvents.value)
            
            _uiState.value = if (result.isSuccess) {
                val events = result.getOrDefault(emptyList())
                if (events.isEmpty()) CalendarUiState.Empty
                else {
                    events.forEach { event ->
                        if (!_activeAlarms.value.containsKey(event.id)) {
                            setAlarmForEvent(event, _defaultAlarmMinutes.value)
                        }
                    }
                    CalendarUiState.Success(events)
                }
            } else {
                handleError(result.exceptionOrNull())
            }
        }
    }

    fun loadThreeDayEvents() {
        _threeDayUiState.value = CalendarUiState.Loading
        viewModelScope.launch {
            val selectedIds = _selectedCalendarIds.value.toList()
            val result = repository.getEventsForRange(selectedIds, _currentStartDate.value, 3)
            
            _threeDayUiState.value = if (result.isSuccess) {
                val events = result.getOrDefault(emptyList())
                if (events.isEmpty()) CalendarUiState.Empty
                else CalendarUiState.Success(events)
            } else {
                handleError(result.exceptionOrNull())
            }
        }
    }

    private fun handleError(error: Throwable?): CalendarUiState {
        return if (error is UserRecoverableAuthException) {
            val intent = error.intent
            if (intent != null) {
                CalendarUiState.NeedsPermission(intent)
            } else {
                CalendarUiState.Error("Necesitás dar permisos de Calendar.")
            }
        } else {
            val message = error?.message ?: "Error desconocido"
            CalendarUiState.Error(message)
        }
    }

    fun setAlarmForEvent(event: CalendarEvent, minutesBefore: Int) {
        val triggerTime = event.startMillis - (minutesBefore * 60 * 1000)
        if (triggerTime <= System.currentTimeMillis()) return

        AlarmScheduler.scheduleAlarm(
            context = context,
            eventId = event.id,
            eventTitle = event.title,
            triggerTimeMillis = triggerTime
        )
        
        val currentAlarms = _activeAlarms.value.toMutableMap()
        currentAlarms[event.id] = minutesBefore
        _activeAlarms.value = currentAlarms
        saveActiveAlarms(currentAlarms)
    }

    fun cancelAlarmForEvent(event: CalendarEvent) {
        AlarmScheduler.cancelAlarm(context, event.id)
        val currentAlarms = _activeAlarms.value.toMutableMap()
        currentAlarms.remove(event.id)
        _activeAlarms.value = currentAlarms
        saveActiveAlarms(currentAlarms)
    }
}