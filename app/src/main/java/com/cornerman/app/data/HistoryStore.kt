package com.cornerman.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight local history; deliberately no cloud database for the hackathon MVP. */
data class ReviewSummary(
    val id: Long,
    val type: String, // "QUICK_IGL" or "CORNERMAP"
    val game: String,
    val situation: String,
    val score: Int,
    val mistake: String,
    val verdict: String,
    val timestamp: Long
)

object HistoryStore {
    private const val PREFS = "cornerman_history"
    private const val KEY = "reviews"
    private const val MAX = 30

    fun load(context: Context): List<ReviewSummary> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(ReviewSummary(
                        id = o.optLong("id"),
                        type = o.optString("type", "QUICK_IGL"),
                        game = o.optString("game", "Battle Royale"),
                        situation = o.optString("situation", "Death"),
                        score = o.optInt("score", 0),
                        mistake = o.optString("mistake", "Unknown"),
                        verdict = o.optString("verdict", ""),
                        timestamp = o.optLong("timestamp", 0L)
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, game: String, situation: String, result: CoachResult, type: String = "QUICK_IGL") {
        val items = load(context).toMutableList()
        val now = System.currentTimeMillis()
        items.add(0, ReviewSummary(
            id = now,
            type = type,
            game = game,
            situation = situation,
            score = result.decisionScore,
            mistake = result.rootMistake,
            verdict = result.iglVerdict,
            timestamp = now
        ))
        val array = JSONArray()
        items.take(MAX).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                put("game", item.game)
                put("situation", item.situation)
                put("score", item.score)
                put("mistake", item.mistake)
                put("verdict", item.verdict)
                put("timestamp", item.timestamp)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
