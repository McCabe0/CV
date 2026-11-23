package com.skill2career.application.job

import com.skill2career.application.cv.AgentFailure
import com.skill2career.domain.job.FitSummary
import com.skill2career.domain.job.ListedJob
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile

// Hand written fakes of the summary ports, for any module's tests.

class InMemoryFitSummaryStore : FitSummaryStore {
    private val summaries = mutableMapOf<Long, FitSummary>()
    private var nextId = 1L

    /** The ids of the summaries queued for the cv-worker, in order. */
    val queued = mutableListOf<Long>()

    override fun save(
        summary: FitSummary,
        queueWork: Boolean,
    ): FitSummary {
        val saved = if (summary.id == null) summary.copy(id = nextId++) else summary
        summaries[saved.id!!] = saved
        if (queueWork) queued += saved.id!!
        return saved
    }

    override fun find(id: Long): FitSummary? = summaries[id]

    override fun find(
        ownerId: OwnerId,
        jobId: Long,
    ): FitSummary? = summaries.values.firstOrNull { it.ownerId == ownerId && it.jobId == jobId }
}

class FakeFitExplainer : FitExplainer {
    private var answer: () -> String = { "It fits." }

    /** What it was asked to explain, in order. */
    val asked = mutableListOf<Pair<Profile, ListedJob>>()

    fun says(text: String) {
        answer = { text }
    }

    /** Gives no usable answer, as an LLM agent does after its retry. */
    fun fails() {
        answer = { throw AgentFailure("no usable answer") }
    }

    /** Breaks as an LLM that is down does, which is worth retrying. */
    fun breaks() {
        answer = { throw IllegalStateException("the LLM is down") }
    }

    override fun explain(
        profile: Profile,
        job: ListedJob,
    ): String {
        asked += profile to job
        return answer()
    }
}
