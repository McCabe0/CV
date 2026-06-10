package com.skill2career.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.skill2career.model.CvSummarySections
import com.skill2career.model.JobItem
import com.skill2career.model.JobSearchRequest
import com.skill2career.model.Profile
import com.skill2career.model.WorkExperience
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Collections
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry

@Service
class GeminiService(
    private val geminiWebClient: WebClient,
    @Value("\${gemini.api.key}") private val apiKey: String,
    private val successCacheTtlMillis: Long = 10 * 60 * 1000L,
    private val maxCacheEntries: Int = 500,
    private val maxRetries: Long = 2,
    private val retryBackoffMillis: Long = 500,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) {

    // Tolerate extra fields Gemini may include that aren't part of our model.
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val logger = LoggerFactory.getLogger(GeminiService::class.java)

    private data class CachedResponse(val value: String, val expiresAtMillis: Long)

    // Access-ordered LinkedHashMap so the least-recently-used entry is evicted once we hit
    // capacity (a ConcurrentHashMap has no ordering and would evict an arbitrary entry).
    // Wrapped in a synchronized map because LinkedHashMap is not thread-safe on its own.
    private val promptCache: MutableMap<String, CachedResponse> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedResponse>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, CachedResponse>): Boolean =
                size > maxCacheEntries
        }
    )

    fun generateSummary(profile: Profile): CvSummarySections {
        val prompt = """
            You are an expert CV/resume writer. Using only the candidate details below,
            write polished, ATS-friendly CV content tailored to the target role.

            Candidate details
            - Name: ${profile.name}
            - Target role: ${profile.targetRole ?: "Not specified"}
            - Skills: ${profile.skills.joinToString(", ")}
            - Career summary: ${profile.experience}
            - Years of experience: ${profile.yearsOfExperience ?: "Not specified"}
            - Location: ${profile.location ?: "Not specified"}
            - Work authorization: ${profile.workAuthorization ?: "Not specified"}
            - Projects: ${profile.projects.joinToString(", ").ifBlank { "Not specified" }}
            - Certifications: ${profile.certifications.joinToString(", ").ifBlank { "Not specified" }}
            - Languages: ${profile.languages.joinToString(", ").ifBlank { "Not specified" }}
            - Education: ${profile.education}

            Work history
            ${formatWorkHistory(profile.workHistory)}

            Guidelines
            - Base experienceBullets primarily on the structured work history above, keeping each
              role's facts (employer, title, dates) accurate; use the career summary for context.
            - Tailor the wording and keywords to the target role when one is given.
            - Use a professional, confident tone; avoid first person ("I", "my").
            - Start each experience bullet with a strong action verb and quantify impact
              where the details support it.
            - Do NOT invent employers, job titles, dates, metrics, or skills that are not
              present or clearly implied by the details above.
            - headline: a concise, role-focused title (e.g. "Senior Data Analyst | Python & SQL").
            - summary: a 2-4 sentence professional summary.
            - keySkills: the 6-12 most relevant skills, prioritising matches to the target role.
            - experienceBullets: 3-6 achievement-oriented bullets drawn from the experience and projects.
            - educationSection: education and certifications, one item per line.
            - atsKeywords: 8-15 keywords a recruiter or ATS would search for this role.

            Return ONLY valid JSON with exactly these fields:
            headline (string),
            summary (string),
            keySkills (array of strings),
            experienceBullets (array of strings),
            educationSection (string),
            atsKeywords (array of strings).
            Do not include markdown, code fences, or commentary.
        """.trimIndent()

        val raw = executePrompt(prompt, "{}")
        val parsed = parseSummaryJson(raw)

        if (parsed == null) {
            logger.warn("Could not parse CV summary JSON from Gemini. Raw response: {}", raw.take(500))
        }

        return parsed ?: CvSummarySections(
            headline = "Professional Profile",
            summary = if (raw.contains("Gemini unavailable")) raw else "Failed to generate summary",
            keySkills = profile.skills,
            experienceBullets = listOf(profile.experience),
            educationSection = profile.education,
            atsKeywords = profile.skills
        )
    }

    private fun formatWorkHistory(workHistory: List<WorkExperience>): String =
        if (workHistory.isEmpty()) {
            "Not specified"
        } else {
            workHistory.joinToString("\n") { role ->
                val period = listOfNotNull(role.startDate, role.endDate).joinToString(" to ")
                val bullets = role.bullets.joinToString("; ")
                "- ${role.title} at ${role.company} [$period]: $bullets"
            }
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
            - Do NOT include a url field; the application generates a reliable search link itself.

            Return ONLY valid JSON as an array of objects with fields:
            id, title, company, location, description, requiredSkills (array), roleKeywords (array), source.
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

        logger.warn("No usable jobs parsed from Gemini; using fallback. Raw response: {}", raw.take(500))
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

        return job.copy(
            id = generatedId,
            title = normalizedTitle,
            company = normalizedCompany,
            location = generatedLocation,
            description = job.description.ifBlank { "Role aligned with the candidate's profile and required skill set." },
            requiredSkills = normalizedSkills,
            roleKeywords = normalizedKeywords,
            source = job.source.ifBlank { "company-careers" },
            // The model frequently hallucinates dead "direct" links, so we always build a
            // reliable search URL that lands on real listings instead of trusting job.url.
            url = jobSearchUrl(normalizedTitle, normalizedCompany, generatedLocation)
        )
    }

    private fun jobSearchUrl(title: String, company: String, location: String): String {
        val query = listOf(title, company, location, "job")
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        return "https://www.google.com/search?q=$encoded"
    }

    private fun fallbackJobsFromRequest(request: JobSearchRequest): List<JobItem> {
        val baseSkills = request.skills.takeIf { it.isNotEmpty() } ?: listOf("Communication", "Problem Solving")
        val baseKeywords = request.roleKeywords.takeIf { it.isNotEmpty() } ?: listOf("Engineer", "Analyst")
        val location = request.location ?: "Remote"

        return baseKeywords.take(6).mapIndexed { index, keyword ->
            val title = "$keyword ${if (index % 2 == 0) "Specialist" else "Engineer"}"
            val company = "Hiring Company ${index + 1}"

            JobItem(
                id = "fallback-${index + 1}",
                title = title,
                company = company,
                location = location,
                description = "Potentially relevant opportunity generated from your profile while live search results were sparse.",
                requiredSkills = baseSkills.take(6),
                roleKeywords = baseKeywords,
                source = "fallback-search",
                url = jobSearchUrl(title, company, location)
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

    // SHA-256 digest rather than String.hashCode(): a 32-bit hash collision would serve one
    // request's cached response to an unrelated prompt, leaking another candidate's content.
    private fun getCacheKey(prompt: String, fallback: String): String =
        sha256("$prompt $fallback")

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun getCachedValue(key: String): String? {
        val now = currentTimeMillis()
        val cached = promptCache[key] ?: return null
        if (cached.expiresAtMillis < now) {
            promptCache.remove(key)
            return null
        }
        return cached.value
    }

    private fun putCachedValue(key: String, value: String, ttlMillis: Long) {
        // Eviction is handled automatically by removeEldestEntry on the access-ordered map.
        promptCache[key] = CachedResponse(value = value, expiresAtMillis = currentTimeMillis() + ttlMillis)
    }

    private fun executePrompt(prompt: String, fallback: String): String {
        val cacheKey = getCacheKey(prompt, fallback)
        getCachedValue(cacheKey)?.let { return it }

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
                // Generous budget: gemini-flash-latest is a thinking model, and thinking tokens
                // count against this limit. A full 8-12 job list needs room to avoid truncation.
                "maxOutputTokens" to 8192
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
                        .defaultIfEmpty("")
                        .map { body -> GeminiApiException(clientResponse.statusCode().value(), "Gemini API ${clientResponse.statusCode().value()}: $body") }
                }
                .bodyToMono(Map::class.java)
                // Transient errors (rate limit / overloaded model) are worth retrying with backoff.
                .retryWhen(
                    Retry.backoff(maxRetries, Duration.ofMillis(retryBackoffMillis))
                        .filter { it is GeminiApiException && it.statusCode in RETRYABLE_STATUSES }
                        .onRetryExhaustedThrow { _, signal -> signal.failure() }
                )
                .block()

            val candidates = response?.get("candidates") as? List<*>
            val first = candidates?.firstOrNull() as? Map<*, *>
            val content = first?.get("content") as? Map<*, *>
            val parts = content?.get("parts") as? List<*>
            val textObj = parts?.firstOrNull() as? Map<*, *>

            val value = textObj?.get("text")?.toString() ?: fallback
            // Only successful responses are cached; transient errors are not, so the next
            // request retries instead of being served a stale failure.
            putCachedValue(cacheKey, value, successCacheTtlMillis)
            value
        } catch (error: Exception) {
            logger.warn("Gemini call failed: ${error.message}")
            "$fallback | Gemini unavailable (${error.message?.take(180) ?: "unknown error"})"
        }
    }

    private class GeminiApiException(val statusCode: Int, message: String) : RuntimeException(message)

    private companion object {
        private val RETRYABLE_STATUSES = setOf(429, 502, 503, 504)
    }
}
