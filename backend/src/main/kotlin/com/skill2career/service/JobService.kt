package com.skill2career.service

import com.skill2career.model.JobItem
import com.skill2career.model.JobMatchRequest
import com.skill2career.model.JobMatchResponse
import com.skill2career.model.JobMatchResult
import com.skill2career.model.JobSearchRequest
import com.skill2career.model.JobSearchResponse
import kotlin.math.roundToInt
import org.springframework.stereotype.Service

@Service
class JobService(
    private val geminiService: GeminiService
) {

    private val jobCatalog = listOf(
        JobItem(
            id = "job-101",
            title = "Backend Kotlin Engineer",
            company = "Skill2Career",
            location = "Remote",
            description = "Build Spring Boot APIs for career intelligence products.",
            requiredSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
            roleKeywords = listOf("backend", "api", "microservices")
        ),
        JobItem(
            id = "job-102",
            title = "AI Product Engineer",
            company = "FutureWorks",
            location = "New York, NY",
            description = "Integrate LLM-based experiences into recruiter tooling.",
            requiredSkills = listOf("Python", "LLM", "Prompt Engineering", "APIs"),
            roleKeywords = listOf("ai", "product", "llm")
        ),
        JobItem(
            id = "job-103",
            title = "Frontend React Developer",
            company = "DesignLoop",
            location = "San Francisco, CA",
            description = "Develop modern interfaces for candidate workflows.",
            requiredSkills = listOf("React", "TypeScript", "CSS", "Testing"),
            roleKeywords = listOf("frontend", "ui", "react")
        ),
        JobItem(
            id = "job-104",
            title = "Data Engineer",
            company = "InsightGrid",
            location = "Austin, TX",
            description = "Design ETL pipelines and analytics datasets for jobs data.",
            requiredSkills = listOf("SQL", "Python", "ETL", "Data Modeling"),
            roleKeywords = listOf("data", "etl", "analytics")
        )
    )

    fun searchJobs(request: JobSearchRequest): JobSearchResponse {
        val normalizedSkills = request.skills.normalizedSet()
        val normalizedKeywords = request.roleKeywords.normalizedSet()
        val normalizedLocation = request.location?.normalize()

        val filtered = jobCatalog.filter { job ->
            val skillMatch = normalizedSkills.isEmpty() ||
                job.requiredSkills.normalizedSet().intersect(normalizedSkills).isNotEmpty()

            val keywordMatch = normalizedKeywords.isEmpty() ||
                job.roleKeywords.normalizedSet().intersect(normalizedKeywords).isNotEmpty() ||
                normalizedKeywords.any { keyword -> job.title.normalize().contains(keyword) }

            val locationMatch = normalizedLocation.isNullOrBlank() ||
                job.location.normalize().contains(normalizedLocation)

            skillMatch && keywordMatch && locationMatch
        }

        return JobSearchResponse(jobs = filtered)
    }

    fun matchJobs(request: JobMatchRequest): JobMatchResponse {
        val profileSkills = request.profileSkills.normalizedSet()

        val matches = request.jobs.map { job ->
            val requiredSkillsNormalized = job.requiredSkills.normalizedSet()
            val overlap = requiredSkillsNormalized.intersect(profileSkills)
            val missing = job.requiredSkills.filter { required ->
                !profileSkills.contains(required.normalize())
            }

            val overlapPercent = if (requiredSkillsNormalized.isEmpty()) {
                0
            } else {
                ((overlap.size.toDouble() / requiredSkillsNormalized.size) * 100).roundToInt()
            }

            val keywordBonus = if (
                profileSkills.any { skill ->
                    request.generatedCvOrProfile.normalize().contains(skill)
                }
            ) {
                10
            } else {
                0
            }

            val score = (overlapPercent * 0.8 + keywordBonus).roundToInt().coerceIn(0, 100)
            val confidence = (60 + overlapPercent * 0.4).roundToInt().coerceIn(0, 100)

            val reasoning = geminiService.generateMatchReasoning(
                cvOrProfile = request.generatedCvOrProfile,
                job = job,
                overlapPercent = overlapPercent,
                missingSkills = missing
            )

            JobMatchResult(
                job = job,
                score = score,
                skillOverlapPercent = overlapPercent,
                requiredSkillsMissing = missing,
                confidence = confidence,
                reasoning = reasoning
            )
        }.sortedByDescending { it.score }

        return JobMatchResponse(matches = matches)
    }

    fun recommendations(profileId: String): JobMatchResponse {
        val derivedSkills = when (profileId.lowercase().firstOrNull()) {
            'd' -> listOf("SQL", "Python", "ETL")
            'f' -> listOf("React", "TypeScript", "CSS")
            else -> listOf("Kotlin", "Spring Boot", "REST")
        }

        val syntheticProfile = "Profile($profileId) with skills: ${derivedSkills.joinToString(", ")}"

        val fullMatchResponse = matchJobs(
            JobMatchRequest(
                generatedCvOrProfile = syntheticProfile,
                profileSkills = derivedSkills,
                jobs = jobCatalog
            )
        )

        return fullMatchResponse.copy(matches = fullMatchResponse.matches.take(3))
    }

    private fun String.normalize(): String = trim().lowercase()

    private fun List<String>.normalizedSet(): Set<String> =
        map { it.normalize() }
            .filter { it.isNotBlank() }
            .toSet()
}
