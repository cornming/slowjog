package com.cornming.slowjog.plan

/** 什麼時候才算「這次運動結束」。純邏輯，有單元測試。 */
object PlanEnd {

    /**
     * 要不要檢查「達成目標」。沒有啟用課表時隨時檢查；有課表時要等課表全部做完才檢查，
     * 否則目標（時間或距離）比課表先到，會在肌力訓練、伸展還沒做之前就停掉節拍器與音樂、跳出結束提示。
     */
    fun targetCheckApplies(plan: WorkoutPlan?, elapsedMs: Long): Boolean =
        plan == null || plan.position(elapsedMs).stage == null
}
