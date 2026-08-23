package com.cornerman.app.data

import android.content.Context
import android.graphics.Bitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

/**
 * Simple OpenRouter vision client for CORNERMAN.
 *
 * Deliberately uses the basic /chat/completions + image_url format first.
 * We avoid provider-specific routing and strict JSON-schema requirements so
 * the first real AI request is as compatible as possible across providers.
 */
object OpenRouterApi {

    private const val ENDPOINT =
        "https://openrouter.ai/api/v1/chat/completions"

    private const val MODEL = "openai/gpt-4o-mini"

    private val SYSTEM_PROMPT = """
        You are CORNERMAN, an elite AI In-Game Leader for mobile Battle Royale shooters.

        Your job is to find the ONE decision that most likely lost the fight and turn it
        into a reusable rule for the next fight.

        EVIDENCE-FIRST RULES:
        - First decide whether the image is recognizable mobile/PC Battle Royale or FPS gameplay.
        - If the image is a person, a room, a landscape, a meme, or generic non-gaming content, YOU MUST REJECT IT.
        - Never invent enemy count, weapon, distance, teammate, rotation, map location,
          damage, audio cue, or an event that is not visible.
        - If something is uncertain, say "not visible" and lower confidence.
        - Tactical recommendations may use general game knowledge, but do not present
          that knowledge as something observed in the screenshot.
        - Prefer ONE root mistake, not a list of mistakes.
        - Decision score measures decision quality, not raw aim skill.
        - Be concise, direct and competitive.

        ROOT MISTAKE MUST BE ONE OF:
        Positioning, Exposure, Timing, Aim, Cover, Rotation, Target Selection,
        Resource Management, Information, Fight Selection.

        VALIDATION REJECTION:
        If valid_game_screen is false, explain exactly why in rejection_reason.
        Example: "This appears to be a personal photo, not gameplay." or "The screenshot is too blurry to identify tactical evidence."

        Return ONLY valid JSON. No markdown fences. No explanation outside JSON.
    """.trimIndent()

    suspend fun analyze(
        apiKey: String,
        bitmap: Bitmap,
        game: String,
        situation: String,
        playerNote: String,
        mediaContext: SanitizedMediaContext? = null,
        storyboard: List<Bitmap>? = null
    ): CoachResult = withContext(Dispatchers.IO) {

        require(apiKey.isNotBlank()) { "OpenRouter API key is empty." }

        val image = bitmapToBase64Jpeg(bitmap)
        val storyboardBase64 = storyboard?.map { bitmapToBase64Jpeg(it) }

        val prompt = """
            GAME: $game
            SITUATION: $situation
            PLAYER NOTE: ${playerNote.ifBlank { "None provided" }}
            MEDIA CONTEXT: ${mediaContext?.let { "Type: ${it.mediaType}, Size: ${it.width}x${it.height}, Orientation: ${it.orientation}, Duration: ${it.durationMs ?: "N/A"}ms" } ?: "Not available"}
            
            ${if (!storyboardBase64.isNullOrEmpty()) "ANALYZING VIDEO STORYBOARD: The attached multiple images are sequential frames from a short gameplay clip." else "ANALYZING SCREENSHOT: The attached image is a static gameplay screenshot."}

            Analyze the provided gameplay evidence like an elite IGL.
            Use the media context and visual data to identify the earliest losing decision.

            Return exactly this JSON shape:
            {
              "valid_game_screen": true,
              "confidence": 0.0,
              "decision_score": 0,
              "root_mistake": "Exposure",
              "why_you_died": "...",
              "biggest_mistake": "...",
              "better_play": "...",
              "evidence": ["...", "..."],
              "next_fight_rules": ["...", "...", "..."],
              "igl_verdict": "...",
              "rejection_reason": ""
            }

            Requirements:
            - decision_score: 0 to 100.
            - confidence: 0.0 to 1.0.
            - If video/storyboard is provided, look for movement or decision changes across frames.
            - If non-gameplay, explain why in rejection_reason.
        """.trimIndent()

        val request = buildMultimodalRequest(image, storyboardBase64, prompt)
        val raw = post(apiKey, request)
        parseResponse(raw)
    }

    private fun buildMultimodalRequest(image: String, storyboard: List<String>?, prompt: String): JSONObject {
        val content = JSONArray()
            .put(JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            })
            
        if (storyboard.isNullOrEmpty()) {
            content.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image"))
            })
        } else {
            storyboard.forEach { frame ->
                content.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$frame"))
                })
            }
        }

        return JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            }).put(JSONObject().apply {
                put("role", "user")
                put("content", content)
            }))
            put("temperature", 0.2)
            put("max_tokens", 1000)
        }
    }

    suspend fun analyzeMap(
        apiKey: String,
        timeline: MatchTimeline
    ): CoachResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "OpenRouter API key is empty." }

        val prompt = """
            CORNERMAP TACTICAL REVIEW
            GAME: ${timeline.gameId}
            MAP: ${timeline.mapId}
            TIMELINE:
            - Landing: ${timeline.landingPOI} (Drop Logic: ${timeline.dropReason})
            - Last Zone: ${timeline.lastZonePOI}
            - Markers: ${timeline.markers.joinToString { "${it.type} (x:${"%.2f".format(it.x)}, y:${"%.2f".format(it.y)})" }}
            - Intent: ${timeline.playerIntent}
            - Action: ${timeline.whatHappened}
            - Outcome: ${timeline.outcome}

            Analyze this match timeline like an elite IGL. 
            Compare the player's intent with the outcome and map context.
            Suggest the most efficient rotation path from ${timeline.landingPOI} toward ${timeline.lastZonePOI} given the player's intent.
            Identify the ONE rotation or fight decision that most likely led to the outcome.

            Return exactly this JSON shape:
            {
              "decision_score": 0,
              "root_mistake": "Rotation",
              "why_you_died": "...",
              "biggest_mistake": "...",
              "better_play": "...",
              "evidence": ["...", "..."],
              "next_fight_rules": ["...", "...", "..."],
              "igl_verdict": "...",
              "your_play_summary": "...",
              "cornerman_recommends": "...",
              "next_game_plan": "..."
            }

            Requirements:
            - decision_score: 0-100.
            - your_play_summary: A punchy recap of the player's choices.
            - cornerman_recommends: A professional, lower-risk alternative strategy covering the landing-to-last-zone route.
            - next_game_plan: A specific directive for the next match on this map.
        """.trimIndent()

        val request = buildTextRequest(prompt)
        val raw = post(apiKey, request)
        parseResponse(raw)
    }

    private fun buildTextRequest(prompt: String): JSONObject {
        return JSONObject().apply {
            put("model", MODEL)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
            )
            put("temperature", 0.3)
        }
    }



    private fun post(apiKey: String, body: JSONObject): String {
        val connection =
            (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("HTTP-Referer", "https://cornerman.app")
                setRequestProperty("X-Title", "CORNERMAN AI IGL")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 90000
            }

        try {
            connection.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use {
                    it.write(body.toString())
                }
            }

            val code = connection.responseCode
            val stream =
                if (code in 200..299) connection.inputStream
                else connection.errorStream

            val response = try {
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                "Failed to read stream: ${e.message}"
            }

            if (code !in 200..299) {
                throw RuntimeException(
                    "OpenRouter HTTP $code: ${friendlyError(response)}"
                )
            }

            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(raw: String): CoachResult {
        val root = JSONObject(raw)

        root.optJSONObject("error")?.let {
            throw RuntimeException(
                it.optString("message", "OpenRouter returned an error.")
            )
        }

        val choices =
            root.optJSONArray("choices")
                ?: throw RuntimeException("OpenRouter returned no choices.")

        if (choices.length() == 0) {
            throw RuntimeException("OpenRouter returned an empty choices array.")
        }

        val message = choices.getJSONObject(0).optJSONObject("message")
            ?: throw RuntimeException("OpenRouter response has no message.")

        val content = message.optString("content", "").trim()

        if (content.isBlank()) {
            throw RuntimeException("The vision model returned an empty response.")
        }

        val cleaned = cleanJson(content)

        val json = try {
            JSONObject(cleaned)
        } catch (_: Exception) {
            throw RuntimeException(
                "Model returned non-JSON output: ${content.take(600)}"
            )
        }

        val evidence =
            json.optJSONArray("evidence")?.toStringList() ?: emptyList()

        val rules =
            json.optJSONArray("next_fight_rules")?.toStringList() ?: emptyList()

        return CoachResult(
            validGameScreen = json.optBoolean("valid_game_screen", true),
            confidence =
                json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
            decisionScore =
                json.optInt("decision_score", 0).coerceIn(0, 100),
            rootMistake =
                json.optString("root_mistake", "Information"),
            whyYouDied =
                json.optString(
                    "why_you_died",
                    "The analysis does not provide enough evidence."
                ),
            biggestMistake =
                json.optString(
                    "biggest_mistake",
                    "Insufficient evidence for a confident diagnosis."
                ),
            betterPlay =
                json.optString(
                    "better_play",
                    "Play from stronger information and cover."
                ),
            evidence = evidence,
            nextFightRules = rules,
            iglVerdict =
                json.optString(
                    "igl_verdict",
                    "Play the next fight with more information."
                ),
            rejectionReason =
                json.optString("rejection_reason")
                    .takeIf { it.isNotBlank() },
            yourPlaySummary = json.optString("your_play_summary"),
            cornermanRecommends = json.optString("cornerman_recommends"),
            nextGamePlan = json.optString("next_game_plan")
        )
    }

    private fun cleanJson(content: String): String {
        var cleaned = content.trim()

        if (cleaned.startsWith("```")) {
            cleaned = cleaned
                .removePrefix("```json")
                .removePrefix("```")
                .trim()

            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```").trim()
            }
        }

        val first = cleaned.indexOf('{')
        val last = cleaned.lastIndexOf('}')

        if (first in 0 until last) {
            cleaned = cleaned.substring(first, last + 1)
        }

        return cleaned
    }

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (i in 0 until length()) {
                val value = optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }

    private fun friendlyError(raw: String): String =
        runCatching {
            JSONObject(raw)
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: raw.take(800)
        }.getOrDefault(raw.take(800))

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val scaled = scale(bitmap)
        val output = ByteArrayOutputStream()

        scaled.compress(
            Bitmap.CompressFormat.JPEG,
            82,
            output
        )

        return Base64.encodeToString(
            output.toByteArray(),
            Base64.NO_WRAP
        )
    }

    private fun scale(bitmap: Bitmap, maxSide: Int = 1600): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)

        if (largest <= maxSide) return bitmap

        val ratio = maxSide.toFloat() / largest.toFloat()

        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }
}

/** Small dependency-free encrypted preference helper for the demo API key. */
object SecurePrefs {
    private const val PREFS = "cornerman_secure"
    private const val KEY_NAME = "openrouter_api_key"
    private const val KS = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KS).apply { load(null) }
        val existing = keyStore.getKey(KEY_NAME, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance("AES", KS)
        val spec = KeyGenParameterSpec.Builder(
            KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    fun saveApiKey(context: Context, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + encrypted
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, Base64.encodeToString(combined, Base64.NO_WRAP))
            .apply()
    }

    fun loadApiKey(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null) ?: return ""
        return runCatching {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}