package com.skill2career.adapter.agents

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

/** Against the real guide and examples in src/main/resources/cv-guide, so a broken file fails here. */
class FileCvGuideTest {
    private val guide = FileCvGuide()

    @Test
    fun `reads the guide, crediting the book it was distilled from`() {
        assertThat(guide.forRole("Backend Engineer").guide)
            .startsWith("# What makes a strong tech CV")
            .contains("The Tech Resume Inside Out")
            .contains("Never invent")
    }

    @Test
    fun `gives the example closest to the target role`() {
        assertThat(guide.forRole("Backend Engineer").example).contains("Northwind Payments")
        assertThat(guide.forRole("Senior React Developer").example).contains("Brightpath Learning")
        assertThat(guide.forRole("Machine Learning Engineer").example).contains("Greenleaf Grocers")
        assertThat(guide.forRole("Site Reliability Engineer").example).contains("Skyway Travel")
        assertThat(guide.forRole("Graduate Software Engineer").example).contains("University of Galway")
    }

    @Test
    fun `gives the example without its tags or its note, which are for us, not the writer`() {
        val example = guide.forRole("Backend Engineer").example!!

        assertThat(example).startsWith("**Summary**").doesNotContain("roles:", "---", "<!--")
    }

    @Test
    fun `gives no example when nothing fits the role, or there is no role, rather than a misleading one`() {
        assertThat(guide.forRole("Pastry Chef").example).isNull()
        assertThat(guide.forRole(null).example).isNull()
        assertThat(guide.forRole("  ").example).isNull()
    }

    @Test
    fun `matches whole words, so a role that only contains a tag inside a word is not matched`() {
        // "data" inside "metadata", "ui" inside "build", "ml" inside "html".
        assertThat(guide.forRole("Build Metadata Clerk").example).isNull()
    }
}
