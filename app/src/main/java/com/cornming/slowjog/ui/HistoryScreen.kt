package com.cornming.slowjog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cornming.slowjog.R
import com.cornming.slowjog.data.Stats
import com.cornming.slowjog.data.WorkoutRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 紀錄畫面：累計數字與歷史運動列表。 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val records by rememberRecords()
    val stats = remember(records) { Stats.compute(records, LocalDate.now()) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("M月d日 E", Locale.TRADITIONAL_CHINESE) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem(stringResource(R.string.stat_total_jog), formatTotal(stats.totalJogSec))
                        StatItem(stringResource(R.string.stat_total_workout), formatTotal(stats.totalDurationSec))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem(stringResource(R.string.stat_streak), stringResource(R.string.days_format, stats.streakDays))
                        StatItem(stringResource(R.string.stat_count), stringResource(R.string.times_format, stats.count))
                    }
                }
            }
        }
        if (records.isEmpty()) {
            item {
                Text(text = stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(records, key = { it.id }) { record ->
                RecordRow(record, dateFormat, timeFormat)
            }
        }
    }
}

@Composable
private fun RecordRow(record: WorkoutRecord, dateFormat: DateTimeFormatter, timeFormat: DateTimeFormatter) {
    val start = Instant.ofEpochMilli(record.startedAt).atZone(ZoneId.systemDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = start.format(dateFormat) + "　" + start.format(timeFormat),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(stringResource(R.string.workout_total_time), formatClock(record.durationSec))
                StatItem(stringResource(R.string.workout_jog_time), formatClock(record.jogSec))
                StatItem(stringResource(R.string.bpm_unit), record.bpm.toString())
            }
        }
    }
}
