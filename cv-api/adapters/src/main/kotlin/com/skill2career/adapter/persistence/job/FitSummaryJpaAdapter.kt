package com.skill2career.adapter.persistence.job

import com.skill2career.adapter.messaging.FitMessage
import com.skill2career.adapter.messaging.OutboxEntity
import com.skill2career.adapter.messaging.OutboxRepo
import com.skill2career.application.job.FitSummaryStore
import com.skill2career.domain.job.FitStatus
import com.skill2career.domain.job.FitSummary
import com.skill2career.domain.profile.OwnerId
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
import java.util.UUID

@Entity
@Table(name = "fit_summaries")
class FitSummaryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var ownerId: String,
    var jobId: Long,
    @Enumerated(EnumType.STRING)
    var status: FitStatus,
    var text: String?,
    var failure: String?,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }

    fun toDomain() = FitSummary(id, OwnerId(ownerId), jobId, status, text, failure)

    companion object {
        fun of(summary: FitSummary) =
            FitSummaryEntity(
                id = summary.id,
                ownerId = summary.ownerId.value,
                jobId = summary.jobId,
                status = summary.status,
                text = summary.text,
                failure = summary.failure,
            )
    }
}

interface FitSummaryJpaRepo : JpaRepository<FitSummaryEntity, Long> {
    fun findByOwnerIdAndJobId(
        ownerId: String,
        jobId: Long,
    ): FitSummaryEntity?
}

/** The [FitSummaryStore] port, on postgres. A summary and its queued work are saved together, or neither. */
@Repository
@Transactional
class FitSummaryJpaAdapter(
    private val repo: FitSummaryJpaRepo,
    private val outbox: OutboxRepo,
) : FitSummaryStore {
    override fun save(
        summary: FitSummary,
        queueWork: Boolean,
    ): FitSummary {
        val entity = FitSummaryEntity.of(summary)
        summary.id?.let { id -> repo.findById(id).ifPresent { entity.createdAt = it.createdAt } }
        val saved = repo.save(entity).toDomain()
        if (queueWork) outbox.save(OutboxEntity.fit(FitMessage(UUID.randomUUID(), saved.id!!)))
        return saved
    }

    @Transactional(readOnly = true)
    override fun find(id: Long): FitSummary? = repo.findById(id).map { it.toDomain() }.orElse(null)

    @Transactional(readOnly = true)
    override fun find(
        ownerId: OwnerId,
        jobId: Long,
    ): FitSummary? = repo.findByOwnerIdAndJobId(ownerId.value, jobId)?.toDomain()
}
