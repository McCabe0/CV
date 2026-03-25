package com.skill2career.controller

import com.skill2career.model.CvResponse
import com.skill2career.model.GenerateCvRequest
import com.skill2career.model.Profile
import com.skill2career.model.ProfileCreateResponse
import com.skill2career.service.GeminiService
import com.skill2career.service.PersistenceService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cv")
class CvController(
    private val geminiService: GeminiService,
    private val persistenceService: PersistenceService
) {

    @PostMapping("/profiles")
    fun createProfile(@Valid @RequestBody profile: Profile): ProfileCreateResponse {
        val savedProfile = persistenceService.saveSubmittedProfile(profile)
        return ProfileCreateResponse(profileId = savedProfile.id!!)
    }

    @PostMapping("/generate")
    fun generateCv(@Valid @RequestBody request: GenerateCvRequest): CvResponse {
        val profile = persistenceService.getProfile(request.profileId)?.let { persistenceService.toProfile(it) }
            ?: throw IllegalArgumentException("Profile not found: ${request.profileId}")

        val sections = geminiService.generateSummary(profile)

        val response = CvResponse(
            profileId = request.profileId,
            cvId = -1,
            headline = sections.headline,
            summary = sections.summary,
            keySkills = sections.keySkills,
            experienceBullets = sections.experienceBullets,
            educationSection = sections.educationSection,
            atsKeywords = sections.atsKeywords
        )

        val generated = persistenceService.saveGeneratedCvResponse(request.profileId, response)

        return response.copy(cvId = generated.id!!)
    }
}
