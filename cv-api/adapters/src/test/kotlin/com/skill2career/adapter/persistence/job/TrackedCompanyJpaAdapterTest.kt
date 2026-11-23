package com.skill2career.adapter.persistence.job

import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.job.SourceType
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
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
}
