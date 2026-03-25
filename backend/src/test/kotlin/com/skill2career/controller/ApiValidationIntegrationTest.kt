package com.skill2career.controller

import com.skill2career.service.GeminiService
import com.skill2career.service.PersistenceService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ApiValidationIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var geminiService: GeminiService

    @MockBean
    private lateinit var persistenceService: PersistenceService

    @Test
    fun `returns clear validation error for invalid request body`() {
        val requestBody = """
            {
              "generatedCvOrProfile": "",
              "jobs": []
            }
        """.trimIndent()

        mockMvc.perform(
            post("/jobs/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Validation failed"))
            .andExpect(jsonPath("$.message").value("One or more fields are invalid"))
            .andExpect(jsonPath("$.details").isArray)
    }

    @Test
    fun `returns clear validation error for path type mismatch`() {
        mockMvc.perform(get("/jobs/recommendations/not-a-number"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Validation failed"))
            .andExpect(jsonPath("$.message").value("Invalid value for 'profileId'"))
            .andExpect(jsonPath("$.details[0].field").value("profileId"))
    }
}
