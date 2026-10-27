package com.cornming.slowjog.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CsvExportTest {

    private val taipei = ZoneId.of("Asia/Taipei")

    private fun record(id: Long, hour: Int, duration: Int, jog: Int, bpm: Int): WorkoutRecord {
        val start = ZonedDateTime.of(2026, 10, 27, hour, 30, 0, 0, taipei)
        return WorkoutRecord(
            id = id,
            dateEpochDay = LocalDate.of(2026, 10, 27).toEpochDay(),
            startedAt = start.toInstant().toEpochMilli(),
            durationSec = duration,
            jogSec = jog,
            bpm = bpm,
        )
    }

    @Test
    fun emptyHasOnlyHeader() {
        assertEquals("id,date,startedAt,durationSec,jogSec,bpm\n", CsvExport.build(emptyList(), taipei))
    }

    @Test
    fun rowsAreSortedByStartTimeWithIsoDates() {
        val csv = CsvExport.build(listOf(record(2, 18, 600, 300, 170), record(1, 6, 1800, 900, 180)), taipei)
        assertEquals(
            "id,date,startedAt,durationSec,jogSec,bpm\n" +
                "1,2026-10-27,2026-10-27T06:30:00+08:00,1800,900,180\n" +
                "2,2026-10-27,2026-10-27T18:30:00+08:00,600,300,170\n",
            csv,
        )
    }
}
