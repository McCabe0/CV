package com.skill2career.adapter.persistence.cv

import com.skill2career.adapter.messaging.OutboxEntity
import com.skill2career.adapter.messaging.OutboxRepo
import com.skill2career.adapter.messaging.RequestMessage
import com.skill2career.adapter.persistence.AnswersConverter
import com.skill2career.adapter.persistence.CvConverter
import com.skill2career.adapter.persistence.QuestionsConverter
import com.skill2career.adapter.persistence.RoundsConverter
import com.skill2career.application.cv.CvRequestStore
import com.skill2career.domain.cv.Answer
import com.skill2career.domain.cv.Cv
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.cv.Question
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.OwnerId
import jakarta.persistence.Column
import jakarta.persistence.Convert
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

/** The table's shape. The domain never sees it: [toDomain] and [of] translate. */
@Entity
@Table(name = "cv_requests")
class CvRequestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var ownerId: String,
    @Enumerated(EnumType.STRING)
    var status: CvStatus,
    @Column(name = "questions_json")
    @Convert(converter = QuestionsConverter::class)
    var questions: List<Question>,
    @Column(name = "answers_json")
    @Convert(converter = AnswersConverter::class)
    var answers: List<Answer>,
    @Column(name = "rounds_json")
    @Convert(converter = RoundsConverter::class)
    var rounds: List<Round>,
    @Column(name = "cv_json")
    @Convert(converter = CvConverter::class)
    var cv: Cv?,
    var failure: String?,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }

    fun toDomain() =
        CvRequest(
            id = id,
            ownerId = OwnerId(ownerId),
            status = status,
            questions = questions,
            answers = answers,
            rounds = rounds,
            cv = cv,
            failure = failure,
        )

    companion object {
        fun of(request: CvRequest) =
            CvRequestEntity(
                id = request.id,
                ownerId = request.ownerId.value,
                status = request.status,
                questions = request.questions,
                answers = request.answers,
                rounds = request.rounds,
                cv = request.cv,
                failure = request.failure,
            )
    }
}

interface CvRequestJpaRepo : JpaRepository<CvRequestEntity, Long>

/**
 * The [CvRequestStore] port, on postgres. In the same transaction as each save, the outbox gets the save
 * as progress (any change to a request is progress a candidate may be watching), and the work, if queued.
 */
@Repository
@Transactional
class CvRequestJpaAdapter(
    private val repo: CvRequestJpaRepo,
    private val outbox: OutboxRepo,
) : CvRequestStore {
    override fun save(
        request: CvRequest,
        queueWork: Boolean,
    ): CvRequest {
        val entity = CvRequestEntity.of(request)
        request.id?.let { id -> repo.findById(id).ifPresent { entity.createdAt = it.createdAt } }
        val saved = repo.save(entity).toDomain()
        if (queueWork) outbox.save(OutboxEntity.work(RequestMessage(UUID.randomUUID(), saved.id!!)))
        outbox.save(OutboxEntity.progress(RequestMessage(UUID.randomUUID(), saved.id!!)))
        return saved
    }

    @Transactional(readOnly = true)
    override fun find(id: Long): CvRequest? = repo.findById(id).map { it.toDomain() }.orElse(null)
}
