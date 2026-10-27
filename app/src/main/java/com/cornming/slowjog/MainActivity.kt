package com.cornming.slowjog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.cornming.slowjog.ui.HistoryScreen
import com.cornming.slowjog.ui.HomeScreen
import com.cornming.slowjog.ui.SlowJogTheme
import com.cornming.slowjog.ui.WorkoutScreen
import com.cornming.slowjog.workout.WorkoutController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordRepository.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            SlowJogTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val workout by WorkoutController.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // 運動中只顯示運動中畫面；其餘時間有首頁與紀錄兩個分頁。
    if (workout.active) {
        WorkoutScreen()
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = {},
                        label = { Text(stringResource(R.string.tab_home)) },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = {},
                        label = { Text(stringResource(R.string.tab_history)) },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                if (tab == 0) HomeScreen() else HistoryScreen()
            }
        }
    }
}
