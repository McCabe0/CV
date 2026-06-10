package com.skill2career.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_profiles")
class UserProfileEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var skills: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var experience: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var education: String = "",

    @Column
    var targetRole: String? = null,

    @Column
    var yearsOfExperience: String? = null,

    @Column
    var location: String? = null,

    @Column
    var workAuthorization: String? = null,

    @Column(columnDefinition = "TEXT")
    var projects: String = "",

    @Column(columnDefinition = "TEXT")
    var certifications: String = "",

    @Column(columnDefinition = "TEXT")
    var languages: String = "",

    @Column
    var email: String? = null,

    @Column
    var phone: String? = null,

    @Column
    var linkedin: String? = null,

    @Column
    var portfolio: String? = null,

    @Column(columnDefinition = "TEXT")
    var workHistoryJson: String = "[]",

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
