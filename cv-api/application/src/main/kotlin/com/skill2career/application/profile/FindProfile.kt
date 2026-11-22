package com.skill2career.application.profile

import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile

class FindProfile(
    private val store: ProfileStore,
) {
    fun find(ownerId: OwnerId): Profile = store.findByOwner(ownerId) ?: throw ProfileNotFound(ownerId)
}

class ProfileNotFound(
    ownerId: OwnerId,
) : RuntimeException("no profile for owner ${ownerId.value}")
