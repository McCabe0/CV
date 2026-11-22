package com.skill2career.application.profile

import com.skill2career.domain.profile.Profile

/**
 * Saves a candidate's profile. Each candidate has one, so a save replaces theirs. The id comes from the
 * store, never the caller, so no one can overwrite another's profile. The interviewer's facts are kept,
 * as the profile form does not send them.
 */
class SaveProfile(
    private val store: ProfileStore,
) {
    fun save(profile: Profile): Profile {
        val stored = store.findByOwner(profile.ownerId)
        return store.save(profile.copy(id = stored?.id, facts = stored?.facts.orEmpty()))
    }
}
