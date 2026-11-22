package com.skill2career.application.cv

import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.application.profile.ProfileStore
import com.skill2career.domain.cv.Answer
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.profile.OwnerId

// What the candidate does, through the api.

/** Starts a CV. The candidate needs a profile first, as the CV is written only from it. */
class RequestCv(
    private val profiles: ProfileStore,
    private val requests: CvRequestStore,
) {
    fun request(ownerId: OwnerId): CvRequest {
        profiles.findByOwner(ownerId) ?: throw ProfileNotFound(ownerId)
        return requests.save(CvRequest.requested(ownerId))
    }
}

class FindCvRequest(
    private val requests: CvRequestStore,
) {
    fun find(
        ownerId: OwnerId,
        id: Long,
    ): CvRequest = requests.findOwned(ownerId, id)
}

/**
 * The candidate's answers to the interviewer. Any left out are skipped. Those given are saved to the
 * profile too, so a later CV uses them. The profile is saved first: should saving the request then fail,
 * answering again saves the same facts once more, which changes nothing.
 */
class AnswerQuestions(
    private val requests: CvRequestStore,
    private val profiles: ProfileStore,
) {
    fun answer(
        ownerId: OwnerId,
        id: Long,
        answers: List<Answer>,
    ): CvRequest {
        val answered = requests.findOwned(ownerId, id).answered(answers)
        profiles.findByOwner(ownerId)?.let { profiles.save(it.withFacts(answered.facts())) }
        return requests.save(answered)
    }
}
