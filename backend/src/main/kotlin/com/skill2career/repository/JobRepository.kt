package com.skill2career.repository

import com.skill2career.entity.JobEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JobRepository : JpaRepository<JobEntity, Long> {
    /**
     * Looks up a previously persisted job by its external (AI/source) id so repeat saves
     * of the same job (e.g. the search flow followed by the match flow) reuse one row
     * instead of inserting duplicates.
     */
    fun findFirstByExternalJobId(externalJobId: String): JobEntity?
}
