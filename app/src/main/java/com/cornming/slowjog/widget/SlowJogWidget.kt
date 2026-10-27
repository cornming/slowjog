package com.cornming.slowjog.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cornming.slowjog.MainActivity
import com.cornming.slowjog.R
import com.cornming.slowjog.data.Habit
import com.cornming.slowjog.data.RecordRepository
import com.cornming.slowjog.data.Stats
import com.cornming.slowjog.settings.AppSettings
import com.cornming.slowjog.ui.formatTotal
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 桌面小工具：累計超慢跑時間、連續天數與今天是否完成。點一下開啟 App。
 * 儲存運動後會立即更新；平常由系統每 30 分鐘左右更新一次，所以跨過午夜後最多晚半小時才換成「今天還沒運動」。
 */
class SlowJogWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        RecordRepository.init(context)
        AppSettings.init(context)
        val records = RecordRepository.observeAll().first()
        val today = LocalDate.now()
        val stats = Stats.compute(records, today)
        val score = (Habit.score(records.map { it.dateEpochDay }.toSet(), today.toEpochDay(), AppSettings.settings.value.weeklyTargetDays) * 100).toInt()
        provideContent { WidgetContent(context, stats, score) }
    }

    companion object {
        suspend fun refresh(context: Context) {
            SlowJogWidget().updateAll(context.applicationContext)
        }
    }
}

class SlowJogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SlowJogWidget()
}

@Composable
private fun WidgetContent(context: Context, stats: Stats, score: Int) {
    val background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF202124))
    val text = ColorProvider(day = Color(0xFF1B1B1B), night = Color(0xFFEDEDED))
    val accent = ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF81C784))
    val muted = ColorProvider(day = Color(0xFF616161), night = Color(0xFFB0B0B0))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_total_label),
            style = TextStyle(color = muted, fontSize = 12.sp),
        )
        Text(
            text = formatTotal(stats.totalJogSec),
            style = TextStyle(color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_streak, stats.streakDays),
            style = TextStyle(color = text, fontSize = 14.sp),
        )
        Text(
            text = context.getString(R.string.widget_score, score),
            style = TextStyle(color = text, fontSize = 14.sp),
        )
        Text(
            text = context.getString(if (stats.doneToday) R.string.widget_done_today else R.string.widget_not_yet),
            style = TextStyle(color = if (stats.doneToday) accent else muted, fontSize = 12.sp),
        )
    }
}
