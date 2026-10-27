package com.cornming.slowjog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cornming.slowjog.data.RecordRepository
import com.cornming.slowjog.data.WorkoutRecord
import kotlinx.coroutines.delay

/**
 * 包住「會啟動節拍器」的動作：Android 13 以上先取得通知權限；
 * 不論使用者同意與否都照樣執行，沒有權限時只是看不到通知，節拍器仍會運作。
 */
@Composable
fun rememberWithNotificationPermission(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentAction by rememberUpdatedState(action)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        currentAction()
    }
    return remember(context, launcher) {
        {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else currentAction()
        }
    }
}

/** 每 200 毫秒更新一次的開機後經過時間，用來讓計時畫面跳動。 */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    return now
}

@Composable
fun rememberRecords(): State<List<WorkoutRecord>> {
    val flow = remember { RecordRepository.observeAll() }
    return flow.collectAsStateWithLifecycle(initialValue = emptyList())
}
