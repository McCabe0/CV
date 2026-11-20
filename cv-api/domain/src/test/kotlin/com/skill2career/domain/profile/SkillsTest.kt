package com.skill2career.domain.profile

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class SkillsTest {
    @Test
    fun `equals the same skills, as a list, so a saved profile equals the one read back`() {
        val skills = Skills(listOf("Kotlin", " SQL"))

        assertThat(skills).isEqualTo(Skills(listOf("Kotlin", "SQL")))
        assertThat(skills == listOf("Kotlin", "SQL")).isTrue()
        assertThat(skills.hashCode()).isEqualTo(listOf("Kotlin", "SQL").hashCode())
        assertThat(skills.toString()).isEqualTo("[Kotlin, SQL]")
    }

    @Test
    fun `differs from other skills and from what is not a list`() {
        val skills = Skills(listOf("Kotlin"))

        assertThat(skills == Skills(listOf("Java"))).isFalse()
        assertThat(skills.equals("Kotlin")).isFalse()
    }
}
