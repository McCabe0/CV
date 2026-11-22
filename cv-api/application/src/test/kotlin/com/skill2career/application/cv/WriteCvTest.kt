package com.skill2career.application.cv

import com.skill2career.application.profile.InMemoryProfileStore
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.rejected
import com.skill2career.domain.cv.CvFixtures.review
import com.skill2career.domain.cv.CvFixtures.writing
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.cv.Review
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.Fact
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class WriteCvTest {
    private val profiles = InMemoryProfileStore().apply { save(profile()) }
    private val requests = InMemoryCvRequestStore()

    /** A writer that numbers its drafts, and keeps every brief it was given. */
    private val briefs = mutableListOf<Brief>()
    private val writer =
        Writer { brief ->
            briefs += brief
            cv(summary = "Draft ${briefs.size}")
        }

    /** A critic that gives these reviews, in turn. */
    private fun critic(vararg reviews: Review): Critic {
        val next = reviews.iterator()
        return Critic { _, _ -> next.next() }
    }

    private fun write(
        critic: Critic,
        writer: Writer = this.writer,
        id: Long = requests.save(writing().copy(id = null)).id!!,
    ) = WriteCv(requests, profiles, writer, critic).write(id)

    @Test
    fun `finishes after one round when the critic approves the first draft`() {
        val request = write(critic(review(score = 92)))

        assertThat(request.status).isEqualTo(CvStatus.COMPLETED)
        assertThat(request.rounds).hasSize(1)
        assertThat(request.cv!!.summary).isEqualTo("Draft 1")
    }

    @Test
    fun `gives the writer the critic's last review, until a draft is approved`() {
        val firstReview = rejected(score = 60)

        val request = write(critic(firstReview, review(score = 88)))

        assertThat(request.rounds).hasSize(2)
        assertThat(briefs[0].lastRound).isNull()
        assertThat(briefs[1].lastRound).isEqualTo(Round(1, cv(summary = "Draft 1"), firstReview))
        assertThat(request.cv!!.summary).isEqualTo("Draft 2")
    }

    @Test
    fun `stops after three rounds, keeping the best scoring draft`() {
        val request = write(critic(rejected(score = 60), rejected(score = 80), rejected(score = 70)))

        assertThat(request.rounds).hasSize(3)
        assertThat(request.cv!!.summary).isEqualTo("Draft 2")
    }

    @Test
    fun `saves each round as it happens, so progress can be shown and a restart resumes`() {
        val request = write(critic(rejected(), review()))

        val saved = requests.saves.filter { it.id == request.id }
        assertThat(saved.map { it.rounds.size to it.status }).containsExactly(
            0 to CvStatus.WRITING,
            1 to CvStatus.WRITING,
            2 to CvStatus.WRITING,
            2 to CvStatus.COMPLETED,
        )
    }

    @Test
    fun `resumes after the rounds already saved, so a repeated message does not start again`() {
        val oneRoundIn = requests.save(writing().copy(id = null).withRound(Round(1, cv(summary = "Earlier"), rejected())))

        val request = write(critic(review()), id = oneRoundIn.id!!)

        assertThat(briefs).hasSize(1)
        assertThat(
            briefs
                .single()
                .lastRound!!
                .draft.summary,
        ).isEqualTo("Earlier")
        assertThat(request.rounds).hasSize(2)
    }

    @Test
    fun `does nothing for a request that is not being written`() {
        val waiting = requests.save(awaitingAnswers().copy(id = null))

        val request = write(critic(), id = waiting.id!!)

        assertThat(request).isEqualTo(waiting)
        assertThat(briefs).isEmpty()
    }

    @Test
    fun `gives the writer and the critic the profile, with the candidate's answers as its facts`() {
        val withFacts = profiles.save(profiles.findByOwner(OWNER)!!.withFacts(listOf(Fact("By how much?", "By 40%"))))
        var criticBrief: Brief? = null

        write(
            Critic { brief, _ ->
                criticBrief = brief
                review()
            },
        )

        assertThat(briefs.single().profile).isEqualTo(withFacts)
        assertThat(criticBrief!!.profile.facts).containsExactly(Fact("By how much?", "By 40%"))
    }

    @Test
    fun `fails the request, keeping its rounds, when an agent gives no usable answer`() {
        val critic = critic(rejected())
        val failingSecondTime =
            Writer { brief ->
                if (brief.lastRound != null) throw AgentFailure("not json")
                writer.write(brief)
            }

        val request = write(critic, writer = failingSecondTime)

        assertThat(request.status).isEqualTo(CvStatus.FAILED)
        assertThat(request.failure).isEqualTo("The writer or critic failed: not json")
        assertThat(request.rounds).hasSize(1)
    }

    @Test
    fun `fails the request when the profile has gone`() {
        val id = requests.save(writing().copy(id = null)).id!!

        val request = WriteCv(requests, InMemoryProfileStore(), writer, critic()).write(id)

        assertThat(request.status).isEqualTo(CvStatus.FAILED)
    }

    @Test
    fun `lets other errors through, so the message is retried`() {
        val down = Writer { throw IllegalStateException("connection refused") }

        val thrown = runCatching { write(critic(), writer = down) }.exceptionOrNull()

        assertThat(thrown).hasMessage("connection refused")
    }

    @Test
    fun `refuses a request that does not exist`() {
        val thrown = runCatching { write(critic(), id = 999) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CvRequestNotFound::class.java)
    }
}
