package com.cornming.slowjog.scenery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneryLogicTest {

    @Test
    fun fixedSceneNeverChanges() {
        val pref = ScenePref.Fixed(Scene.CITY)
        assertEquals(Scene.CITY, SceneryLogic.sceneFor(pref, 0))
        assertEquals(Scene.CITY, SceneryLogic.sceneFor(pref, 12345))
    }

    @Test
    fun dailyRotatesThroughAllScenesOnePerDay() {
        val seen = (0L until 4L).map { SceneryLogic.sceneFor(ScenePref.Daily, 20_000 + it) }
        assertEquals(Scene.values().toSet(), seen.toSet())
        assertEquals(SceneryLogic.sceneFor(ScenePref.Daily, 20_000), SceneryLogic.sceneFor(ScenePref.Daily, 20_004))
    }

    @Test
    fun prefRoundTripsThroughItsId() {
        assertEquals(ScenePref.Daily, ScenePref.fromId(ScenePref.Daily.id))
        assertEquals(ScenePref.Fixed(Scene.SEASIDE), ScenePref.fromId(ScenePref.Fixed(Scene.SEASIDE).id))
        assertEquals(ScenePref.Fixed(Scene.RIVERSIDE), ScenePref.fromId(null)) // 沒設定時預設河濱公園
        assertEquals(ScenePref.Fixed(Scene.RIVERSIDE), ScenePref.fromId("unknown"))
    }

    @Test
    fun offsetFollowsDistanceAndLayerSpeed() {
        assertEquals(0f, SceneryLogic.layerOffset(0.0, 1f), 1e-3f)
        assertEquals(90f, SceneryLogic.layerOffset(1.0, 1f), 1e-3f)
        assertEquals(45f, SceneryLogic.layerOffset(1.0, 0.5f), 1e-3f)
    }

    @Test
    fun offsetWrapsWithinOneTile() {
        // 10 公尺 = 900 單位，超過一段 600 就從頭接續
        assertEquals(300f, SceneryLogic.layerOffset(10.0, 1f), 1e-2f)
        val o = SceneryLogic.layerOffset(1_234.5, 1f)
        assertTrue(o >= 0f && o < SceneryLogic.TILE_W)
    }
}
