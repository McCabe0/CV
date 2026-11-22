package com.skill2career.domain.cv

import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.review
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class CvTest {
    @Test
    fun `needs a summary and at least one skill`() {
        assertThatThrownBy { cv(summary = " ") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { cv(skills = emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a question needs words, and an id to answer it by`() {
        assertThatThrownBy { Question(1, " ") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Question(0, "Why?") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `an answer with no words is a skip`() {
        assertThat(Answer(1, null).skipped).isTrue()
        assertThat(Answer(1, "  ").skipped).isTrue()
        assertThat(Answer(1, "By 40%").skipped).isFalse()
    }

    @Test
    fun `a score is out of 100`() {
        assertThatThrownBy { review(score = 101) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { review(score = -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a review is approved when the critic approves it`() {
        assertThat(review(approved = true).approved).isTrue()
        assertThat(review(approved = false).approved).isFalse()
    }

    @Test
    fun `a draft with an invented fact is never approved, whatever the critic says`() {
        val review = review(approved = true, issues = listOf(Issue(IssueKind.INVENTED_FACT, "No such employer")))

        assertThat(review.approved).isFalse()
    }
}
