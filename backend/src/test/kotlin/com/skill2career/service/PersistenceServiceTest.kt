package com.skill2career.service

import com.skill2career.entity.GeneratedCvEntity
import com.skill2career.entity.JobEntity
import com.skill2career.entity.JobMatchEntity
import com.skill2career.entity.UserProfileEntity
import com.skill2career.model.CvResponse
import com.skill2career.model.JobItem
import com.skill2career.model.JobMatchResult
import com.skill2career.model.Profile
import com.skill2career.repository.GeneratedCvRepository
import com.skill2career.repository.JobMatchRepository
import com.skill2career.repository.JobRepository
import com.skill2career.repository.UserProfileRepository
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PersistenceServiceTest {

    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var generatedCvRepository: GeneratedCvRepository
    private lateinit var jobRepository: JobRepository
    private lateinit var jobMatchRepository: JobMatchRepository
    private lateinit var persistenceService: PersistenceService

    @BeforeEach
    fun setUp() {
        userProfileRepository = mock()
        generatedCvRepository = mock()
        jobRepository = mock()
        jobMatchRepository = mock()

        persistenceService = PersistenceService(
            userProfileRepository,
            generatedCvRepository,
            jobRepository,
            jobMatchRepository
        )
    }

    @Test
    fun `saveSubmittedProfile maps and persists profile`() {
        whenever(userProfileRepository.save(any())).thenAnswer { invocation ->
            val entity = invocation.getArgument<UserProfileEntity>(0)
            entity.id = 1L
            entity
        }

        val saved = persistenceService.saveSubmittedProfile(
            Profile("Alex", listOf("Kotlin", " Spring Boot "), "3 years", "BS")
        )

        assertEquals(1L, saved.id)
        assertEquals("Kotlin||Spring Boot", saved.skills)
    }

    @Test
    fun `getProfile returns null when not found`() {
        whenever(userProfileRepository.findById(99L)).thenReturn(Optional.empty())

        assertNull(persistenceService.getProfile(99L))
    }

    @Test
    fun `saveGeneratedCvResponse persists cv for an existing profile`() {
        val profile = UserProfileEntity(id = 2L, name = "Alex", skills = "Kotlin", experience = "3y", education = "BS")
        whenever(userProfileRepository.findById(2L)).thenReturn(Optional.of(profile))
        whenever(generatedCvRepository.save(any())).thenAnswer { it.getArgument(0) }

        val result = persistenceService.saveGeneratedCvResponse(
            2L,
            CvResponse(
                profileId = 2L,
                cvId = -1L,
                summary = "Summary",
                skills = listOf("Kotlin", "SQL"),
                experience = "3y"
            )
        )

        assertEquals(profile, result.profile)
        assertEquals("Kotlin||SQL", result.skills)
    }

    @Test
    fun `saveGeneratedCvResponse throws when profile does not exist`() {
        whenever(userProfileRepository.findById(3L)).thenReturn(Optional.empty())

        assertThrows(IllegalArgumentException::class.java) {
            persistenceService.saveGeneratedCvResponse(
                3L,
                CvResponse(3L, -1, "Summary", listOf("Kotlin"), "3y")
            )
        }
    }

    @Test
    fun `saveSearchedJobs stores mapped jobs`() {
        whenever(jobRepository.saveAll(any<List<JobEntity>>())).thenAnswer { it.getArgument(0) }

        val saved = persistenceService.saveSearchedJobs(
            listOf(
                JobItem(
                    id = "ai-1",
                    title = "Backend",
                    company = "Acme",
                    location = "Remote",
                    description = "Build APIs",
                    requiredSkills = listOf("Kotlin", "SQL"),
                    roleKeywords = listOf("backend"),
                    source = "ai"
                )
            )
        )

        assertEquals(1, saved.size)
        assertEquals("ai-1", saved.first().externalJobId)
        assertEquals("Kotlin||SQL", saved.first().requiredSkills)
        assertEquals("backend", saved.first().roleKeywords)
    }

    @Test
    fun `saveMatchResults ties records to profile cv and persisted jobs`() {
        val profile = UserProfileEntity(id = 10L)
        val cv = GeneratedCvEntity(id = 20L)
        whenever(userProfileRepository.findById(10L)).thenReturn(Optional.of(profile))
        whenever(generatedCvRepository.findById(20L)).thenReturn(Optional.of(cv))
        whenever(jobRepository.saveAll(any<List<JobEntity>>())).thenAnswer { invocation ->
            invocation.getArgument<List<JobEntity>>(0).onEachIndexed { index, entity -> entity.id = (100 + index).toLong() }
        }
        whenever(jobMatchRepository.saveAll(any<List<JobMatchEntity>>())).thenAnswer { it.getArgument(0) }

        val matches = listOf(
            JobMatchResult(
                job = JobItem(
                    id = "ai-1",
                    title = "Backend",
                    company = "Acme",
                    location = "Remote",
                    description = "Build APIs",
                    requiredSkills = listOf("Kotlin"),
                    roleKeywords = listOf("backend")
                ),
                score = 90,
                skillOverlapPercent = 100,
                requiredSkillsMissing = listOf("AWS"),
                confidence = 95,
                reasoning = "Strong fit"
            )
        )

        val saved = persistenceService.saveMatchResults(10L, 20L, matches)

        assertEquals(1, saved.size)
        assertEquals(profile, saved.first().profile)
        assertEquals(cv, saved.first().generatedCv)
        assertEquals(100L, saved.first().job?.id)
        assertEquals("AWS", saved.first().requiredSkillsMissing)
    }

    @Test
    fun `getGeneratedCv returns null when missing and toProfile unpacks skills`() {
        whenever(generatedCvRepository.findById(999L)).thenReturn(Optional.empty())

        assertNull(persistenceService.getGeneratedCv(999L))

        val profile = persistenceService.toProfile(
            UserProfileEntity(
                id = 1L,
                name = "Alex",
                skills = "Kotlin||Spring Boot|| ",
                experience = "3 years",
                education = "BS"
            )
        )

        assertEquals(listOf("Kotlin", "Spring Boot"), profile.skills)
        assertEquals("Alex", profile.name)
    }

    @Test
    fun `saveMatchResults handles null profile and cv ids`() {
        whenever(jobRepository.saveAll(any<List<JobEntity>>())).thenAnswer { it.getArgument(0) }
        whenever(jobMatchRepository.saveAll(any<List<JobMatchEntity>>())).thenAnswer { it.getArgument(0) }

        val results = persistenceService.saveMatchResults(
            profileId = null,
            cvId = null,
            matches = listOf(
                JobMatchResult(
                    job = JobItem(
                        id = "j1",
                        title = "Kotlin Dev",
                        company = "Acme",
                        location = "Remote",
                        description = "Build",
                        requiredSkills = listOf("Kotlin"),
                        roleKeywords = listOf("backend")
                    ),
                    score = 80,
                    skillOverlapPercent = 70,
                    requiredSkillsMissing = listOf("SQL", "AWS"),
                    confidence = 88,
                    reasoning = "Good potential"
                )
            )
        )

        assertEquals(1, results.size)
        assertNull(results.first().profile)
        assertNull(results.first().generatedCv)
        assertEquals("SQL||AWS", results.first().requiredSkillsMissing)
    }
}
