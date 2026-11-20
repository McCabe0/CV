package com.skill2career.application.profile

import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile

/** A hand written fake of the port, for any module's tests. */
class InMemoryProfileStore : ProfileStore {
    private val profiles = mutableMapOf<Long, Profile>()
    private var nextId = 1L

    override fun findByOwner(ownerId: OwnerId): Profile? = profiles.values.firstOrNull { it.ownerId == ownerId }

    override fun save(profile: Profile): Profile {
        val saved = if (profile.id == null) profile.copy(id = nextId++) else profile
        profiles[saved.id!!] = saved
        return saved
    }

    fun count(): Int = profiles.size
}
