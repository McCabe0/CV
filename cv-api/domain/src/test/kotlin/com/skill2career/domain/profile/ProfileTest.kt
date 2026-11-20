package com.skill2career.domain.profile

import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class ProfileTest {
    @Test
    fun `needs a name`() {
        assertThatThrownBy { profile(name = " ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `needs at least one skill`() {
        assertThatThrownBy { profile(skills = listOf(" ")) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `needs experience and education`() {
        assertThatThrownBy { profile(experience = "") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { profile(education = "") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `keeps each skill once, trimmed, the first spelling winning`() {
        assertThat(profile(skills = listOf(" Kotlin", "kotlin ", "SQL", "", "sql")).skills)
            .containsExactly("Kotlin", "SQL")
    }

    @Test
    fun `an owner needs an id`() {
        assertThatThrownBy { OwnerId(" ") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
