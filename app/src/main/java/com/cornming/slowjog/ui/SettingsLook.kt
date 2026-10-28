package com.cornming.slowjog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cornming.slowjog.R
import com.cornming.slowjog.mascot.MascotStyle
import com.cornming.slowjog.scenery.Scene
import com.cornming.slowjog.scenery.ScenePref
import com.cornming.slowjog.scenery.SceneryView
import com.cornming.slowjog.settings.AppSettings
import com.cornming.slowjog.settings.Settings

/** 設定的「外觀」區：介面風格、陪跑夥伴、慢跑時的風景。 */
@Composable
fun LookSection(settings: Settings) {
    val p = LocalPalette.current
    Text(text = stringResource(R.string.settings_look_section), style = MaterialTheme.typography.titleMedium, color = p.ink)

    Text(text = stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleSmall, color = p.ink)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (theme in AppTheme.values()) {
            val tp = paletteOf(theme)
            OptionCard(
                modifier = Modifier.weight(1f),
                selected = settings.appTheme == theme,
                onClick = { AppSettings.setAppTheme(theme) },
            ) {
                // 小樣本：那個風格的底色、主色按鈕與四段課表色
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tp.bg)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(tp.sun))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (c in tp.stages) Box(modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(5.dp)).background(tp.accent))
                }
                Text(text = theme.nameZh, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = p.ink)
                Text(text = theme.descZh, style = MaterialTheme.typography.labelSmall, color = p.inkSoft, textAlign = TextAlign.Center)
            }
        }
    }

    Text(text = stringResource(R.string.settings_mascot_title), style = MaterialTheme.typography.titleSmall, color = p.ink)
    for (row in MascotStyle.values().toList().chunked(2)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (style in row) {
                OptionCard(
                    modifier = Modifier.weight(1f),
                    selected = settings.mascotStyle == style,
                    onClick = { AppSettings.setMascotStyle(style) },
                ) {
                    MascotIdleImage(style = style, contentDescription = style.nameZh, modifier = Modifier.height(120.dp))
                    Text(text = "${style.nameZh}（${style.kindZh}）", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = p.ink)
                }
            }
        }
    }

    Text(text = stringResource(R.string.settings_scene_title), style = MaterialTheme.typography.titleSmall, color = p.ink)
    for (row in Scene.values().toList().chunked(2)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (scene in row) {
                OptionCard(
                    modifier = Modifier.weight(1f),
                    selected = settings.scenePref == ScenePref.Fixed(scene),
                    onClick = { AppSettings.setScenePref(ScenePref.Fixed(scene)) },
                ) {
                    SceneryView(
                        scene = scene,
                        meters = { 0.0 },
                        moving = false,
                        modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Text(text = scene.nameZh, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = p.ink)
                }
            }
        }
    }
    OptionCard(
        modifier = Modifier.fillMaxWidth(),
        selected = settings.scenePref == ScenePref.Daily,
        onClick = { AppSettings.setScenePref(ScenePref.Daily) },
    ) {
        Text(text = stringResource(R.string.settings_scene_daily), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = p.ink)
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.settings_scene_moving),
            style = MaterialTheme.typography.bodyLarge,
            color = p.ink,
        )
        Switch(checked = settings.sceneryMoving, onCheckedChange = { AppSettings.setSceneryMoving(it) })
    }
    Text(text = stringResource(R.string.settings_scene_help), style = MaterialTheme.typography.bodySmall, color = p.inkSoft)
}

/** 可選取的卡片；選中時外框用主色。 */
@Composable
private fun OptionCard(
    modifier: Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
            .border(width = if (selected) 3.dp else 1.dp, color = if (selected) p.accent else p.line, shape = RoundedCornerShape(18.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}
