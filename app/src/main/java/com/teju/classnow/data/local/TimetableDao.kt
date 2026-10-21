package com.teju.classnow.data.local

import androidx.room.*
import com.teju.classnow.data.model.TimetableEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries ORDER BY dayOfWeek, startTime ASC")
    fun getAllEntries(): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries ORDER BY dayOfWeek, startTime ASC")
    suspend fun getAllEntriesList(): List<TimetableEntry>

    @Query("SELECT * FROM timetable_entries WHERE dayOfWeek = :day ORDER BY startTime ASC")
    fun getEntriesForDay(day: String): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries WHERE dayOfWeek = :day ORDER BY startTime ASC")
    suspend fun getEntriesForDayList(day: String): List<TimetableEntry>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: TimetableEntry)

    @Update
    suspend fun updateEntry(entry: TimetableEntry)

    @Delete
    suspend fun deleteEntry(entry: TimetableEntry)

    @Query("DELETE FROM timetable_entries")
    suspend fun deleteAllEntries()
}
