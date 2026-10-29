package com.cornming.slowjog.voice

import com.cornming.slowjog.mascot.CoachCue
import com.cornming.slowjog.mascot.Move
import com.cornming.slowjog.plan.Stage
import com.cornming.slowjog.plan.StageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceLinesTest {

    @Test
    fun idsAreUniqueAndSafeFileNames() {
        val ids = VoiceLines.catalog().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (id in ids) assertTrue(id, Regex("[a-z0-9_]+").matches(id))
    }

    @Test
    fun everyLineHasScriptAndFallback() {
        for (line in VoiceLines.catalog()) {
            assertTrue(line.id, line.text.isNotBlank())
            assertTrue(line.id, line.fallback.isNotBlank())
        }
    }

    @Test
    fun catalogCoversEveryMoveAndCoachCue() {
        val ids = VoiceLines.catalog().map { it.id }.toSet()
        for (m in Move.values()) assertTrue(m.id, "move_${m.id}" in ids)
        assertTrue("coach_switch" in ids && "coach_30s" in ids && "coach_10s" in ids)
        for (n in 1..5) assertTrue("count_$n" in ids)
        for (kind in StageKind.values()) assertTrue(kind.name, VoiceLines.stage(Stage(kind, 5)).id in ids)
    }

    @Test
    fun coachCuesMapToLines() {
        assertEquals("coach_switch", VoiceLines.coach(CoachCue(CoachCue.Kind.SWITCH_SIDE)).id)
        assertEquals("count_3", VoiceLines.coach(CoachCue(CoachCue.Kind.COUNT, 3)).id)
        assertEquals("三", VoiceLines.coach(CoachCue(CoachCue.Kind.COUNT, 3)).fallback)
    }

    @Test
    fun stageClipSkipsMinutesButFallbackKeepsThem() {
        val line = VoiceLines.stage(Stage(StageKind.WARMUP, 5))
        assertEquals("stage_warmup", line.id)
        assertEquals("暖身開始，5 分鐘", line.fallback)
        assertTrue(!line.text.contains("5") && !line.text.contains("分鐘"))
    }

    @Test
    fun chineseNumbers() {
        assertEquals("五", VoiceLines.chineseNumber(5))
        assertEquals("十", VoiceLines.chineseNumber(10))
        assertEquals("十五", VoiceLines.chineseNumber(15))
        assertEquals("二十", VoiceLines.chineseNumber(20))
        assertEquals("三十五", VoiceLines.chineseNumber(35))
        assertEquals("六十", VoiceLines.chineseNumber(60))
    }

    @Test
    fun elapsedLine() {
        val line = VoiceLines.elapsed(15)
        assertEquals("elapsed_15", line.id)
        assertEquals("已經十五分鐘囉，繼續保持！", line.text)
        assertEquals("已經 15 分鐘", line.fallback)
    }

    @Test
    fun cheerIdsAreNumberedFromOne() {
        assertEquals("cheer_01", VoiceLines.cheer(0).id)
        assertEquals("cheer_24", VoiceLines.cheer(23).id)
    }

    /** 語音稿 tools/voice/script.csv 要和程式裡的話一致，否則產生出來的語音檔會對不上。 */
    @Test
    fun scriptFileMatchesTheCatalog() {
        val file = listOf("../tools/voice/script.csv", "tools/voice/script.csv").map { File(it) }.firstOrNull { it.exists() }
        if (file == null) return // 找不到檔案（例如在別的目錄執行）就略過
        val rows = file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }.map {
            val comma = it.indexOf(',')
            it.substring(0, comma) to it.substring(comma + 1).trim().removeSurrounding("\"")
        }
        assertEquals(VoiceLines.catalog().map { it.id to it.text }, rows)
    }
}
