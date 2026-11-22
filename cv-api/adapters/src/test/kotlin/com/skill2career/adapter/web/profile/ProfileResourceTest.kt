package com.skill2career.adapter.web.profile

import com.skill2career.adapter.web.ApiExceptionHandler
import com.skill2career.adapter.web.security.SecurityConfiguration
import com.skill2career.application.profile.FindProfile
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.application.profile.SaveProfile
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test

@WebMvcTest(ProfileResource::class)
@Import(SecurityConfiguration::class, ApiExceptionHandler::class)
class ProfileResourceTest {
    companion object {
        const val SUB = "candidate-sub"

        @JvmStatic
        fun invalidRequests(): List<ProfileRequest> =
            listOf(
                ProfileRequestFixtures.request(name = ""),
                ProfileRequestFixtures.request(name = "x".repeat(101)),
                ProfileRequestFixtures.request(skills = emptyList()),
                ProfileRequestFixtures.request(skills = listOf("")),
                ProfileRequestFixtures.request(skills = List(51) { "skill $it" }),
                ProfileRequestFixtures.request(experience = ""),
                ProfileRequestFixtures.request(education = ""),
                ProfileRequestFixtures.request(email = "not-an-email"),
                ProfileRequestFixtures.request(linkedin = "javascript:alert(1)"),
                ProfileRequestFixtures.request(workHistory = listOf(ProfileRequestFixtures.work(company = ""))),
            )
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var saveProfile: SaveProfile

    @MockitoBean
    lateinit var findProfile: FindProfile

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    private fun candidate() = jwt().jwt { it.subject(SUB) }.authorities(SimpleGrantedAuthority("ROLE_CANDIDATE"))

    private fun putProfile(body: Any) =
        put("/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))

    @Test
    fun `saves the signed in user's profile, as theirs`() {
        whenever(saveProfile.save(any())).thenAnswer { (it.arguments[0] as Profile).copy(id = 7) }

        mockMvc
            .perform(putProfile(ProfileRequestFixtures.request()).with(candidate()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.name").value("Casey Candidate"))
            .andExpect(jsonPath("$.workHistory[0].company").value("Acme Payments"))

        val saved = argumentCaptor<Profile>().apply { verify(saveProfile).save(capture()) }.firstValue
        assertThat(saved.ownerId).isEqualTo(OwnerId(SUB))
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    fun `refuses an invalid profile`(request: ProfileRequest) {
        mockMvc
            .perform(putProfile(request).with(candidate()))
            .andExpect(status().isBadRequest)

        verifyNoInteractions(saveProfile)
    }

    @Test
    fun `returns the signed in user's profile`() {
        whenever(findProfile.find(OwnerId(SUB))).thenReturn(profile(id = 7, ownerId = OwnerId(SUB)))

        mockMvc
            .perform(get("/profile").with(candidate()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.skills[0]").value("Kotlin"))
            .andExpect(jsonPath("$.email").value("casey@example.com"))
    }

    @Test
    fun `says not found when the user has no profile yet`() {
        whenever(findProfile.find(OwnerId(SUB))).thenThrow(ProfileNotFound(OwnerId(SUB)))

        mockMvc
            .perform(get("/profile").with(candidate()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `refuses a user who is not a candidate`() {
        mockMvc
            .perform(get("/profile").with(jwt().jwt { it.subject(SUB) }))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `refuses a token with no subject, as it names no owner`() {
        mockMvc
            .perform(
                get("/profile").with(
                    jwt()
                        .jwt {
                            it.claims { c ->
                                c.remove("sub")
                            }
                        }.authorities(SimpleGrantedAuthority("ROLE_CANDIDATE")),
                ),
            ).andExpect(status().isForbidden)

        verifyNoInteractions(findProfile)
    }

    @Test
    fun `refuses a request with no token`() {
        mockMvc.perform(get("/profile")).andExpect(status().isUnauthorized)
    }
}
