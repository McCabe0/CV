package com.skill2career.controller

import com.skill2career.model.Profile
import com.skill2career.model.CvResponse
import com.skill2career.service.GeminiService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/cv")
class CvController(
    private val geminiService: GeminiService
) {

    @PostMapping("/generate")
    fun generateCv(@RequestBody profile: Profile): CvResponse {

        val summary = geminiService.generateSummary(profile)

        return CvResponse(
            summary = summary,
            skills = profile.skills,
            experience = profile.experience
        )
    }
}
