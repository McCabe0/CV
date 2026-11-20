package com.skill2career.application.profile

import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class FindProfileTest {
    private val store = InMemoryProfileStore()
    private val findProfile = FindProfile(store)

    @Test
    fun `finds the owner's profile`() {
        val saved = store.save(profile())

        assertThat(findProfile.find(saved.ownerId)).isEqualTo(saved)
    }

    @Test
    fun `says when the owner has none`() {
        store.save(profile(ownerId = OwnerId("someone-else")))

        assertThatThrownBy { findProfile.find(OwnerId("nobody")) }.isInstanceOf(ProfileNotFound::class.java)
    }
}
