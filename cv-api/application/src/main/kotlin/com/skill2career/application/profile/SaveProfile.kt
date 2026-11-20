package com.skill2career.application.profile

import com.skill2career.domain.profile.Profile

/**
 * Saves a candidate's profile. Each candidate has one, so a save replaces theirs. The id comes from the
 * store, never the caller, so no one can overwrite another's profile.
 */
class SaveProfile(
    private val store: ProfileStore,
) {
    fun save(profile: Profile): Profile = store.save(profile.copy(id = store.findByOwner(profile.ownerId)?.id))
}
