package com.skill2career.adapter.llm

import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

data class GeminiSettings(
    val apiKey: String,
    val model: String = "gemini-flash-latest",
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    val timeout: Duration = Duration.ofMinutes(5),
)

/** Gemini, over its generateContent http api. */
class GeminiClient(
    private val settings: GeminiSettings,
) : LlmClient {
    private val json = JsonMapper.builder().build()

    private val http =
        RestClient
            .builder()
            .baseUrl(settings.baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build())
                    .apply { setReadTimeout(settings.timeout) },
            ).defaultHeader("x-goog-api-key", settings.apiKey)
            .build()

    override fun complete(request: LlmRequest): String {
        val body =
            mapOf(
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to request.system))),
                "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to request.prompt)))),
                "generationConfig" to
                    mapOf("responseMimeType" to "application/json", "responseJsonSchema" to request.schema),
            )

        val response =
            try {
                http
                    .post()
                    .uri("/models/{model}:generateContent", settings.model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(body))
                    .retrieve()
                    .body(String::class.java)
            } catch (error: RestClientException) {
                throw LlmUnavailable("Gemini could not be used: ${error.message}", error)
            }

        return answerIn(json.readTree(response ?: "{}"))
    }

    private fun answerIn(response: JsonNode): String {
        response.path("promptFeedback").path("blockReason").takeIf { it.isString }?.let {
            throw LlmUnusableAnswer("Gemini blocked the prompt: ${it.asString()}")
        }
        val candidate = response.path("candidates").path(0)
        when (val finish = candidate.path("finishReason").asString("")) {
            "STOP" -> {}

            "MAX_TOKENS" -> {
                throw LlmUnusableAnswer("Gemini's answer was cut short")
            }

            else -> {
                throw LlmUnusableAnswer("Gemini gave no answer: ${finish.ifEmpty { "no reason" }}")
            }
        }
        return candidate.path("content").path("parts").joinToString("") { it.path("text").asString("") }
    }
}
