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
        val profileText = request.generatedCvOrProfile.normalize()

        val initialMatches = request.jobs.map { job ->
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

            val keywordCoverage = job.roleKeywords
                .map { it.normalize() }
                .filter { it.isNotBlank() }
                .let { keywords ->
                    if (keywords.isEmpty()) 0
                    else ((keywords.count { profileText.contains(it) }.toDouble() / keywords.size) * 100).roundToInt()
                }

            val titleAlignmentBonus = if (
                job.title.normalize().split(" ").any { token ->
                    token.length > 3 && profileText.contains(token)
                }
            ) {
                8
            } else {
                0
            }

            val missingPenalty = (missing.size * 6).coerceAtMost(30)
            val baseScore = (overlapPercent * 0.7 + keywordCoverage * 0.2 + titleAlignmentBonus).roundToInt()
            val score = (baseScore - missingPenalty).coerceIn(0, 100)

            val confidence = (55 + overlapPercent * 0.35 + keywordCoverage * 0.15 - missingPenalty * 0.25)
                .roundToInt()
                .coerceIn(0, 100)

            JobMatchResult(
                job = job,
                score = score,
                skillOverlapPercent = overlapPercent,
                requiredSkillsMissing = missing,
                confidence = confidence,
                reasoning = "Reasoning not requested"
            )
        }.sortedWith(compareByDescending<JobMatchResult> { it.score }.thenByDescending { it.confidence })

        val matches = if (!request.includeReasoning) {
            initialMatches
        } else {
            val limit = request.reasoningLimit.coerceAtMost(initialMatches.size)
            initialMatches.mapIndexed { index, match ->
                if (index >= limit) {
                    match
                } else {
                    val reasoning = geminiService.generateMatchReasoning(
                        cvOrProfile = request.generatedCvOrProfile,
                        job = match.job,
                        overlapPercent = match.skillOverlapPercent,
                        missingSkills = match.requiredSkillsMissing
                    )
                    match.copy(reasoning = reasoning)
                }
            }
        }

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
            ?: throw IllegalArgumentException("Profile not found: $profileId")

        val derivedSkills = profile.skills
            .split("||")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (derivedSkills.isEmpty()) {
            throw IllegalArgumentException("Profile has no skills: $profileId")
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
                jobs = aiJobs,
                includeReasoning = false
            )
        )

        return fullMatchResponse.copy(matches = fullMatchResponse.matches.take(6))
    }

    private fun String.normalize(): String = trim().lowercase()

    private fun List<String>.normalizedSet(): Set<String> =
        map { it.normalize() }
            .filter { it.isNotBlank() }
            .toSet()
}
