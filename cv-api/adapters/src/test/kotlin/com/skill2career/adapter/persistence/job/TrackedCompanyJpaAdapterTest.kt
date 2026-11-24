package com.skill2career.adapter.persistence.job

import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import kotlin.test.Test

@Import(TrackedCompanyJpaAdapter::class)
class TrackedCompanyJpaAdapterTest : PostgresTest() {
    @Autowired
    lateinit var adapter: TrackedCompanyJpaAdapter

    @Test
    fun `starts with the companies checked live, until the admin zone keeps the list`() {
        assertThat(adapter.all().map { Triple(it.name, it.source, it.boardToken) }).containsExactly(
            Triple("Monzo", SourceType.GREENHOUSE, "monzo"),
            Triple("GitLab", SourceType.GREENHOUSE, "gitlab"),
            Triple("Cloudflare", SourceType.GREENHOUSE, "cloudflare"),
            Triple("Palantir", SourceType.LEVER, "palantir"),
            Triple("Spotify", SourceType.LEVER, "spotify"),
            Triple("Ramp", SourceType.ASHBY, "ramp"),
            Triple("Linear", SourceType.ASHBY, "linear"),
            Triple("Blueground", SourceType.WORKABLE, "blueground"),
        )
    }

    @Test
    fun `finds a company by its id`() {
        val spotify = adapter.all().single { it.name == "Spotify" }

        assertThat(adapter.find(spotify.id!!)).isEqualTo(spotify)
    }

    @Test
    fun `finds nothing for an id it does not have`() {
        assertThat(adapter.find(999_999)).isNull()
    }

    @Test
    fun `saves a new company, giving it an id, and finds it by its board`() {
        val saved = adapter.save(TrackedCompany(name = "Stripe", source = SourceType.GREENHOUSE, boardToken = "stripe"))

        assertThat(saved.id).isNotNull()
        assertThat(adapter.find(saved.id!!)).isEqualTo(saved)
        assertThat(adapter.findByBoard(SourceType.GREENHOUSE, "stripe")).isEqualTo(saved)
        assertThat(adapter.findByBoard(SourceType.LEVER, "stripe")).isNull()
    }

    @Test
    fun `keeps when a company stopped being tracked, and clears it when tracked again`() {
        val spotify = adapter.all().single { it.name == "Spotify" }
        val at = Instant.parse("2026-11-23T06:00:00Z")

        adapter.save(spotify.untracked(at))
        assertThat(adapter.find(spotify.id!!)!!.untrackedAt).isEqualTo(at)

        adapter.save(spotify.untracked(at).trackedAgain("Spotify Music"))
        assertThat(adapter.find(spotify.id!!)).isEqualTo(spotify.copy(name = "Spotify Music"))
    }

    @Test
    fun `refuses a second company with the same board`() {
        assertThatThrownBy { adapter.save(TrackedCompany(name = "Monzo 2", source = SourceType.GREENHOUSE, boardToken = "monzo")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
