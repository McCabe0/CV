package com.skill2career.app.integration

import com.skill2career.app.integration.container.Containers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.json.JacksonJsonParser
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import java.time.Duration

/**
 * The whole app, against real postgres and keycloak, signing in as the test realm's users.
 * See decisions/architecture.md.
 */
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = RANDOM_PORT)
abstract class KeycloakIntegrationBase {
    companion object {
        const val CANDIDATE = "candidate"
        const val CANDIDATE_OTHER = "candidate-other"
        const val ADMIN = "admin"
        private const val PASSWORD = "test"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", Containers.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", Containers.postgres::getUsername)
            registry.add("spring.datasource.password", Containers.postgres::getPassword)
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { Containers.issuer }
            registry.add("spring.kafka.bootstrap-servers") { Containers.kafka.bootstrapServers }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    protected fun getAnonymous(url: String): ResponseSpec = webTestClient.get().uri(url).exchange()

    protected fun getWithToken(
        url: String,
        token: String,
    ): ResponseSpec =
        webTestClient
            .get()
            .uri(url)
            .headers { it.setBearerAuth(token) }
            .exchange()

    protected fun get(
        url: String,
        username: String,
    ): ResponseSpec = getWithToken(url, token = tokenFor(username))

    protected fun put(
        url: String,
        body: Any,
        username: String,
    ): ResponseSpec =
        webTestClient
            .put()
            .uri(url)
            .headers { it.setBearerAuth(tokenFor(username)) }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()

    protected fun post(
        url: String,
        username: String,
    ): ResponseSpec =
        webTestClient
            .post()
            .uri(url)
            .headers { it.setBearerAuth(tokenFor(username)) }
            .exchange()

    protected fun postJson(
        url: String,
        body: Any,
        username: String,
    ): ResponseSpec =
        webTestClient
            .post()
            .uri(url)
            .headers { it.setBearerAuth(tokenFor(username)) }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()

    protected fun delete(
        url: String,
        username: String,
    ): ResponseSpec =
        webTestClient
            .delete()
            .uri(url)
            .headers { it.setBearerAuth(tokenFor(username)) }
            .exchange()

    /** A server sent event stream, each event's data as text, until the server ends it. */
    protected fun events(
        url: String,
        username: String,
    ): Flux<ServerSentEvent<String>> =
        webTestClient
            .mutate()
            .responseTimeout(Duration.ofMinutes(2))
            .build()
            .get()
            .uri(url)
            .headers { it.setBearerAuth(tokenFor(username)) }
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk
            .returnResult(object : ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .responseBody

    private fun tokenFor(username: String): String {
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "password")
                add("client_id", "cv-api")
                add("username", username)
                add("password", PASSWORD)
            }
        val body =
            webTestClient
                .post()
                .uri("${Containers.issuer}/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!
        return JacksonJsonParser().parseMap(body)["access_token"].toString()
    }
}
