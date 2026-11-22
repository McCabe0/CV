package com.skill2career.domain.cv

/** Something the interviewer asks, because a strong CV needs it and the profile does not say it. */
data class Question(
    val id: Int,
    val text: String,
) {
    init {
        require(id > 0) { "a question needs an id to answer it by" }
        require(text.isNotBlank()) { "a question needs words" }
    }
}

/** The candidate's answer, a fact they gave. No words is a skip, which is always allowed. */
data class Answer(
    val questionId: Int,
    val text: String?,
) {
    val skipped: Boolean get() = text.isNullOrBlank()
}
