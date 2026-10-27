package com.cornming.slowjog.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 把運動紀錄匯出成 CSV，欄位同資料模型。 */
object CsvExport {

    const val HEADER = "id,date,startedAt,durationSec,jogSec,bpm"

    fun build(records: List<WorkoutRecord>, zone: ZoneId = ZoneId.systemDefault()): String {
        val builder = StringBuilder(HEADER).append('\n')
        records.sortedBy { it.startedAt }.forEach { r ->
            builder
                .append(r.id).append(',')
                .append(LocalDate.ofEpochDay(r.dateEpochDay)).append(',')
                .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(r.startedAt).atZone(zone))).append(',')
                .append(r.durationSec).append(',')
                .append(r.jogSec).append(',')
                .append(r.bpm).append('\n')
        }
        return builder.toString()
    }
}
