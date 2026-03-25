package com.skill2career.service

import com.skill2career.entity.GeneratedCvEntity
import com.skill2career.entity.JobEntity
import com.skill2career.entity.JobMatchEntity
import com.skill2career.entity.UserProfileEntity
import com.skill2career.model.CvResponse
import com.skill2career.model.JobItem
import com.skill2career.model.JobMatchResult
import com.skill2career.model.Profile
import com.skill2career.repository.GeneratedCvRepository
import com.skill2career.repository.JobMatchRepository
import com.skill2career.repository.JobRepository
import com.skill2career.repository.UserProfileRepository
import org.springframework.stereotype.Service

@Service
class PersistenceService(
    private val userProfileRepository: UserProfileRepository,
    private val generatedCvRepository: GeneratedCvRepository,
    private val jobRepository: JobRepository,
    private val jobMatchRepository: JobMatchRepository
) {

    fun saveSubmittedProfile(profile: Profile): UserProfileEntity =
        userProfileRepository.save(
            UserProfileEntity(
                name = profile.name,
                skills = profile.skills.pack(),
                experience = profile.experience,
                education = profile.education
            )
        )

    fun getProfile(profileId: Long): UserProfileEntity? = userProfileRepository.findById(profileId).orElse(null)

    fun saveGeneratedCvResponse(profileId: Long, cvResponse: CvResponse): GeneratedCvEntity {
        val profile = userProfileRepository.findById(profileId)
            .orElseThrow { IllegalArgumentException("Profile not found: $profileId") }

        return generatedCvRepository.save(
            GeneratedCvEntity(
                profile = profile,
                summary = listOf(cvResponse.headline, cvResponse.summary).filter { it.isNotBlank() }.joinToString("\n"),
                skills = cvResponse.keySkills.pack(),
                experience = cvResponse.experienceBullets.pack()
            )
        )
    }

    fun getGeneratedCv(cvId: Long): GeneratedCvEntity? = generatedCvRepository.findById(cvId).orElse(null)

    fun saveSearchedJobs(jobs: List<JobItem>): List<JobEntity> =
        jobRepository.saveAll(
            jobs.map { job ->
                JobEntity(
                    externalJobId = job.id,
                    title = job.title,
                    company = job.company,
                    location = job.location,
                    description = job.description,
                    requiredSkills = job.requiredSkills.pack(),
                    roleKeywords = job.roleKeywords.pack(),
                    source = job.source
                )
            }
        )

    fun saveMatchResults(
        profileId: Long?,
        cvId: Long?,
        matches: List<JobMatchResult>
    ): List<JobMatchEntity> {
        val profile = profileId?.let { userProfileRepository.findById(it).orElse(null) }
        val cv = cvId?.let { generatedCvRepository.findById(it).orElse(null) }
        val savedJobs = saveSearchedJobs(matches.map { it.job })

        val entities = matches.mapIndexed { idx, match ->
            JobMatchEntity(
                profile = profile,
                generatedCv = cv,
                job = savedJobs[idx],
                score = match.score,
                skillOverlapPercent = match.skillOverlapPercent,
                requiredSkillsMissing = match.requiredSkillsMissing.pack(),
                confidence = match.confidence,
                reasoning = match.reasoning
            )
        }

        return jobMatchRepository.saveAll(entities)
    }

    fun toProfile(entity: UserProfileEntity): Profile =
        Profile(
            name = entity.name,
            skills = entity.skills.unpack(),
            experience = entity.experience,
            education = entity.education
        )

    private fun List<String>.pack(): String = joinToString("||") { it.trim() }

    private fun String.unpack(): List<String> =
        split("||").map { it.trim() }.filter { it.isNotEmpty() }
}
