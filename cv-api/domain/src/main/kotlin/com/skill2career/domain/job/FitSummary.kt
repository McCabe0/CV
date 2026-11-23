package com.skill2career.domain.job

import com.skill2career.domain.profile.OwnerId

enum class FitStatus { WRITING, READY, FAILED }

/**
 * Why one job fits one candidate, in a few sentences an LLM writes. Written once, when they first ask,
 * then kept, as each costs a request from the LLM's daily quota. See decisions/jobs.md.
 */
data class FitSummary(
    val id: Long? = null,
    val ownerId: OwnerId,
    val jobId: Long,
    val status: FitStatus = FitStatus.WRITING,
    val text: String? = null,
    /** Why it failed, in words the candidate can act on. */
    val failure: String? = null,
) {
    fun written(text: String): FitSummary {
        require(text.isNotBlank()) { "a summary needs its text" }
        return copy(status = FitStatus.READY, text = text.trim(), failure = null)
    }

    fun failed(reason: String): FitSummary = copy(status = FitStatus.FAILED, failure = reason)

    /** A failed summary, written afresh. One that is ready, or being written, is never written twice. */
    fun askedAgain(): FitSummary {
        check(status == FitStatus.FAILED) { "only a failed summary is asked for again" }
        return copy(status = FitStatus.WRITING, failure = null)
    }
}
