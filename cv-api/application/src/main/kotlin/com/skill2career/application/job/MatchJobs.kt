package com.skill2career.application.job

import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.application.profile.ProfileStore
import com.skill2career.domain.job.JobFilter
import com.skill2career.domain.job.JobMatch
import com.skill2career.domain.job.Matching
import com.skill2career.domain.profile.OwnerId

/**
 * The jobs that fit a candidate's skills, best first. The index finds the jobs mentioning any of their
 * skills; the domain's rule ranks them. It reads our copy of the jobs only, so it never touches a board.
 */
class MatchJobs(
    private val profiles: ProfileStore,
    private val index: JobIndex,
) {
    fun match(
        owner: OwnerId,
        filter: JobFilter,
    ): List<JobMatch> {
        val profile = profiles.findByOwner(owner) ?: throw ProfileNotFound(owner)
        val candidates = index.search(profile.skills, filter, CANDIDATES)
        return Matching.rank(profile.skills, candidates).take(MATCHES)
    }

    companion object {
        /** How many of the index's best text matches are ranked. */
        const val CANDIDATES = 200

        /** How many matches a candidate is shown. */
        const val MATCHES = 50
    }
}
