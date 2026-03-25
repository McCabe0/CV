package com.skill2career.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "jobs")
class JobEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var externalJobId: String = "",

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false)
    var company: String = "",

    @Column(nullable = false)
    var location: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var requiredSkills: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var roleKeywords: String = "",

    @Column(nullable = false)
    var source: String = "internal",

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
