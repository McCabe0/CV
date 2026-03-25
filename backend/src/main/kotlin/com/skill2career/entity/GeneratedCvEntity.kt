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
@Table(name = "generated_cvs")
class GeneratedCvEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    var profile: UserProfileEntity? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var summary: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var skills: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var experience: String = "",

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
