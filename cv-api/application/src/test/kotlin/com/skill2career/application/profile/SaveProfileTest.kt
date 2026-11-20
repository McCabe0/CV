package com.skill2career.application.profile

import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class SaveProfileTest {
    private val store = InMemoryProfileStore()
    private val saveProfile = SaveProfile(store)

    @Test
    fun `saves a new profile, giving it an id`() {
        val saved = saveProfile.save(profile())

        assertThat(saved.id).isNotNull()
        assertThat(store.findByOwner(saved.ownerId)).isEqualTo(saved)
    }

    @Test
    fun `replaces the owner's profile, keeping its id, so each user has one`() {
        val first = saveProfile.save(profile(name = "First"))

        val second = saveProfile.save(profile(name = "Second"))

        assertThat(second.id).isEqualTo(first.id)
        assertThat(store.findByOwner(first.ownerId)!!.name).isEqualTo("Second")
        assertThat(store.count()).isEqualTo(1)
    }

    @Test
    fun `ignores an id sent in, so no one can overwrite another's profile`() {
        val theirs = saveProfile.save(profile(ownerId = OwnerId("someone-else")))

        val mine = saveProfile.save(profile(id = theirs.id))

        assertThat(mine.id).isNotEqualTo(theirs.id)
        assertThat(store.findByOwner(OwnerId("someone-else"))).isEqualTo(theirs)
    }
}
