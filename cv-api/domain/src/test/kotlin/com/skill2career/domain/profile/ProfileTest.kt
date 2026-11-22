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
    fun `keeps the candidate's answers as facts, a newer answer to the same question replacing the older`() {
        val first = profile().withFacts(listOf(Fact("Which team?", "Payments"), Fact("By how much?", "30%")))

        val updated = first.withFacts(listOf(Fact(" by how MUCH? ", "40%"), Fact("What stack?", "Kotlin")))

        assertThat(updated.facts).containsExactly(
            Fact("Which team?", "Payments"),
            Fact(" by how MUCH? ", "40%"),
            Fact("What stack?", "Kotlin"),
        )
    }

    @Test
    fun `a fact needs a question and an answer`() {
        assertThatThrownBy { Fact(" ", "Yes") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Fact("Why?", " ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `an owner needs an id`() {
        assertThatThrownBy { OwnerId(" ") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
