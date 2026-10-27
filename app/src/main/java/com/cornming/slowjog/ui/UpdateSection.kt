package com.cornming.slowjog.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cornming.slowjog.R
import com.cornming.slowjog.update.UpdateController
import kotlin.math.roundToInt

/** 首頁底部：目前版本、檢查更新按鈕，以及有新版時的更新提示。 */
@Composable
fun UpdateSection(versionName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by UpdateController.state.collectAsStateWithLifecycle()

    // 開啟 App 時自動檢查一次，沒有新版或失敗都不打擾。
    LaunchedEffect(Unit) { UpdateController.check(context, silent = true) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (val s = state) {
            is UpdateController.State.Available, is UpdateController.State.NeedPermission -> {
                val info = (s as? UpdateController.State.Available)?.info
                    ?: (s as UpdateController.State.NeedPermission).info
                Text(
                    text = stringResource(
                        R.string.update_available,
                        info.version,
                        Formatter.formatShortFileSize(context, info.apkSize),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (s is UpdateController.State.NeedPermission) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = stringResource(R.string.update_need_permission),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    onClick = { UpdateController.startUpdate(context) },
                ) { Text(stringResource(R.string.update_now)) }
            }

            is UpdateController.State.Downloading -> {
                Text(
                    text = stringResource(R.string.update_downloading, (s.progress * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            is UpdateController.State.Checking ->
                Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)

            is UpdateController.State.UpToDate ->
                Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyMedium)

            is UpdateController.State.Failed ->
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

            is UpdateController.State.Idle -> Unit
        }

        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (versionName.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.version_format, versionName),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                enabled = state !is UpdateController.State.Checking && state !is UpdateController.State.Downloading,
                onClick = { UpdateController.check(context, silent = false) },
            ) { Text(stringResource(R.string.update_check)) }
        }
    }
}
