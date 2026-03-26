package com.skill2career.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.skill2career.model.CvSummarySections
import com.skill2career.model.JobItem
import com.skill2career.model.JobSearchRequest
import com.skill2career.model.Profile
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GeminiService(
    private val geminiWebClient: WebClient,
    @Value("\${gemini.api.key}") private val apiKey: String
) {

    private val objectMapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(GeminiService::class.java)

    fun generateSummary(profile: Profile): CvSummarySections {
        val prompt = """
            Create structured CV content for this candidate.

            Name: ${profile.name}
            Target role: ${profile.targetRole ?: "Not specified"}
            Skills: ${profile.skills.joinToString(", ")}
            Experience: ${profile.experience}
            Years of experience: ${profile.yearsOfExperience ?: "Not specified"}
            Location: ${profile.location ?: "Not specified"}
            Work authorization: ${profile.workAuthorization ?: "Not specified"}
            Projects: ${profile.projects.joinToString(", ").ifBlank { "Not specified" }}
            Certifications: ${profile.certifications.joinToString(", ").ifBlank { "Not specified" }}
            Languages: ${profile.languages.joinToString(", ").ifBlank { "Not specified" }}
            Education: ${profile.education}

            Return ONLY valid JSON with fields:
            headline (string),
            summary (string),
            keySkills (array of strings),
            experienceBullets (array of strings, 3-5 bullets),
            educationSection (string),
            atsKeywords (array of strings).
            Do not include markdown or commentary.
        """.trimIndent()

        val raw = executePrompt(prompt, "{}")
        val parsed = parseSummaryJson(raw)

        return parsed ?: CvSummarySections(
            headline = "Professional Profile",
            summary = if (raw.contains("Gemini unavailable")) raw else "Failed to generate summary",
            keySkills = profile.skills,
            experienceBullets = listOf(profile.experience),
            educationSection = profile.education,
            atsKeywords = profile.skills
        )
    }

    fun generateJobsForSearch(request: JobSearchRequest): List<JobItem> {
        val skillsText = request.skills.joinToString(", ").ifBlank { "Not specified" }
        val roleKeywordsText = request.roleKeywords.joinToString(", ").ifBlank { "Not specified" }

        val prompt = """
            You are a job search engine assistant.
            Generate realistic, currently-open roles that best match this profile.

            Candidate skills: $skillsText
            Preferred location: ${request.location ?: "Any / remote-friendly"}
            Role keywords: $roleKeywordsText

            Rules:
            - Return between 8 and 12 jobs.
            - Prioritize strong skill overlap first.
            - Include a mix of remote + location-relevant jobs when possible.
            - description must be 1-2 concise sentences.
            - requiredSkills should be 4-8 concrete skills.
            - roleKeywords should be concise and relevant.
            - source should be a recognizable board name (LinkedIn, Indeed, Greenhouse, Lever, company-careers).
            - url should be a direct job link when possible; otherwise use a searchable board URL.

            Return ONLY valid JSON as an array of objects with fields:
            id, title, company, location, description, requiredSkills (array), roleKeywords (array), source, url.
            Do not include markdown or commentary.
        """.trimIndent()

        val raw = executePrompt(prompt, "[]")
        val json = extractJsonArray(raw)

        val parsed = runCatching {
            objectMapper.readValue(json, object : TypeReference<List<JobItem>>() {})
        }.getOrDefault(emptyList())

        val normalized = parsed
            .map { normalizeJobItem(it, request) }
            .filter { it.title.isNotBlank() && it.company.isNotBlank() }
            .distinctBy { listOf(it.title.lowercase(), it.company.lowercase(), it.location.lowercase()).joinToString("|") }

        if (normalized.isNotEmpty()) return normalized

        return fallbackJobsFromRequest(request)
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

    private fun normalizeJobItem(job: JobItem, request: JobSearchRequest): JobItem {
        val normalizedSkills = job.requiredSkills
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { request.skills.take(6) }

        val normalizedKeywords = job.roleKeywords
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { request.roleKeywords.take(6) }

        val normalizedTitle = job.title.ifBlank { request.roleKeywords.firstOrNull() ?: "Software Engineer" }
        val normalizedCompany = job.company.ifBlank { "Confidential Company" }

        val generatedId = if (job.id.isBlank()) UUID.randomUUID().toString() else job.id
        val generatedLocation = job.location.ifBlank { request.location ?: "Remote" }

        val generatedUrl = when {
            !job.url.isNullOrBlank() -> job.url
            job.source.startsWith("http") -> job.source
            else -> {
                val query = listOf(normalizedTitle, normalizedCompany, generatedLocation, "job")
                    .joinToString(" ")
                "https://www.google.com/search?q=${query.replace(" ", "+")}"
            }
        }

        return job.copy(
            id = generatedId,
            title = normalizedTitle,
            company = normalizedCompany,
            location = generatedLocation,
            description = job.description.ifBlank { "Role aligned with the candidate's profile and required skill set." },
            requiredSkills = normalizedSkills,
            roleKeywords = normalizedKeywords,
            source = job.source.ifBlank { "company-careers" },
            url = generatedUrl
        )
    }

    private fun fallbackJobsFromRequest(request: JobSearchRequest): List<JobItem> {
        val baseSkills = request.skills.takeIf { it.isNotEmpty() } ?: listOf("Communication", "Problem Solving")
        val baseKeywords = request.roleKeywords.takeIf { it.isNotEmpty() } ?: listOf("Engineer", "Analyst")
        val location = request.location ?: "Remote"

        return baseKeywords.take(6).mapIndexed { index, keyword ->
            val title = "$keyword ${if (index % 2 == 0) "Specialist" else "Engineer"}"
            val company = "Hiring Company ${index + 1}"
            val query = listOf(title, company, location, "jobs").joinToString("+")

            JobItem(
                id = "fallback-${index + 1}",
                title = title,
                company = company,
                location = location,
                description = "Potentially relevant opportunity generated from your profile while live search results were sparse.",
                requiredSkills = baseSkills.take(6),
                roleKeywords = baseKeywords,
                source = "fallback-search",
                url = "https://www.google.com/search?q=$query"
            )
        }
    }

    private fun parseSummaryJson(raw: String): CvSummarySections? {
        val withoutFence = raw.replace("```json", "").replace("```", "").trim()
        val start = withoutFence.indexOf('{')
        val end = withoutFence.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        val json = withoutFence.substring(start, end + 1)
        return runCatching {
            objectMapper.readValue(json, CvSummarySections::class.java)
        }.getOrNull()
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
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.3,
                "topP" to 0.9,
                "topK" to 40,
                "maxOutputTokens" to 2048
            )
        )

        return try {
            val response = geminiWebClient.post()
                .uri("/models/gemini-flash-latest:generateContent")
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus({ status -> status.isError }) { clientResponse ->
                    clientResponse.bodyToMono(String::class.java)
                        .map { body -> RuntimeException("Gemini API ${clientResponse.statusCode().value()}: $body") }
                }
                .bodyToMono(Map::class.java)
                .block()

            val candidates = response?.get("candidates") as? List<*>
            val first = candidates?.firstOrNull() as? Map<*, *>
            val content = first?.get("content") as? Map<*, *>
            val parts = content?.get("parts") as? List<*>
            val textObj = parts?.firstOrNull() as? Map<*, *>

            textObj?.get("text")?.toString() ?: fallback
        } catch (error: Exception) {
            logger.warn("Gemini call failed: ${error.message}")
            "$fallback | Gemini unavailable (${error.message?.take(180) ?: "unknown error"})"
        }
    }
}
