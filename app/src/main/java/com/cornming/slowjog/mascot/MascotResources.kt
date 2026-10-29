package com.cornming.slowjog.mascot

import com.cornming.slowjog.R

/** 每種風格的影格表資源（由 mascot/render.js 與 to-webp.py 產生）。 */
data class StyleRes(val run: Int, val idle: Int, val moves: Map<Move, Int>)

object MascotResources {
    private val ALL = mapOf(
        MascotStyle.BLUE to StyleRes(
            run = R.drawable.mascot_blue_run,
            idle = R.drawable.mascot_blue_idle,
            moves = mapOf(
                Move.MARCH to R.drawable.mascot_blue_m_march,
                Move.ARM_CIRCLE to R.drawable.mascot_blue_m_armcircle,
                Move.TWIST to R.drawable.mascot_blue_m_twist,
                Move.SQUAT to R.drawable.mascot_blue_m_squat,
                Move.DEEP_SQUAT to R.drawable.mascot_blue_m_deepsquat,
                Move.LUNGE to R.drawable.mascot_blue_m_lunge,
                Move.CALF_RAISE to R.drawable.mascot_blue_m_calfraise,
                Move.KNEE_ELBOW to R.drawable.mascot_blue_m_kneeelbow,
                Move.SIDE_LEG to R.drawable.mascot_blue_m_sideleg,
                Move.CALF to R.drawable.mascot_blue_m_calf,
                Move.QUAD to R.drawable.mascot_blue_m_quad,
                Move.FORWARD to R.drawable.mascot_blue_m_forward,
                Move.SIDEBEND to R.drawable.mascot_blue_m_sidebend,
                Move.CROSSARM to R.drawable.mascot_blue_m_crossarm,
            ),
        ),
        MascotStyle.ORANGE to StyleRes(
            run = R.drawable.mascot_orange_run,
            idle = R.drawable.mascot_orange_idle,
            moves = mapOf(
                Move.MARCH to R.drawable.mascot_orange_m_march,
                Move.ARM_CIRCLE to R.drawable.mascot_orange_m_armcircle,
                Move.TWIST to R.drawable.mascot_orange_m_twist,
                Move.SQUAT to R.drawable.mascot_orange_m_squat,
                Move.DEEP_SQUAT to R.drawable.mascot_orange_m_deepsquat,
                Move.LUNGE to R.drawable.mascot_orange_m_lunge,
                Move.CALF_RAISE to R.drawable.mascot_orange_m_calfraise,
                Move.KNEE_ELBOW to R.drawable.mascot_orange_m_kneeelbow,
                Move.SIDE_LEG to R.drawable.mascot_orange_m_sideleg,
                Move.CALF to R.drawable.mascot_orange_m_calf,
                Move.QUAD to R.drawable.mascot_orange_m_quad,
                Move.FORWARD to R.drawable.mascot_orange_m_forward,
                Move.SIDEBEND to R.drawable.mascot_orange_m_sidebend,
                Move.CROSSARM to R.drawable.mascot_orange_m_crossarm,
            ),
        ),
        MascotStyle.PIXEL to StyleRes(
            run = R.drawable.mascot_pixel_run,
            idle = R.drawable.mascot_pixel_idle,
            moves = mapOf(
                Move.MARCH to R.drawable.mascot_pixel_m_march,
                Move.ARM_CIRCLE to R.drawable.mascot_pixel_m_armcircle,
                Move.TWIST to R.drawable.mascot_pixel_m_twist,
                Move.SQUAT to R.drawable.mascot_pixel_m_squat,
                Move.DEEP_SQUAT to R.drawable.mascot_pixel_m_deepsquat,
                Move.LUNGE to R.drawable.mascot_pixel_m_lunge,
                Move.CALF_RAISE to R.drawable.mascot_pixel_m_calfraise,
                Move.KNEE_ELBOW to R.drawable.mascot_pixel_m_kneeelbow,
                Move.SIDE_LEG to R.drawable.mascot_pixel_m_sideleg,
                Move.CALF to R.drawable.mascot_pixel_m_calf,
                Move.QUAD to R.drawable.mascot_pixel_m_quad,
                Move.FORWARD to R.drawable.mascot_pixel_m_forward,
                Move.SIDEBEND to R.drawable.mascot_pixel_m_sidebend,
                Move.CROSSARM to R.drawable.mascot_pixel_m_crossarm,
            ),
        ),
        MascotStyle.SHIBA to StyleRes(
            run = R.drawable.mascot_shiba_run,
            idle = R.drawable.mascot_shiba_idle,
            moves = mapOf(
                Move.MARCH to R.drawable.mascot_shiba_m_march,
                Move.ARM_CIRCLE to R.drawable.mascot_shiba_m_armcircle,
                Move.TWIST to R.drawable.mascot_shiba_m_twist,
                Move.SQUAT to R.drawable.mascot_shiba_m_squat,
                Move.DEEP_SQUAT to R.drawable.mascot_shiba_m_deepsquat,
                Move.LUNGE to R.drawable.mascot_shiba_m_lunge,
                Move.CALF_RAISE to R.drawable.mascot_shiba_m_calfraise,
                Move.KNEE_ELBOW to R.drawable.mascot_shiba_m_kneeelbow,
                Move.SIDE_LEG to R.drawable.mascot_shiba_m_sideleg,
                Move.CALF to R.drawable.mascot_shiba_m_calf,
                Move.QUAD to R.drawable.mascot_shiba_m_quad,
                Move.FORWARD to R.drawable.mascot_shiba_m_forward,
                Move.SIDEBEND to R.drawable.mascot_shiba_m_sidebend,
                Move.CROSSARM to R.drawable.mascot_shiba_m_crossarm,
            ),
        ),
    )

    fun of(style: MascotStyle): StyleRes = ALL.getValue(style)
}
