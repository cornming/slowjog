package com.cornming.slowjog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.cornming.slowjog.reminder.ReminderNotifier
import com.cornming.slowjog.reminder.ReminderScheduler
import com.cornming.slowjog.ui.StartRequest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cornming.slowjog.data.RecordRepository
import com.cornming.slowjog.settings.AppSettings
import com.cornming.slowjog.ui.HistoryScreen
import com.cornming.slowjog.ui.HomeScreen
import com.cornming.slowjog.ui.SettingsScreen
import com.cornming.slowjog.ui.SlowJogTheme
import com.cornming.slowjog.ui.AppIcons
import com.cornming.slowjog.ui.LocalPalette
import com.cornming.slowjog.ui.WorkoutScreen
import com.cornming.slowjog.workout.WorkoutController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordRepository.init(applicationContext)
        AppSettings.init(applicationContext)
        ReminderScheduler.schedule(applicationContext) // 每次開啟都依設定確認一次鬧鐘
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings by AppSettings.settings.collectAsStateWithLifecycle()
            SlowJogTheme(settings.appTheme) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 從提醒通知的「開始運動」按鈕進來：收起通知並請首頁開始運動。 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_WORKOUT, false) == true) {
            intent.removeExtra(EXTRA_START_WORKOUT)
            ReminderNotifier.cancel(applicationContext)
            StartRequest.request()
        }
    }

    companion object {
        const val EXTRA_START_WORKOUT = "com.cornming.slowjog.START_WORKOUT"
    }
}

@Composable
private fun AppRoot() {
    val workout by WorkoutController.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val startRequested by StartRequest.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings by AppSettings.settings.collectAsStateWithLifecycle()

    // 第一次開啟 App 時問一次通知權限，之後的提醒才看得到。
    val askNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(settings.reminderEnabled) {
        if (settings.reminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AppSettings.notificationAsked() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            AppSettings.markNotificationAsked()
            askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // 通知要求開始運動：切到首頁（首頁會接手）；已經在運動中就忽略。
    LaunchedEffect(startRequested, workout.active) {
        if (startRequested) {
            if (workout.active) StartRequest.clear() else tab = 0
        }
    }

    // 運動中只顯示運動中畫面；其餘時間有首頁與紀錄兩個分頁。
    if (workout.active) {
        WorkoutScreen()
    } else {
        val palette = LocalPalette.current
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = palette.accentText,
            selectedTextColor = palette.accentText,
            indicatorColor = palette.accentSoft,
            unselectedIconColor = palette.inkSoft,
            unselectedTextColor = palette.inkSoft,
        )
        Scaffold(
            containerColor = palette.bg,
            bottomBar = {
                NavigationBar(containerColor = palette.surface) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(AppIcons.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_home)) },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(AppIcons.Calendar, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_history)) },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(AppIcons.Sliders, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                        colors = itemColors,
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen()
                    1 -> HistoryScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}
