package com.skill2career.service

import com.skill2career.model.JobMatchRequest
import com.skill2career.model.JobMatchResponse
import com.skill2career.model.JobMatchResult
import com.skill2career.model.JobSearchRequest
import com.skill2career.model.JobSearchResponse
import kotlin.math.roundToInt
import org.springframework.stereotype.Service

@Service
class JobService(
    private val geminiService: GeminiService,
    private val persistenceService: PersistenceService
) {

    fun searchJobs(request: JobSearchRequest): JobSearchResponse {
        val aiJobs = geminiService.generateJobsForSearch(request)
        val savedJobs = persistenceService.saveSearchedJobs(aiJobs)
        val searchId = savedJobs.firstOrNull()?.id ?: -1L
        return JobSearchResponse(searchId = searchId, savedJobIds = savedJobs.mapNotNull { it.id }, jobs = aiJobs)
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

        val savedMatches = persistenceService.saveMatchResults(
            profileId = request.profileId,
            cvId = request.cvId,
            matches = matches
        )

        return JobMatchResponse(
            profileId = request.profileId,
            cvId = request.cvId,
            matchIds = savedMatches.mapNotNull { it.id },
            matches = matches
        )
    }

    fun recommendations(profileId: Long): JobMatchResponse {
        val profile = persistenceService.getProfile(profileId)
        val derivedSkills = profile?.skills?.split("||")?.filter { it.isNotBlank() } ?: when (profileId % 3) {
            1L -> listOf("SQL", "Python", "ETL")
            2L -> listOf("React", "TypeScript", "CSS")
            else -> listOf("Kotlin", "Spring Boot", "REST")
        }

        val syntheticProfile = "Profile($profileId) with skills: ${derivedSkills.joinToString(", ")}"

        val aiJobs = geminiService.generateJobsForSearch(
            JobSearchRequest(
                skills = derivedSkills,
                location = null,
                roleKeywords = derivedSkills
            )
        )

        val fullMatchResponse = matchJobs(
            JobMatchRequest(
                profileId = profileId,
                generatedCvOrProfile = syntheticProfile,
                profileSkills = derivedSkills,
                jobs = aiJobs
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
