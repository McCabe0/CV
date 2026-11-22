package com.skill2career.adapter.persistence.profile

import com.skill2career.application.profile.ProfileStore
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

interface ProfileJpaRepo : JpaRepository<ProfileEntity, Long> {
    fun findByOwnerId(ownerId: String): ProfileEntity?
}

/** The [ProfileStore] port, on postgres. */
@Repository
@Transactional
class ProfileJpaAdapter(
    private val repo: ProfileJpaRepo,
) : ProfileStore {
    @Transactional(readOnly = true)
    override fun findByOwner(ownerId: OwnerId): Profile? = repo.findByOwnerId(ownerId.value)?.toDomain()

    override fun save(profile: Profile): Profile {
        val entity = ProfileEntity.of(profile)
        profile.id?.let { id -> repo.findById(id).ifPresent { entity.createdAt = it.createdAt } }
        return repo.save(entity).toDomain()
    }
}
