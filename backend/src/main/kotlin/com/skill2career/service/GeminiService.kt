package com.skill2career.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.skill2career.model.JobItem
import com.skill2career.model.JobSearchRequest
import com.skill2career.model.Profile
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GeminiService(
    private val geminiWebClient: WebClient,
    @Value("\${gemini.api.key}") private val apiKey: String
) {

    private val objectMapper = jacksonObjectMapper()

    fun generateSummary(profile: Profile): String {
        val prompt = """
            Create a professional CV summary for:

            Name: ${profile.name}
            Skills: ${profile.skills.joinToString(", ")}
            Experience: ${profile.experience}
            Education: ${profile.education}

            Keep it concise and professional.
        """.trimIndent()

        return executePrompt(prompt, "Failed to generate summary")
    }

    fun generateJobsForSearch(request: JobSearchRequest): List<JobItem> {
        val prompt = """
            Find current job opportunities that match this search profile.

            Skills: ${request.skills.joinToString(", ").ifBlank { "Not specified" }}
            Location: ${request.location ?: "Any"}
            Role keywords: ${request.roleKeywords.joinToString(", ").ifBlank { "Not specified" }}

            Return ONLY valid JSON as an array of objects with fields:
            id, title, company, location, description, requiredSkills (array), roleKeywords (array), source.
            Do not include markdown or commentary.
            Return up to 10 jobs.
        """.trimIndent()

        val raw = executePrompt(prompt, "[]")
        val json = extractJsonArray(raw)

        return runCatching {
            objectMapper.readValue(json, object : TypeReference<List<JobItem>>() {})
        }.getOrDefault(emptyList())
    }

    fun generateMatchReasoning(
        cvOrProfile: String,
        job: JobItem,
        overlapPercent: Int,
        missingSkills: List<String>
    ): String {
        val prompt = """
            Explain job-candidate compatibility in 2 concise sentences.
            Candidate profile/CV: $cvOrProfile
            Job title: ${job.title}
            Job description: ${job.description}
            Required skills: ${job.requiredSkills.joinToString(", ")}
            Skill overlap percent: $overlapPercent
            Missing skills: ${missingSkills.joinToString(", ").ifBlank { "none" }}

            Keep it factual and avoid inventing skills.
        """.trimIndent()

        return executePrompt(prompt, "Reasoning unavailable")
    }

    private fun extractJsonArray(raw: String): String {
        val withoutFence = raw.replace("```json", "").replace("```", "").trim()
        val start = withoutFence.indexOf('[')
        val end = withoutFence.lastIndexOf(']')

        return if (start >= 0 && end > start) {
            withoutFence.substring(start, end + 1)
        } else {
            "[]"
        }
    }

    private fun executePrompt(prompt: String, fallback: String): String {
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
            .onErrorReturn(emptyMap<String, Any>())
            .block()

        val candidates = response?.get("candidates") as? List<*>
        val first = candidates?.firstOrNull() as? Map<*, *>
        val content = first?.get("content") as? Map<*, *>
        val parts = content?.get("parts") as? List<*>
        val textObj = parts?.firstOrNull() as? Map<*, *>

        return textObj?.get("text")?.toString() ?: fallback
    }
}
