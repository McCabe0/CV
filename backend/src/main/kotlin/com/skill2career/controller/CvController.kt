package com.skill2career.controller

import com.skill2career.model.Profile
import com.skill2career.model.CvResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/cv")
class CvController {

    @PostMapping("/generate")
    fun generateCv(@RequestBody profile: Profile): CvResponse {

        val summary = "Experienced ${profile.name} with skills in ${profile.skills.joinToString(", ")}."

        return CvResponse(
            summary = summary,
            skills = profile.skills,
            experience = profile.experience
        )
    }
}
