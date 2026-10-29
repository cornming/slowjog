package com.cornming.slowjog.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEndTest {

    private val plan = WorkoutPlan.of(warmup = 5, jog = 15, strength = 6, cooldown = 4)

    @Test
    fun withoutPlanTargetIsCheckedAnytime() {
        assertTrue(PlanEnd.targetCheckApplies(null, 0))
        assertTrue(PlanEnd.targetCheckApplies(null, 99 * 60_000L))
    }

    @Test
    fun withPlanTargetWaitsUntilEveryStageIsDone() {
        assertFalse(PlanEnd.targetCheckApplies(plan, 0))
        assertFalse(PlanEnd.targetCheckApplies(plan, 12 * 60_000L)) // 超慢跑途中
        assertFalse(PlanEnd.targetCheckApplies(plan, 25 * 60_000L)) // 肌力訓練完、伸展還沒做
        assertFalse(PlanEnd.targetCheckApplies(plan, 30 * 60_000L - 1))
    }

    @Test
    fun withPlanTargetIsCheckedOnceThePlanIsDone() {
        assertTrue(PlanEnd.targetCheckApplies(plan, 30 * 60_000L))
        assertTrue(PlanEnd.targetCheckApplies(plan, 45 * 60_000L))
    }

    @Test
    fun skippedStagesDoNotCountAsLater() {
        // 肌力與伸展設 0 分鐘 ＝ 後面沒有課程，超慢跑做完就算結束。
        val short = WorkoutPlan.of(warmup = 5, jog = 15, strength = 0, cooldown = 0)
        assertFalse(PlanEnd.targetCheckApplies(short, 19 * 60_000L))
        assertTrue(PlanEnd.targetCheckApplies(short, 20 * 60_000L))
    }
}
