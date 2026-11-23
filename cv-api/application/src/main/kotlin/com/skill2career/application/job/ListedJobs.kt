package com.skill2career.application.job

import com.skill2career.domain.job.ListedJob

/** A job, active or not, with the name of the company that lists it, as the agents are shown it. */
class ListedJobs(
    private val jobs: JobStore,
    private val companies: TrackedCompanyStore,
) {
    fun find(id: Long): ListedJob? {
        val job = jobs.find(id) ?: return null
        val company = companies.find(job.companyId) ?: return null
        return ListedJob(job, company.name)
    }
}
