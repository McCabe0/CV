package com.skill2career.adapter.jobboard

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class PlainTextTest {
    @Test
    fun `keeps the words, dropping the tags`() {
        assertThat(PlainText.of("<p>Build <strong>payment</strong> APIs.</p>")).isEqualTo("Build payment APIs.")
    }

    @Test
    fun `puts each paragraph, heading and list item on its own line`() {
        assertThat(PlainText.of("<h3>About</h3><p>We build.</p><ul><li>Kotlin</li><li>SQL</li></ul>"))
            .isEqualTo("About\nWe build.\nKotlin\nSQL")
    }

    @Test
    fun `turns a line break into a new line`() {
        assertThat(PlainText.of("London<br>Remote")).isEqualTo("London\nRemote")
    }

    @Test
    fun `decodes entities`() {
        assertThat(PlainText.of("<p>R&amp;D &ndash; we&#39;re &quot;hiring&quot;&nbsp;now</p>")).isEqualTo("R&D – we're \"hiring\" now")
    }

    @Test
    fun `collapses runs of spaces and blank lines`() {
        assertThat(PlainText.of("<div>  Lots   of\n\n space </div><p></p><p>  </p><p>End</p>")).isEqualTo("Lots of space\nEnd")
    }

    @Test
    fun `leaves out scripts and styles`() {
        assertThat(PlainText.of("<style>p{}</style><p>Text</p><script>alert(1)</script>")).isEqualTo("Text")
    }

    @Test
    fun `plain text stays as it is`() {
        assertThat(PlainText.of("Already plain.")).isEqualTo("Already plain.")
    }
}
