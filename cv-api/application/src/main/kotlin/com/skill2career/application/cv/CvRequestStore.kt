package com.skill2career.application.cv

import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.profile.OwnerId

/** Where CV requests are kept, with their rounds. A driven port: an adapter implements it. */
interface CvRequestStore {
    /**
     * Saves the request. With [queueWork], it is also queued for the cv-worker, in the same save: both
     * happen, or neither, so the worker never misses a request, nor works on one that was not saved.
     */
    fun save(
        request: CvRequest,
        queueWork: Boolean = false,
    ): CvRequest

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
