package com.skill2career.adapter.persistence.job

import com.skill2career.application.job.JobStore
import com.skill2career.application.job.TrackedCompanyStore
import com.skill2career.domain.job.Job
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import com.skill2career.domain.job.Workplace
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Entity
@Table(name = "tracked_companies")
class TrackedCompanyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String,
    @Enumerated(EnumType.STRING)
    var source: SourceType,
    var boardToken: String,
) {
    fun toDomain() = TrackedCompany(id, name, source, boardToken)
}

interface TrackedCompanyJpaRepo : JpaRepository<TrackedCompanyEntity, Long>

/** The [TrackedCompanyStore] port, on postgres. Read only until the admin zone, in slice 6. */
@Repository
@Transactional(readOnly = true)
class TrackedCompanyJpaAdapter(
    private val repo: TrackedCompanyJpaRepo,
) : TrackedCompanyStore {
    override fun all(): List<TrackedCompany> = repo.findAll().sortedBy { it.id }.map { it.toDomain() }

    override fun find(id: Long): TrackedCompany? = repo.findById(id).map { it.toDomain() }.orElse(null)
}

/** The table's shape. The domain never sees it: [toDomain] and [of] translate. */
@Entity
@Table(name = "jobs")
class JobEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var companyId: Long,
    @Enumerated(EnumType.STRING)
    var source: SourceType,
    var externalId: String,
    var title: String,
    var location: String?,
    var description: String,
    var applyUrl: String,
    @Enumerated(EnumType.STRING)
    var workplace: Workplace?,
    var firstSeenAt: Instant,
    var inactiveSince: Instant?,
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }

    fun toDomain() =
        Job(
            id = id,
            companyId = companyId,
            source = source,
            posting = JobPosting(externalId, title, location, description, applyUrl, workplace),
            firstSeenAt = firstSeenAt,
            inactiveSince = inactiveSince,
        )

    companion object {
        fun of(job: Job) =
            JobEntity(
                id = job.id,
                companyId = job.companyId,
                source = job.source,
                externalId = job.posting.externalId,
                title = job.posting.title,
                location = job.posting.location,
                description = job.posting.description,
                applyUrl = job.posting.applyUrl,
                workplace = job.posting.workplace,
                firstSeenAt = job.firstSeenAt,
                inactiveSince = job.inactiveSince,
            )
    }
}

interface JobJpaRepo : JpaRepository<JobEntity, Long> {
    fun findByCompanyId(companyId: Long): List<JobEntity>
}

/** The [JobStore] port, on postgres. A crawl's changes are saved in one transaction: all, or none. */
@Repository
@Transactional
class JobJpaAdapter(
    private val repo: JobJpaRepo,
) : JobStore {
    @Transactional(readOnly = true)
    override fun forCompany(companyId: Long): List<Job> = repo.findByCompanyId(companyId).map { it.toDomain() }

    override fun saveAll(jobs: List<Job>) {
        repo.saveAll(jobs.map(JobEntity::of))
    }
}
