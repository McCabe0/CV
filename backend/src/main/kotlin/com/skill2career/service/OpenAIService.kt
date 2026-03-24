package com.skill2career.service

import com.skill2career.model.Profile
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class OpenAIService(
    private val webClient: WebClient,
    @Value("\${openai.api.key}") private val apiKey: String
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
            "model" to "gpt-4.1-mini",
            "input" to prompt
        )

        val response = webClient.post()
            .uri("/responses")
            .header("Authorization", "Bearer $apiKey")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        val output = response?.get("output") as? List<*>
        val first = output?.firstOrNull() as? Map<*, *>
        val content = first?.get("content") as? List<*>
        val textObj = content?.firstOrNull() as? Map<*, *>

        return textObj?.get("text")?.toString() ?: "Failed to generate summary"
    }
}
