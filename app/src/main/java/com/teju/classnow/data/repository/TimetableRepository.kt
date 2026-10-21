package com.teju.classnow.data.repository

import com.teju.classnow.data.local.TimetableDao
import com.teju.classnow.data.model.TimetableEntry
import kotlinx.coroutines.flow.Flow

class TimetableRepository(private val timetableDao: TimetableDao) {
    val allEntries: Flow<List<TimetableEntry>> = timetableDao.getAllEntries()
    suspend fun getAllEntriesList(): List<TimetableEntry> = timetableDao.getAllEntriesList()

    fun getEntriesForDay(day: String): Flow<List<TimetableEntry>> = timetableDao.getEntriesForDay(day)
    suspend fun getEntriesForDayList(day: String): List<TimetableEntry> = timetableDao.getEntriesForDayList(day)


    suspend fun insertEntry(entry: TimetableEntry) = timetableDao.insertEntry(entry)

    suspend fun updateEntry(entry: TimetableEntry) = timetableDao.updateEntry(entry)

    suspend fun deleteEntry(entry: TimetableEntry) = timetableDao.deleteEntry(entry)

    suspend fun clearAll() = timetableDao.deleteAllEntries()
}
