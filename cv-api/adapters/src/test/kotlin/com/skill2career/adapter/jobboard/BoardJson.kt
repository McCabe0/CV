package com.skill2career.adapter.jobboard

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import tools.jackson.databind.json.JsonMapper

/** Helpers for playing a board in the tests. */
internal object BoardJson {
    private val json = JsonMapper.builder().build()

    fun write(value: Any?): String = json.writeValueAsString(value)

    /** Escapes html as Greenhouse does in its content field. */
    fun escaped(html: String): String =
        html
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    fun WireMockServer.serves(
        path: String,
        body: Any?,
    ) {
        stubFor(
            get(urlPathEqualTo(path)).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(write(body)),
            ),
        )
    }

    fun WireMockServer.answers(
        path: String,
        status: Int,
    ) {
        stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status)))
    }
}
