package com.skill2career.adapter.persistence.job

import com.skill2career.application.job.CrawlResults
import com.skill2career.domain.job.CrawlOutcome
import com.skill2career.domain.job.CrawlResult
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** A company's latest crawl. The company's id is its key, so each crawl replaces the one before. */
@Entity
@Table(name = "crawl_results")
class CrawlResultEntity(
    @Id
    var companyId: Long,
    @Column(name = "crawled_at")
    var at: Instant,
    @Enumerated(EnumType.STRING)
    var outcome: CrawlOutcome,
    var found: Int,
    var added: Int,
    var closed: Int,
    var error: String?,
) {
    fun toDomain() = CrawlResult(companyId, at, outcome, found, added, closed, error)

    companion object {
        /** The longest error kept, so one board's stack trace cannot fill the table. */
        const val ERROR_CHARS = 1_000

        fun of(result: CrawlResult) =
            CrawlResultEntity(
                companyId = result.companyId,
                at = result.at,
                outcome = result.outcome,
                found = result.found,
                added = result.added,
                closed = result.closed,
                error = result.error?.take(ERROR_CHARS),
            )
    }
}

interface CrawlResultJpaRepo : JpaRepository<CrawlResultEntity, Long>

/** The [CrawlResults] port, on postgres: one row per company, its latest crawl. */
@Repository
@Transactional
class CrawlResultJpaAdapter(
    private val repo: CrawlResultJpaRepo,
) : CrawlResults {
    override fun record(result: CrawlResult) {
        repo.save(CrawlResultEntity.of(result))
    }

    @Transactional(readOnly = true)
    override fun latest(): Map<Long, CrawlResult> = repo.findAll().associate { it.companyId to it.toDomain() }
}
