package com.skill2career.application.job

import com.skill2career.application.profile.InMemoryProfileStore
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.domain.job.JobFilter
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.job.ListedJob
import com.skill2career.domain.job.Workplace
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class MatchJobsTest {
    private val profiles = InMemoryProfileStore().apply { save(profile(skills = listOf("Kotlin", "Kafka"))) }
    private val index = InMemoryJobIndex()
    private val match = MatchJobs(profiles, index)

    @Test
    fun `matches the candidate's skills against the jobs, best first`() {
        index.holds(listed(1, "Kotlin."), listed(2, "Kotlin and Kafka."), listed(3, "Figma."))

        val matches = match.match(OWNER, JobFilter())

        assertThat(matches.map { it.job.id }).containsExactly(2L, 1L)
        assertThat(matches.first().matchedSkills).containsExactly("Kotlin", "Kafka")
    }

    @Test
    fun `searches the index for the candidate's skills, with their filter`() {
        val filter = JobFilter(remoteOnly = true, location = "UK")

        match.match(OWNER, filter)

        assertThat(index.searches.single().terms).containsExactly("Kotlin", "Kafka")
        assertThat(index.searches.single().filter).isEqualTo(filter)
    }

    @Test
    fun `keeps to the filter`() {
        index.holds(listed(1, "Kotlin.", Workplace.REMOTE), listed(2, "Kotlin.", Workplace.ONSITE))

        val matches = match.match(OWNER, JobFilter(remoteOnly = true))

        assertThat(matches.map { it.job.id }).containsExactly(1L)
    }

    @Test
    fun `returns at most the best fifty`() {
        index.holds(*(1L..60L).map { listed(it, "Kotlin.") }.toTypedArray())

        assertThat(match.match(OWNER, JobFilter())).hasSize(MatchJobs.MATCHES)
    }

    @Test
    fun `a candidate with no profile has nothing to match`() {
        assertThatThrownBy { match.match(OwnerId("stranger"), JobFilter()) }.isInstanceOf(ProfileNotFound::class.java)
    }

    private fun listed(
        id: Long,
        description: String,
        workplace: Workplace? = null,
    ) = ListedJob(job(id = id, posting = posting(externalId = "$id", description = description, workplace = workplace)), "Monzo")
}
