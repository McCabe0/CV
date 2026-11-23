package com.skill2career.adapter.jobboard

import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.Workplace
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

data class JobBoardSettings(
    val baseUrl: String,
    val timeout: Duration = Duration.ofSeconds(30),
)

/** The board could not be read now: retry the crawl later. */
class JobBoardUnavailable(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** The board does not exist, so retrying will not help: the company's board token is wrong. */
class UnknownJobBoard(
    message: String,
) : RuntimeException(message)

/** Reads one board's json, turning every way it can fail into one of the two failures above. */
internal class BoardHttp(
    settings: JobBoardSettings,
) {
    private val json = JsonMapper.builder().build()

    private val http =
        RestClient
            .builder()
            .baseUrl(settings.baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build())
                    .apply { setReadTimeout(settings.timeout) },
            ).defaultHeader("User-Agent", "skill2career-job-crawler")
            .defaultHeader("Accept", "application/json")
            .build()

    /** The json at [uri], its {token} filled in. */
    fun get(
        uri: String,
        token: String,
    ): JsonNode {
        val body =
            try {
                http
                    .get()
                    .uri(uri, token)
                    .retrieve()
                    .body(String::class.java)
            } catch (_: HttpClientErrorException.NotFound) {
                throw UnknownJobBoard("there is no board '$token' at $uri")
            } catch (error: RestClientException) {
                throw JobBoardUnavailable("board '$token' could not be read: ${error.message}", error)
            }
        return try {
            json.readTree(body ?: "")
        } catch (error: JacksonException) {
            throw JobBoardUnavailable("board '$token' did not answer with json", error)
        }
    }
}

/**
 * The array a board lists its jobs in. A board that answers without one has changed or broken, so it is
 * unavailable, rather than empty: an empty answer would mark every one of its jobs inactive.
 */
internal fun JsonNode.jobs(token: String): List<JsonNode> {
    if (!isArray) throw JobBoardUnavailable("board '$token' answered without its list of jobs")
    return values().toList()
}

/** The field's text, or null when it is missing, null or blank. Numbers are given as text, for ids. */
internal fun JsonNode.text(field: String): String? =
    path(field).takeIf { it.isString || it.isNumber }?.asString()?.takeIf { it.isNotBlank() }

/** A posting, or null when the board left out what one needs, so one bad posting loses no other. */
internal fun postingOrNull(
    externalId: String?,
    title: String?,
    location: String?,
    description: String,
    applyUrl: String?,
    workplace: Workplace? = null,
): JobPosting? {
    if (externalId == null || title == null || applyUrl == null) return null
    return JobPosting(externalId, title.trim(), location?.trim(), description, applyUrl, workplace)
}

/**
 * A board's own word for where a job is done, as Lever and Ashby send it, whatever its case: remote,
 * hybrid, or on site, written onsite or OnSite. Any other word, like unspecified, says nothing.
 */
internal fun workplaceOf(word: String?): Workplace? =
    when (word?.lowercase()?.replace("-", "")) {
        "remote" -> Workplace.REMOTE
        "hybrid" -> Workplace.HYBRID
        "onsite" -> Workplace.ONSITE
        else -> null
    }
