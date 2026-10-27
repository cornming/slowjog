package com.cornming.slowjog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(record: WorkoutRecord)

    @Query("SELECT * FROM workout_record ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutRecord>>
}
