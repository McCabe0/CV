package com.skill2career.app.integration

import com.skill2career.application.job.JobStore
import com.skill2career.application.job.TrackedCompanyStore
import com.skill2career.domain.job.Job
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.Workplace
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.Test

/**
 * Matching, as a candidate sees it: their real token, their saved profile, and jobs in postgres found by
 * its full text index. Skills no other test uses keep these jobs apart from any others in the database.
 */
class JobMatchIntegrationTest : KeycloakIntegrationBase() {
    @Autowired
    lateinit var jobs: JobStore

    @Autowired
    lateinit var companies: TrackedCompanyStore

    private val profile =
        mapOf(
            "name" to "Casey Candidate",
            "skills" to listOf("Zigbee", "Erlang"),
            "experience" to "Five years of embedded work.",
            "education" to "BSc Computer Science",
            "workHistory" to emptyList<Any>(),
        )

    private fun holds(vararg postings: JobPosting) {
        val monzo = companies.all().single { it.name == "Monzo" }
        jobs.saveAll(postings.map { Job(companyId = monzo.id!!, source = monzo.source, posting = it, firstSeenAt = Instant.now()) })
    }

    private fun posting(
        id: String,
        description: String,
        location: String? = "London",
        workplace: Workplace? = null,
    ) = JobPosting("match-$id", "Engineer $id", location, description, "https://monzo.com/careers/match-$id", workplace)

    @Test
    fun `a candidate sees the jobs that ask for their skills, best first, and can narrow them to remote`() {
        put("/profile", profile, username = CANDIDATE).expectStatus().isOk
        holds(
            posting("both", "Zigbee radios, in Erlang.", location = "Remote (UK)", workplace = Workplace.REMOTE),
            posting("one", "Zigbee radios.", workplace = Workplace.ONSITE),
            posting("none", "Figma all day."),
        )

        get("/jobs/matches", username = CANDIDATE)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
            .jsonPath("$[0].title")
            .isEqualTo("Engineer both")
            .jsonPath("$[0].company")
            .isEqualTo("Monzo")
            .jsonPath("$[0].matchedSkills.length()")
            .isEqualTo(2)
            .jsonPath("$[1].title")
            .isEqualTo("Engineer one")

        get("/jobs/matches?remoteOnly=true&location=uk", username = CANDIDATE)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1)
            .jsonPath("$[0].workplace")
            .isEqualTo("REMOTE")
    }

    @Test
    fun `a candidate with no profile has nothing to match`() {
        get("/jobs/matches", username = CANDIDATE_OTHER).expectStatus().isNotFound
    }
}
