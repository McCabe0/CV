package com.skill2career.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "job_matches")
class JobMatchEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    var profile: UserProfileEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_cv_id")
    var generatedCv: GeneratedCvEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    var job: JobEntity? = null,

    @Column(nullable = false)
    var score: Int = 0,

    @Column(nullable = false)
    var skillOverlapPercent: Int = 0,

    @Column(nullable = false, columnDefinition = "TEXT")
    var requiredSkillsMissing: String = "",

    @Column(nullable = false)
    var confidence: Int = 0,

    @Column(nullable = false, columnDefinition = "TEXT")
    var reasoning: String = "",

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
