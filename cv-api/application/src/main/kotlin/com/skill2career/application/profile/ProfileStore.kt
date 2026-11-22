package com.skill2career.application.profile

import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile

/** Where profiles are kept. A driven port: an adapter implements it. */
interface ProfileStore {
    fun findByOwner(ownerId: OwnerId): Profile?

    fun save(profile: Profile): Profile
}
