package com.skill2career.domain.job

import com.skill2career.domain.job.JobFixtures.FIRST_SEEN
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.profile.Skills
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class MatchingTest {
    @Test
    fun `a skill is mentioned whatever its case`() {
        assertThat(Matching.mentions("Build APIs in KOTLIN and Postgres", "kotlin")).isTrue()
    }

    @Test
    fun `a skill is mentioned only as a whole word, so Java is not found in JavaScript`() {
        assertThat(Matching.mentions("We write JavaScript", "Java")).isFalse()
        assertThat(Matching.mentions("We write Java, mostly", "Java")).isTrue()
    }

    @Test
    fun `a skill spelt with symbols is still found`() {
        assertThat(Matching.mentions("Services in C# and .NET", "C#")).isTrue()
        assertThat(Matching.mentions("Services in C# and .NET", ".NET")).isTrue()
        assertThat(Matching.mentions("Embedded C++ work", "C++")).isTrue()
        assertThat(Matching.mentions("Node.js on the edge", "node.js")).isTrue()
    }

    @Test
    fun `a job that asks for none of the skills is not a match`() {
        val listed = ListedJob(job(id = 1, posting = posting(title = "Designer", description = "Figma all day.")), "Monzo")

        assertThat(Matching.rank(Skills(listOf("Kotlin")), listOf(listed))).isEmpty()
    }

    @Test
    fun `a match says which of the candidate's skills the job asks for, in the candidate's spelling`() {
        val listed = ListedJob(job(id = 1, posting = posting(description = "kotlin, postgres and kafka.")), "Monzo")

        val match = Matching.rank(Skills(listOf("Kotlin", "Kafka", "React")), listOf(listed)).single()

        assertThat(match.matchedSkills).containsExactly("Kotlin", "Kafka")
        assertThat(match.company).isEqualTo("Monzo")
    }

    @Test
    fun `the job asking for more of the skills comes first`() {
        val one = listed(1, description = "Kotlin.")
        val three = listed(2, description = "Kotlin, Kafka and Postgres.")
        val two = listed(3, description = "Kotlin and Kafka.")

        val ranked = Matching.rank(Skills(listOf("Kotlin", "Kafka", "Postgres")), listOf(one, three, two))

        assertThat(ranked.map { it.job.id }).containsExactly(2L, 3L, 1L)
    }

    @Test
    fun `between jobs asking for as many skills, the one naming them in its title comes first`() {
        val inText = listed(1, title = "Backend Engineer", description = "Kotlin.")
        val inTitle = listed(2, title = "Kotlin Engineer", description = "Services.")

        val ranked = Matching.rank(Skills(listOf("Kotlin")), listOf(inText, inTitle))

        assertThat(ranked.map { it.job.id }).containsExactly(2L, 1L)
    }

    @Test
    fun `otherwise the newest job comes first`() {
        val older = listed(1, description = "Kotlin.")
        val newer = ListedJob(job(id = 2, posting = posting(description = "Kotlin."), firstSeenAt = FIRST_SEEN.plusSeconds(60)), "Monzo")

        val ranked = Matching.rank(Skills(listOf("Kotlin")), listOf(older, newer))

        assertThat(ranked.map { it.job.id }).containsExactly(2L, 1L)
    }

    @Test
    fun `an inactive job is never a match`() {
        val gone = ListedJob(job(id = 1, posting = posting(description = "Kotlin."), inactiveSince = FIRST_SEEN), "Monzo")

        assertThat(Matching.rank(Skills(listOf("Kotlin")), listOf(gone))).isEmpty()
    }

    @Test
    fun `with no filter, every job is kept`() {
        assertThat(JobFilter().accepts(posting(location = null, workplace = null))).isTrue()
    }

    @Test
    fun `remote only keeps the jobs whose board says they are remote`() {
        val remoteOnly = JobFilter(remoteOnly = true)

        assertThat(remoteOnly.accepts(posting(workplace = Workplace.REMOTE))).isTrue()
        assertThat(remoteOnly.accepts(posting(workplace = Workplace.HYBRID))).isFalse()
        assertThat(remoteOnly.accepts(posting(workplace = Workplace.ONSITE))).isFalse()
    }

    @Test
    fun `remote only leaves out a job whose board does not say where it is done`() {
        assertThat(JobFilter(remoteOnly = true).accepts(posting(workplace = null))).isFalse()
    }

    @Test
    fun `a location keeps the jobs whose location names it, whatever its case`() {
        val london = JobFilter(location = "london")

        assertThat(london.accepts(posting(location = "Cardiff, London or Remote (UK)"))).isTrue()
        assertThat(london.accepts(posting(location = "Dublin"))).isFalse()
        assertThat(london.accepts(posting(location = null))).isFalse()
    }

    @Test
    fun `a blank location is no filter`() {
        assertThat(JobFilter(location = "  ").accepts(posting(location = null))).isTrue()
    }

    @Test
    fun `remote only and a location together keep remote jobs in that place`() {
        val remoteInUk = JobFilter(remoteOnly = true, location = "UK")

        assertThat(remoteInUk.accepts(posting(location = "Remote (UK)", workplace = Workplace.REMOTE))).isTrue()
        assertThat(remoteInUk.accepts(posting(location = "Remote, Poland", workplace = Workplace.REMOTE))).isFalse()
        assertThat(remoteInUk.accepts(posting(location = "London, UK", workplace = Workplace.ONSITE))).isFalse()
    }

    private fun listed(
        id: Long,
        title: String = "Backend Engineer",
        description: String,
    ) = ListedJob(job(id = id, posting = posting(externalId = "$id", title = title, description = description)), "Monzo")
}
