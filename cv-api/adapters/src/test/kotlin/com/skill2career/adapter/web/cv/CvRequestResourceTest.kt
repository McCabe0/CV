package com.skill2career.adapter.web.cv

import com.skill2career.adapter.web.ApiExceptionHandler
import com.skill2career.adapter.web.security.SecurityConfiguration
import com.skill2career.application.cv.AnswerQuestions
import com.skill2career.application.cv.CvRequestNotFound
import com.skill2career.application.cv.CvRequestStore
import com.skill2career.application.cv.FindCvRequest
import com.skill2career.application.cv.RequestCv
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.domain.cv.Answer
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.question
import com.skill2career.domain.cv.CvFixtures.review
import com.skill2career.domain.cv.CvFixtures.writing
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.OwnerId
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@WebMvcTest(CvRequestResource::class)
@Import(SecurityConfiguration::class, ApiExceptionHandler::class, ProgressStreams::class)
class CvRequestResourceTest {
    private companion object {
        const val SUB = "candidate-sub"
        val OWNER = OwnerId(SUB)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var requestCv: RequestCv

    @MockitoBean
    lateinit var findCvRequest: FindCvRequest

    @MockitoBean
    lateinit var answerQuestions: AnswerQuestions

    @MockitoBean
    lateinit var requests: CvRequestStore

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    private fun candidate() = jwt().jwt { it.subject(SUB) }.authorities(SimpleGrantedAuthority("ROLE_CANDIDATE"))

    private fun answering(json: String) =
        put("/cv-requests/7/answers").with(candidate()).contentType(MediaType.APPLICATION_JSON).content(json)

    @Test
    fun `starts a CV for the signed in candidate, with 202 and where to follow it`() {
        whenever(requestCv.request(OWNER)) doReturn CvRequest.requested(OWNER).copy(id = 7)

        mockMvc
            .perform(post("/cv-requests").with(candidate()))
            .andExpect(status().isAccepted)
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/cv-requests/7")))
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.status").value("REQUESTED"))
    }

    @Test
    fun `asks for a profile first, as the CV is written only from it`() {
        whenever(requestCv.request(OWNER)) doThrow ProfileNotFound(OWNER)

        mockMvc
            .perform(post("/cv-requests").with(candidate()))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("Save your profile before asking for a CV."))
    }

    @Test
    fun `shows the request, with the interviewer's questions`() {
        whenever(findCvRequest.find(OWNER, 7)) doReturn awaitingAnswers(question(1, "By how much?")).copy(id = 7)

        mockMvc
            .perform(get("/cv-requests/7").with(candidate()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("AWAITING_ANSWERS"))
            .andExpect(jsonPath("$.questions[0].id").value(1))
            .andExpect(jsonPath("$.questions[0].text").value("By how much?"))
    }

    @Test
    fun `shows each round the critic reviewed, and the finished CV`() {
        val finished =
            writing()
                .copy(id = 7)
                .withRound(Round(1, cv(summary = "The draft."), review(score = 91)))
                .completed()
        whenever(findCvRequest.find(OWNER, 7)) doReturn finished

        mockMvc
            .perform(get("/cv-requests/7").with(candidate()))
            .andExpect(jsonPath("$.rounds[0].number").value(1))
            .andExpect(jsonPath("$.rounds[0].score").value(91))
            .andExpect(jsonPath("$.rounds[0].approved").value(true))
            .andExpect(jsonPath("$.rounds[0].summary").value("The draft."))
            .andExpect(jsonPath("$.cv.summary").value("The draft."))
    }

    @Test
    fun `never shows another candidate's request`() {
        whenever(findCvRequest.find(OWNER, 7)) doThrow CvRequestNotFound(7)

        mockMvc.perform(get("/cv-requests/7").with(candidate())).andExpect(status().isNotFound)
    }

    @Test
    fun `takes the candidate's answers, with 202, as the writing has begun`() {
        whenever(answerQuestions.answer(eq(OWNER), eq(7), any())) doReturn writing().copy(id = 7)

        mockMvc
            .perform(answering("""{"answers": [{"questionId": 1, "text": "By 40%"}, {"questionId": 2, "text": null}]}"""))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("WRITING"))

        verify(answerQuestions).answer(OWNER, 7, listOf(Answer(1, "By 40%"), Answer(2, null)))
    }

    @Test
    fun `refuses answers once the request has moved on, with 409`() {
        whenever(answerQuestions.answer(eq(OWNER), eq(7), any())) doThrow IllegalStateException("not waiting")

        mockMvc
            .perform(answering("""{"answers": []}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("This CV request is not waiting for answers."))
    }

    @Test
    fun `refuses an answer to a question that was not asked, with 400`() {
        whenever(answerQuestions.answer(eq(OWNER), eq(7), any())) doThrow IllegalArgumentException("not asked")

        mockMvc
            .perform(answering("""{"answers": [{"questionId": 9, "text": "Hi"}]}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("not asked"))
    }

    @Test
    fun `says plainly why answers are refused, when the rule gave no reason`() {
        whenever(answerQuestions.answer(eq(OWNER), eq(7), any())) doThrow IllegalArgumentException()

        mockMvc
            .perform(answering("""{"answers": []}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Those answers cannot be taken."))
    }

    @Test
    fun `refuses an answer over 2,000 characters, or too many answers, before anything is changed`() {
        mockMvc.perform(answering("""{"answers": [{"questionId": 1, "text": "${"a".repeat(2001)}"}]}""")).andExpect(status().isBadRequest)
        val six = (1..6).joinToString(",") { """{"questionId": $it, "text": "x"}""" }
        mockMvc.perform(answering("""{"answers": [$six]}""")).andExpect(status().isBadRequest)

        verifyNoInteractions(answerQuestions)
    }

    @Test
    fun `takes a detailed answer, up to 2,000 characters`() {
        whenever(answerQuestions.answer(eq(OWNER), eq(7), any())) doReturn writing().copy(id = 7)

        mockMvc
            .perform(answering("""{"answers": [{"questionId": 1, "text": "${"a".repeat(2000)}"}]}"""))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `streams the request's progress, starting with where it is now`() {
        val finished = writing().copy(id = 7).withRound(Round(1, cv(), review())).completed()
        whenever(findCvRequest.find(OWNER, 7)) doReturn finished

        val started =
            mockMvc
                .perform(get("/cv-requests/7/events").with(candidate()))
                .andExpect(request().asyncStarted())
                .andReturn()

        mockMvc
            .perform(asyncDispatch(started))
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:progress")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"COMPLETED\"")))
    }

    @Test
    fun `is only for signed in candidates`() {
        mockMvc.perform(post("/cv-requests")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(post("/cv-requests").with(jwt().jwt { it.subject(SUB) }))
            .andExpect(status().isForbidden)
    }
}
