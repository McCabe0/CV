package com.skill2career.adapter.persistence.profile

import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.profile.Fact
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.Test

@Import(ProfileJpaAdapter::class)
class ProfileJpaAdapterTest : PostgresTest() {
    @Autowired
    lateinit var adapter: ProfileJpaAdapter

    @Test
    fun `saves a profile and finds it by its owner, with every field`() {
        val saved = adapter.save(profile())

        val found = adapter.findByOwner(saved.ownerId)

        assertThat(saved.id).isNotNull()
        assertThat(found).isEqualTo(saved)
    }

    @Test
    fun `replaces a profile saved with its id`() {
        val saved = adapter.save(profile(name = "First"))

        adapter.save(saved.copy(name = "Second"))

        assertThat(adapter.findByOwner(saved.ownerId)!!.name).isEqualTo("Second")
    }

    @Test
    fun `keeps the candidate's answers as facts`() {
        val saved = adapter.save(profile().withFacts(listOf(Fact("By how much?", "By 40%"))))

        assertThat(adapter.findByOwner(saved.ownerId)!!.facts).containsExactly(Fact("By how much?", "By 40%"))
    }

    @Test
    fun `finds nothing for an owner with no profile`() {
        adapter.save(profile(ownerId = OwnerId("someone-else")))

        assertThat(adapter.findByOwner(OwnerId("nobody"))).isNull()
    }
}
