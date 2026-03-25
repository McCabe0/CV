package com.skill2career.controller

import com.skill2career.model.Profile
import com.skill2career.model.CvResponse
import com.skill2career.model.GenerateCvRequest
import com.skill2career.model.ProfileCreateResponse
import com.skill2career.service.GeminiService
import com.skill2career.service.PersistenceService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/cv")
class CvController(
    private val geminiService: GeminiService,
    private val persistenceService: PersistenceService
) {

    @PostMapping("/profiles")
    fun createProfile(@RequestBody profile: Profile): ProfileCreateResponse {
        val savedProfile = persistenceService.saveSubmittedProfile(profile)
        return ProfileCreateResponse(profileId = savedProfile.id!!)
    }

    @PostMapping("/generate")
    fun generateCv(@RequestBody request: GenerateCvRequest): CvResponse {
        val profile = persistenceService.getProfile(request.profileId)?.let { persistenceService.toProfile(it) }
            ?: throw IllegalArgumentException("Profile not found: ${request.profileId}")

        val summary = geminiService.generateSummary(profile)

        val generated = persistenceService.saveGeneratedCvResponse(
            request.profileId,
            CvResponse(
                profileId = request.profileId,
                cvId = -1,
                summary = summary,
                skills = profile.skills,
                experience = profile.experience
            )
        )

        return CvResponse(
            profileId = request.profileId,
            cvId = generated.id!!,
            summary = summary,
            skills = profile.skills,
            experience = profile.experience
        )
    }
}
