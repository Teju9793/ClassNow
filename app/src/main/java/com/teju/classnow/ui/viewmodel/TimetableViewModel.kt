package com.teju.classnow.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teju.classnow.data.local.AppDatabase
import com.teju.classnow.data.local.SettingsRepository
import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.data.repository.TimetableRepository
import com.teju.classnow.domain.timetable.ScheduleState
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import com.teju.classnow.util.CsvHelper
import com.teju.classnow.util.SmartSilentManager
import com.teju.classnow.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TimetableRepository
    private val settingsRepository = SettingsRepository(application)

    val allEntries: StateFlow<List<TimetableEntry>>

    private val _selectedDay = MutableStateFlow(getCurrentDayName())
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    private val _currentTime = MutableStateFlow(LocalTime.now())
    val currentTime: StateFlow<LocalTime> = _currentTime.asStateFlow()

    val currentDayEntries: StateFlow<List<TimetableEntry>>

    val scheduleState: StateFlow<ScheduleState>

    val isLiveNotificationEnabled: StateFlow<Boolean> = settingsRepository.isLiveNotificationEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val isSmartSilentModeEnabled: StateFlow<Boolean> = settingsRepository.isSmartSilentModeEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val smartSilentTarget: StateFlow<String> = settingsRepository.smartSilentTarget.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "SILENT"
    )

    val manualOverridePeriod: StateFlow<String> = settingsRepository.manualOverridePeriod.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val themeMode: StateFlow<String> = settingsRepository.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM"
    )

    val hasSeenOnboarding: StateFlow<Boolean?> = settingsRepository.hasSeenOnboarding
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // CSV Import State
    private val _importPreview = MutableStateFlow<CsvHelper.ParseResult?>(null)
    val importPreview: StateFlow<CsvHelper.ParseResult?> = _importPreview.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).timetableDao()
        repository = TimetableRepository(dao)
        allEntries = repository.allEntries.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        currentDayEntries = _selectedDay
            .flatMapLatest { day -> repository.getEntriesForDay(day) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Unified timetable state calculation driven by local clock, today's entries, and silent settings
        val todayEntriesFlow = repository.getEntriesForDay(getCurrentDayName())
        scheduleState = combine(
            _currentTime,
            todayEntriesFlow,
            settingsRepository.isSmartSilentModeEnabled,
            settingsRepository.manualOverridePeriod
        ) { time, entries, isSilentEnabled, overrideKey ->
            TimetableEngine.getScheduleState(
                currentTime = time,
                entries = entries,
                smartSilentMasterEnabled = isSilentEnabled,
                manualOverridePeriodKey = overrideKey
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            ScheduleState(TimetableStatus.NO_CLASSES_TODAY)
        )

        // Minute-boundary clock tick: aligns precisely to :00 seconds to eliminate per-second polling
        viewModelScope.launch {
            while (true) {
                val now = LocalTime.now()
                _currentTime.value = now
                val millisUntilNextMinute = ((60 - now.second) * 1000L - (now.nano / 1_000_000L))
                    .coerceIn(500L, 60_000L)
                delay(millisUntilNextMinute)
            }
        }
    }

    /**
     * Immediately refreshes the clock to current time.
     * Called on screen resumption so the UI never shows stale data after returning to foreground.
     */
    fun refreshCurrentTime() {
        _currentTime.value = LocalTime.now()
    }

    fun toggleLiveNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLiveNotificationEnabled(enabled)
            triggerAllUpdates()
        }
    }

    fun toggleSmartSilentMode(enabled: Boolean) {
        viewModelScope.launch {
            com.teju.classnow.widget.ClassNowWidgetReceiver.executeMasterToggle(getApplication(), enabled)
        }
    }

    fun setSmartSilentTarget(target: String) {
        viewModelScope.launch {
            settingsRepository.setSmartSilentTarget(target)
            triggerAllUpdates()
        }
    }

    fun toggleManualSoundOverride(periodKey: String) {
        viewModelScope.launch {
            val current = settingsRepository.manualOverridePeriod.first()
            if (current == periodKey) {
                SmartSilentManager.clearManualOverride(settingsRepository)
            } else {
                SmartSilentManager.recordManualOverride(settingsRepository, periodKey)
            }
            triggerAllUpdates()
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            WidgetUpdateHelper.updateWidgetUiOnly(getApplication())
        }
    }

    fun setHasSeenOnboarding(seen: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHasSeenOnboarding(seen)
        }
    }


    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun triggerAllUpdates() {
        viewModelScope.launch {
            WidgetUpdateHelper.triggerUpdate(getApplication())
        }
    }

    fun addEntry(
        subject: String,
        startTime: String,
        endTime: String,
        faculty: String?,
        day: String,
        silentModeRule: String = TimetableEntry.RULE_DEFAULT,
        customSilentSchedule: String? = null
    ) {
        viewModelScope.launch {
            repository.insertEntry(
                TimetableEntry(
                    dayOfWeek = day,
                    subject = subject,
                    startTime = startTime,
                    endTime = endTime,
                    faculty = faculty.takeIf { !it.isNullOrBlank() },
                    silentModeRule = silentModeRule,
                    customSilentSchedule = customSilentSchedule
                )
            )
            triggerAllUpdates()
        }
    }

    fun updateEntry(entry: TimetableEntry) {
        viewModelScope.launch {
            repository.updateEntry(entry)
            triggerAllUpdates()
        }
    }

    fun deleteEntry(entry: TimetableEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
            triggerAllUpdates()
        }
    }

    fun duplicateEntry(entry: TimetableEntry) {
        viewModelScope.launch {
            repository.insertEntry(
                TimetableEntry(
                    dayOfWeek = entry.dayOfWeek,
                    subject = "${entry.subject} (Copy)",
                    startTime = entry.startTime,
                    endTime = entry.endTime,
                    faculty = entry.faculty,
                    silentModeRule = entry.silentModeRule,
                    customSilentSchedule = entry.customSilentSchedule
                )
            )
            triggerAllUpdates()
        }
    }

    fun moveEntryUp(entry: TimetableEntry) {
        viewModelScope.launch {
            val entries = currentDayEntries.value
            val index = entries.indexOfFirst { it.id == entry.id }
            if (index > 0) {
                val prevEntry = entries[index - 1]
                val updatedCurrent = entry.copy(startTime = prevEntry.startTime, endTime = prevEntry.endTime)
                val updatedPrev = prevEntry.copy(startTime = entry.startTime, endTime = entry.endTime)
                repository.updateEntry(updatedCurrent)
                repository.updateEntry(updatedPrev)
                triggerAllUpdates()
            }
        }
    }

    fun moveEntryDown(entry: TimetableEntry) {
        viewModelScope.launch {
            val entries = currentDayEntries.value
            val index = entries.indexOfFirst { it.id == entry.id }
            if (index >= 0 && index < entries.size - 1) {
                val nextEntry = entries[index + 1]
                val updatedCurrent = entry.copy(startTime = nextEntry.startTime, endTime = nextEntry.endTime)
                val updatedNext = nextEntry.copy(startTime = entry.startTime, endTime = entry.endTime)
                repository.updateEntry(updatedCurrent)
                repository.updateEntry(updatedNext)
                triggerAllUpdates()
            }
        }
    }

    fun clearAllEntries() {
        viewModelScope.launch {
            repository.clearAll()
            triggerAllUpdates()
        }
    }

    // --- CSV Handling ---

    fun prepareImport(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val csvText = reader.use { it.readText() }
                val result = CsvHelper.parseCsv(csvText)
                _importPreview.value = result
            } catch (e: Exception) {
                _importPreview.value = CsvHelper.ParseResult(emptyList(), listOf("Failed to read file: ${e.message}"))
            }
        }
    }

    fun clearImportPreview() {
        _importPreview.value = null
    }

    fun confirmImport(replaceExisting: Boolean) {
        val preview = _importPreview.value ?: return
        if (preview.errors.isNotEmpty()) return

        viewModelScope.launch {
            if (replaceExisting) {
                repository.clearAll()
            }
            preview.entries.forEach { repository.insertEntry(it) }
            _importPreview.value = null
            triggerAllUpdates()
        }
    }

    fun getCsvExportData(): String {
        return CsvHelper.exportToCsv(allEntries.value)
    }

    fun getCurrentDayName(): String {
        return LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    }
}
