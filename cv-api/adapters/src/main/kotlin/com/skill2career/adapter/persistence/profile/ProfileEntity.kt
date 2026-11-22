package com.skill2career.adapter.persistence.profile

import com.skill2career.adapter.persistence.FactsConverter
import com.skill2career.adapter.persistence.StringListConverter
import com.skill2career.adapter.persistence.WorkHistoryConverter
import com.skill2career.domain.profile.Contact
import com.skill2career.domain.profile.Fact
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile
import com.skill2career.domain.profile.Skills
import com.skill2career.domain.profile.WorkExperience
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/** The table's shape. The domain never sees it: [toDomain] and [of] translate. */
@Entity
@Table(name = "user_profiles")
class ProfileEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var ownerId: String,
    var name: String,
    @Convert(converter = StringListConverter::class)
    var skills: List<String>,
    var experience: String,
    var education: String,
    var targetRole: String?,
    var yearsOfExperience: String?,
    var location: String?,
    var workAuthorization: String?,
    @Convert(converter = StringListConverter::class)
    var projects: List<String>,
    @Convert(converter = StringListConverter::class)
    var certifications: List<String>,
    @Convert(converter = StringListConverter::class)
    var languages: List<String>,
    var email: String?,
    var phone: String?,
    var linkedin: String?,
    var portfolio: String?,
    @Column(name = "work_history_json")
    @Convert(converter = WorkHistoryConverter::class)
    var workHistory: List<WorkExperience>,
    @Column(name = "facts_json")
    @Convert(converter = FactsConverter::class)
    var facts: List<Fact>,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }

    fun toDomain(): Profile =
        Profile(
            id = id,
            ownerId = OwnerId(ownerId),
            name = name,
            skills = Skills(skills),
            experience = experience,
            education = education,
            targetRole = targetRole,
            yearsOfExperience = yearsOfExperience,
            location = location,
            workAuthorization = workAuthorization,
            projects = projects,
            certifications = certifications,
            languages = languages,
            contact = Contact(email = email, phone = phone, linkedin = linkedin, portfolio = portfolio),
            workHistory = workHistory,
            facts = facts,
        )

    companion object {
        fun of(profile: Profile): ProfileEntity =
            ProfileEntity(
                id = profile.id,
                ownerId = profile.ownerId.value,
                name = profile.name,
                skills = profile.skills.toList(),
                experience = profile.experience,
                education = profile.education,
                targetRole = profile.targetRole,
                yearsOfExperience = profile.yearsOfExperience,
                location = profile.location,
                workAuthorization = profile.workAuthorization,
                projects = profile.projects,
                certifications = profile.certifications,
                languages = profile.languages,
                email = profile.contact.email,
                phone = profile.contact.phone,
                linkedin = profile.contact.linkedin,
                portfolio = profile.contact.portfolio,
                workHistory = profile.workHistory,
                facts = profile.facts,
            )
    }
}
