package com.skill2career.controller

import com.skill2career.model.JobMatchRequest
import com.skill2career.model.JobMatchResponse
import com.skill2career.model.JobSearchRequest
import com.skill2career.model.JobSearchResponse
import com.skill2career.service.JobService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/jobs")
class JobController(
    private val jobService: JobService
) {

    @PostMapping("/search")
    fun searchJobs(@RequestBody request: JobSearchRequest): JobSearchResponse =
        jobService.searchJobs(request)

    @PostMapping("/match")
    fun matchJobs(@RequestBody request: JobMatchRequest): JobMatchResponse =
        jobService.matchJobs(request)

    @GetMapping("/recommendations/{profileId}")
    fun recommendations(@PathVariable profileId: Long): JobMatchResponse =
        jobService.recommendations(profileId)
}
