package com.skill2career.application.cv

import com.skill2career.domain.cv.CvRequest

/** A hand written fake of the port, for any module's tests. It keeps every save, to check progress. */
class InMemoryCvRequestStore : CvRequestStore {
    private val requests = mutableMapOf<Long, CvRequest>()
    private var nextId = 1L
    val saves = mutableListOf<CvRequest>()

    /** The ids of the requests queued for the cv-worker, in order. */
    val queued = mutableListOf<Long>()

    override fun save(
        request: CvRequest,
        queueWork: Boolean,
    ): CvRequest {
        val saved = if (request.id == null) request.copy(id = nextId++) else request
        requests[saved.id!!] = saved
        saves += saved
        if (queueWork) queued.add(saved.id!!)
        return saved
    }

    override fun find(id: Long): CvRequest? = requests[id]
}
