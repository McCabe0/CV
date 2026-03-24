package com.skill2career.service

import com.skill2career.model.Profile
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GeminiService(
    private val geminiWebClient: WebClient,
    @Value("\${gemini.api.key}") private val apiKey: String
) {

    fun generateSummary(profile: Profile): String {

        val prompt = """
            Create a professional CV summary for:

            Name: ${profile.name}
            Skills: ${profile.skills.joinToString(", ")}
            Experience: ${profile.experience}
            Education: ${profile.education}

            Keep it concise and professional.
        """.trimIndent()

        val requestBody = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            )
        )

        val response = geminiWebClient.post()
            .uri("/models/gemini-flash-latest:generateContent")
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        val candidates = response?.get("candidates") as? List<*>
        val first = candidates?.firstOrNull() as? Map<*, *>
        val content = first?.get("content") as? Map<*, *>
        val parts = content?.get("parts") as? List<*>
        val textObj = parts?.firstOrNull() as? Map<*, *>

        return textObj?.get("text")?.toString() ?: "Failed to generate summary"
    }
}
