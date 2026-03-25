package com.skill2career.controller

import com.skill2career.entity.GeneratedCvEntity
import com.skill2career.entity.UserProfileEntity
import com.skill2career.model.GenerateCvRequest
import com.skill2career.model.Profile
import com.skill2career.service.GeminiService
import com.skill2career.service.PersistenceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CvControllerTest {

    private lateinit var geminiService: GeminiService
    private lateinit var persistenceService: PersistenceService
    private lateinit var controller: CvController

    @BeforeEach
    fun setUp() {
        geminiService = mock()
        persistenceService = mock()
        controller = CvController(geminiService, persistenceService)
    }

    @Test
    fun `createProfile returns saved profile id`() {
        val profile = Profile(
            name = "Alex",
            skills = listOf("Kotlin", "Spring Boot"),
            experience = "3 years",
            education = "BS"
        )

        whenever(persistenceService.saveSubmittedProfile(profile)).thenReturn(
            UserProfileEntity(id = 42L)
        )

        val response = controller.createProfile(profile)

        assertEquals(42L, response.profileId)
    }

    @Test
    fun `generateCv saves generated content and returns profile and cv ids`() {
        val profileEntity = UserProfileEntity(
            id = 42L,
            name = "Alex",
            skills = "Kotlin||Spring Boot",
            experience = "3 years",
            education = "BS"
        )
        val profile = Profile(
            name = "Alex",
            skills = listOf("Kotlin", "Spring Boot"),
            experience = "3 years",
            education = "BS"
        )

        whenever(persistenceService.getProfile(42L)).thenReturn(profileEntity)
        whenever(persistenceService.toProfile(profileEntity)).thenReturn(profile)
        whenever(geminiService.generateSummary(profile)).thenReturn("Generated summary")
        whenever(persistenceService.saveGeneratedCvResponse(any(), any())).thenReturn(
            GeneratedCvEntity(id = 99L)
        )

        val response = controller.generateCv(GenerateCvRequest(profileId = 42L))

        assertEquals(42L, response.profileId)
        assertEquals(99L, response.cvId)
        assertEquals("Generated summary", response.summary)
        assertEquals(listOf("Kotlin", "Spring Boot"), response.skills)
        assertEquals("3 years", response.experience)
    }

    @Test
    fun `generateCv throws when profile is missing`() {
        whenever(persistenceService.getProfile(404L)).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.generateCv(GenerateCvRequest(profileId = 404L))
        }

        assertEquals("Profile not found: 404", ex.message)
    }
}
