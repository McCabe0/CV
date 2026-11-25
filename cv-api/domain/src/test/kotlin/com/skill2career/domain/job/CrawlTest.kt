package com.skill2career.domain.job

import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.Instant
import kotlin.test.Test

class CrawlTest {
    private val now = Instant.parse("2026-11-23T06:00:00Z")

    @Test
    fun `a company is tracked until an admin stops tracking it, and can be tracked again`() {
        assertThat(COMPANY.tracked).isTrue()

        val untracked = COMPANY.untracked(now)

        assertThat(untracked.tracked).isFalse()
        assertThat(untracked.untrackedAt).isEqualTo(now)
        assertThat(untracked.trackedAgain(name = "Monzo Bank").tracked).isTrue()
        assertThat(untracked.trackedAgain(name = "Monzo Bank").name).isEqualTo("Monzo Bank")
    }

    @Test
    fun `stopping tracking closes every live job the company has, at once`() {
        val live = job(id = 1, posting = posting("1"))
        val closed = job(id = 2, posting = posting("2"), inactiveSince = now.minusSeconds(60))

        val changed = Jobs.close(listOf(live, closed), now)

        assertThat(changed).containsExactly(live.copy(inactiveSince = now))
    }

    @Test
    fun `a crawl that worked counts the jobs its board listed, the new ones, and the ones it closed`() {
        val stored = listOf(job(id = 1, posting = posting("1")), job(id = 2, posting = posting("2")))
        val listed = listOf(posting("2"), posting("3"), posting("4"))
        val changed = Jobs.reconcile(COMPANY, stored, listed, now)

        val result = CrawlResult.worked(COMPANY.id!!, now, listed.size, changed)

        assertThat(result.outcome).isEqualTo(CrawlOutcome.WORKED)
        assertThat(result.found).isEqualTo(3)
        assertThat(result.added).isEqualTo(2)
        assertThat(result.closed).isEqualTo(1)
        assertThat(result.error).isNull()
    }

    @Test
    fun `a crawl that failed says why, and whether it will be tried again or is parked until tomorrow`() {
        val failed = CrawlResult.failed(COMPANY.id!!, now, "the board is down")
        val parked = CrawlResult.parked(COMPANY.id!!, now, "the board is down")

        assertThat(failed.outcome).isEqualTo(CrawlOutcome.FAILED)
        assertThat(parked.outcome).isEqualTo(CrawlOutcome.PARKED)
        assertThat(listOf(failed, parked)).allMatch { it.error == "the board is down" && it.found == 0 }
    }

    @Test
    fun `a failed crawl always says why`() {
        assertThatThrownBy { CrawlResult.failed(COMPANY.id!!, now, " ") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
