package com.cornming.slowjog.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一次運動的紀錄。累計時間與連續天數都由這張表計算，不另外存。 */
@Entity(tableName = "workout_record")
data class WorkoutRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 運動當天的日期（LocalDate.toEpochDay）。 */
    val dateEpochDay: Long,
    /** 開始時間（毫秒時間戳記）。 */
    val startedAt: Long,
    /** 整段運動時長（秒），不含暫停。 */
    val durationSec: Int,
    /** 其中超慢跑（節拍器運作）的時長（秒）。 */
    val jogSec: Int,
    val bpm: Int,
)
