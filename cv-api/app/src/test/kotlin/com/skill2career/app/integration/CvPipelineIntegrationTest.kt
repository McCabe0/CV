package com.skill2career.app.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.skill2career.app.integration.container.Containers
import com.skill2career.application.cv.AnswerQuestions
import com.skill2career.application.cv.CvRequestStore
import com.skill2career.application.cv.RequestCv
import com.skill2career.application.profile.ProfileStore
import com.skill2career.domain.cv.Answer
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.profile.Fact
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import kotlin.test.Test

/**
 * The whole pipeline, as it runs: the api saves a request and its outbox message, the relay sends it to
 * kafka, the cv-worker's agents ask gemini (wiremock here) to interview, write and critique. Retries are
 * quick, so the dead letter path can be seen in the time a test takes.
 */
@TestPropertySource(
    properties = [
        "app.roles=api,cv-worker",
        "cv.worker.attempts=2",
        "cv.worker.retry-delay-ms=500",
        "outbox.relay-delay-ms=100",
    ],
)
class CvPipelineIntegrationTest : KeycloakIntegrationBase() {
    companion object {
        private val gemini = WireMockServer(options().dynamicPort()).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun llm(registry: DynamicPropertyRegistry) {
            registry.add("llm.gemini.base-url") { "${gemini.baseUrl()}/v1beta" }
        }
    }

    @Autowired
    lateinit var profiles: ProfileStore

    @Autowired
    lateinit var requestCv: RequestCv

    @Autowired
    lateinit var answerQuestions: AnswerQuestions

    @Autowired
    lateinit var requests: CvRequestStore

    private val json = JsonMapper.builder().build()

    @BeforeEach
    fun reset() = gemini.resetAll()

    /** Gemini answers this json when the system instruction starts with these words, ie to this agent. */
    private fun agentAnswers(
        systemStart: String,
        answer: String,
    ) {
        val body =
            mapOf(
                "candidates" to
                    listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to answer))), "finishReason" to "STOP")),
            )
        gemini.stubFor(
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
                .withRequestBody(containing(systemStart))
                .willReturn(okJson(json.writeValueAsString(body))),
        )
    }

    private fun candidate(): OwnerId = OwnerId("pipeline-${UUID.randomUUID()}").also { profiles.save(profile(ownerId = it)) }

    private fun statusOf(id: Long) = requests.find(id)!!.status

    private fun waitFor(
        id: Long,
        status: CvStatus,
    ) = await().atMost(Duration.ofSeconds(60)).until { statusOf(id) == status }

    /** One question, then a draft the critic approves at once. */
    private fun agentsWriteACv() {
        agentAnswers("You help a candidate", """{"questions": [{"question": "By how much did you cut settlement time?"}]}""")
        agentAnswers(
            "You write CVs",
            """
            {"summary": "Backend engineer who cut settlement time by 40%.", "skills": ["Kotlin"],
             "roles": [{"title": "Software Engineer", "company": "Acme Payments", "dates": "", "bullets": ["Cut settlement time by 40%"]}],
             "education": ["BSc Computer Science"], "projects": [], "certifications": [], "languages": []}
            """.trimIndent(),
        )
        agentAnswers("You check a draft CV", """{"score": 92, "approved": true, "issues": []}""")
    }

    @Test
    fun `over http, a candidate asks for a CV, answers, and follows it on its stream to the finished CV`() {
        agentsWriteACv()
        put(
            "/profile",
            mapOf("name" to "Casey", "skills" to listOf("Kotlin"), "experience" to "Payments.", "education" to "BSc"),
            CANDIDATE,
        ).expectStatus()
            .isOk

        val asked =
            post("/cv-requests", CANDIDATE)
                .expectStatus()
                .isAccepted
                .expectHeader()
                .exists("Location")
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!
        val id = json.readTree(asked).path("id").asLong()
        waitFor(id, CvStatus.AWAITING_ANSWERS)
        get("/cv-requests/$id", CANDIDATE_OTHER).expectStatus().isNotFound

        // Opened before answering, so the changes reach it through the outbox, kafka and this api's listener.
        val statuses =
            events("/cv-requests/$id/events", CANDIDATE)
                .map { json.readTree(it.data()).path("status").asString() }
                .collectList()
                .toFuture()
        put("/cv-requests/$id/answers", mapOf("answers" to listOf(mapOf("questionId" to 1, "text" to "By 40%"))), CANDIDATE)
            .expectStatus()
            .isAccepted

        val seen = statuses.get(2, java.util.concurrent.TimeUnit.MINUTES)!!
        assertThat(seen.first()).isEqualTo("AWAITING_ANSWERS")
        assertThat(seen).contains("WRITING")
        assertThat(seen.last()).isEqualTo("COMPLETED")
    }

    @Test
    fun `a CV is interviewed, answered, then written and approved, through kafka`() {
        agentsWriteACv()
        val owner = candidate()

        val id = requestCv.request(owner).id!!
        waitFor(id, CvStatus.AWAITING_ANSWERS)
        answerQuestions.answer(owner, id, listOf(Answer(1, "By 40%")))
        waitFor(id, CvStatus.COMPLETED)

        val finished = requests.find(id)!!
        assertThat(finished.cv!!.summary).isEqualTo("Backend engineer who cut settlement time by 40%.")
        assertThat(finished.rounds).hasSize(1)
        assertThat(profiles.findByOwner(owner)!!.facts).containsExactly(Fact("By how much did you cut settlement time?", "By 40%"))
    }

    @Test
    fun `work the LLM cannot do is retried, then parked on the dead letter topic, and the candidate told to try again`() {
        gemini.stubFor(post(urlPathMatching("/v1beta/.*")).willReturn(aResponse().withStatus(503)))

        val id = requestCv.request(candidate()).id!!

        val parked = deadLetters().poll { it.key() == id.toString() }
        assertThat(parked).isNotNull()
        waitFor(id, CvStatus.FAILED)
        assertThat(requests.find(id)!!.failure)
            .isEqualTo("Our writing service is busy right now. Please try again in a few minutes.")
    }

    /** A consumer of cv.requested.dlq, read from the start. */
    private fun deadLetters() =
        KafkaConsumer<String, String>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to Containers.kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ),
        ).apply { subscribe(listOf("cv.requested.dlq")) }

    private fun KafkaConsumer<String, String>.poll(
        matches: (org.apache.kafka.clients.consumer.ConsumerRecord<String, String>) -> Boolean,
    ) = use {
        var found: org.apache.kafka.clients.consumer.ConsumerRecord<String, String>? = null
        await().atMost(Duration.ofSeconds(60)).until {
            found = poll(Duration.ofMillis(500)).firstOrNull(matches)
            found != null
        }
        found
    }
}
