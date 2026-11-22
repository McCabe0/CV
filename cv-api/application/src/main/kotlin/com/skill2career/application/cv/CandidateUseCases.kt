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

/** The candidate's answers to the interviewer. Any left out are skipped. */
class AnswerQuestions(
    private val requests: CvRequestStore,
) {
    fun answer(
        ownerId: OwnerId,
        id: Long,
        answers: List<Answer>,
    ): CvRequest = requests.save(requests.findOwned(ownerId, id).answered(answers))
}
