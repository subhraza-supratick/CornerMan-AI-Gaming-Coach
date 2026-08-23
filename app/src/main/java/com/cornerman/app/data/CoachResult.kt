package com.cornerman.app.data

/** Evidence-first coaching result returned by OpenRouter. */
data class CoachResult(
    val validGameScreen: Boolean,
    val confidence: Double,
    val decisionScore: Int,
    val rootMistake: String,
    val whyYouDied: String,
    val biggestMistake: String,
    val betterPlay: String,
    val evidence: List<String>,
    val nextFightRules: List<String>,
    val iglVerdict: String,
    val rejectionReason: String? = null,
    // Map specific fields
    val yourPlaySummary: String? = null,
    val cornermanRecommends: String? = null,
    val nextGamePlan: String? = null
)
