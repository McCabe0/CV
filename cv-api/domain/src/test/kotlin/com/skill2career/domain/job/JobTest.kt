package com.skill2career.domain.job

import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.Instant
import kotlin.test.Test

class JobTest {
    private val now = Instant.parse("2026-11-23T06:00:00Z")

    @Test
    fun `a new posting becomes an active job, first seen now`() {
        val changed = Jobs.reconcile(COMPANY, stored = emptyList(), listed = listOf(posting("1")), at = now)

        assertThat(changed).containsExactly(
            Job(
                companyId = COMPANY.id!!,
                source = COMPANY.source,
                posting = posting("1"),
                firstSeenAt = now,
            ),
        )
        assertThat(changed.single().active).isTrue()
    }

    @Test
    fun `a job still listed, unchanged, is not saved again`() {
        val stored = job(id = 7, posting = posting("1"))

        assertThat(Jobs.reconcile(COMPANY, listOf(stored), listOf(posting("1")), now)).isEmpty()
    }

    @Test
    fun `a job still listed, but changed, takes the board's latest, keeping its id and when first seen`() {
        val stored = job(id = 7, posting = posting("1", title = "Engineer"))

        val changed = Jobs.reconcile(COMPANY, listOf(stored), listOf(posting("1", title = "Senior Engineer")), now)

        assertThat(changed).containsExactly(stored.copy(posting = posting("1", title = "Senior Engineer")))
    }

    @Test
    fun `a job no longer listed is marked inactive, with when`() {
        val stored = job(id = 7, posting = posting("1"))

        val changed = Jobs.reconcile(COMPANY, listOf(stored), listed = emptyList(), at = now)

        assertThat(changed).containsExactly(stored.copy(inactiveSince = now))
        assertThat(changed.single().active).isFalse()
    }

    @Test
    fun `a job already inactive, still not listed, is left as it was`() {
        val stored = job(id = 7, posting = posting("1"), inactiveSince = Instant.parse("2026-11-01T06:00:00Z"))

        assertThat(Jobs.reconcile(COMPANY, listOf(stored), emptyList(), now)).isEmpty()
    }

    @Test
    fun `an inactive job listed again is active again`() {
        val stored = job(id = 7, posting = posting("1"), inactiveSince = Instant.parse("2026-11-01T06:00:00Z"))

        val changed = Jobs.reconcile(COMPANY, listOf(stored), listOf(posting("1")), now)

        assertThat(changed).containsExactly(stored.copy(inactiveSince = null))
    }

    @Test
    fun `a posting listed twice is kept once, the first winning`() {
        val changed =
            Jobs.reconcile(COMPANY, emptyList(), listOf(posting("1", title = "First"), posting("1", title = "Second")), now)

        assertThat(changed.map { it.posting.title }).containsExactly("First")
    }

    @Test
    fun `a posting needs an id, a title and a link to apply`() {
        assertThatThrownBy { posting(externalId = " ") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { posting(title = " ") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { posting(applyUrl = " ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a tracked company needs a name and a board token`() {
        assertThatThrownBy { COMPANY.copy(name = " ") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { COMPANY.copy(boardToken = " ") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
