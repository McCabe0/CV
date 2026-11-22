package com.skill2career.application.cv

import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.profile.OwnerId

/** Where CV requests are kept, with their rounds. A driven port: an adapter implements it. */
interface CvRequestStore {
    fun save(request: CvRequest): CvRequest

    fun find(id: Long): CvRequest?
}

class CvRequestNotFound(
    id: Long,
) : RuntimeException("no CV request $id")

/** The request, only for its owner. Another's is not found, so its existence is not given away. */
internal fun CvRequestStore.findOwned(
    ownerId: OwnerId,
    id: Long,
): CvRequest = find(id)?.takeIf { it.ownerId == ownerId } ?: throw CvRequestNotFound(id)
