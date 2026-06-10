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
            val locationBonus = locationBonus(request.preferredLocation, job.location)
            val seniorityAdjustment = seniorityAdjustment(request.candidateYears, job.title)

            val baseScore = (overlapPercent * 0.7 + keywordCoverage * 0.2 + titleAlignmentBonus).roundToInt()
            val score = (baseScore - missingPenalty + locationBonus + seniorityAdjustment).coerceIn(0, 100)

            val confidence = (55 + overlapPercent * 0.35 + keywordCoverage * 0.15 - missingPenalty * 0.25 + seniorityAdjustment * 0.5)
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

        val rankedMatches = if (request.minScore > 0) {
            initialMatches.filter { it.score >= request.minScore }
        } else {
            initialMatches
        }

        val matches = if (!request.includeReasoning) {
            rankedMatches
        } else {
            val limit = request.reasoningLimit.coerceAtMost(rankedMatches.size)
            rankedMatches.mapIndexed { index, match ->
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

    /**
     * Rewards roles that are remote or align with the candidate's preferred location.
     * Returns 0 when no preference is supplied or the location does not align.
     */
    private fun locationBonus(preferredLocation: String?, jobLocation: String): Int {
        val preferred = preferredLocation?.normalize().orEmpty()
        if (preferred.isBlank()) return 0

        val location = jobLocation.normalize()
        if (location.contains("remote")) return 5

        val preferredTokens = preferred.split(",", " ")
            .map { it.trim() }
            .filter { it.length > 2 }

        return if (preferredTokens.any { location.contains(it) }) 5 else 0
    }

    /**
     * Compares the candidate's seniority (derived from years of experience) with the
     * seniority implied by the job title. Rewards matches/over-qualification, penalises
     * under-qualification, and stays neutral when the candidate's years are unknown.
     */
    private fun seniorityAdjustment(candidateYears: Int?, jobTitle: String): Int {
        val candidateLevel = candidateYears?.let { yearsToLevel(it) } ?: return 0
        val jobLevel = titleToLevel(jobTitle.normalize())
        return if (candidateLevel >= jobLevel) 5 else -8
    }

    private fun yearsToLevel(years: Int): Int = when {
        years < 3 -> 1
        years < 7 -> 2
        else -> 3
    }

    private fun titleToLevel(normalizedTitle: String): Int = when {
        SENIOR_TITLE_KEYWORDS.any { normalizedTitle.contains(it) } -> 3
        JUNIOR_TITLE_KEYWORDS.any { normalizedTitle.contains(it) } -> 1
        else -> 2
    }

    private fun String.normalize(): String = trim().lowercase()

    private fun List<String>.normalizedSet(): Set<String> =
        map { it.normalize() }
            .filter { it.isNotBlank() }
            .toSet()

    private companion object {
        private val SENIOR_TITLE_KEYWORDS = listOf("senior", "lead", "principal", "staff", "head")
        private val JUNIOR_TITLE_KEYWORDS = listOf("junior", "intern", "graduate", "entry")
    }
}
