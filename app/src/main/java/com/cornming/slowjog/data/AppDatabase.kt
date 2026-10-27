package com.cornming.slowjog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(entities = [WorkoutRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}

/** 運動紀錄的單一入口；資料只存在本機。 */
object RecordRepository {
    @Volatile
    private var dao: WorkoutDao? = null

    fun init(context: Context) {
        if (dao != null) return
        synchronized(this) {
            if (dao == null) {
                dao = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "slowjog.db")
                    .build()
                    .workoutDao()
            }
        }
    }

    private fun requireDao(): WorkoutDao = checkNotNull(dao) { "RecordRepository 尚未初始化" }

    fun observeAll(): Flow<List<WorkoutRecord>> = requireDao().observeAll()

    suspend fun insert(record: WorkoutRecord) = requireDao().insert(record)
}
