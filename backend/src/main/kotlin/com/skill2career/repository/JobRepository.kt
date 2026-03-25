package com.skill2career.repository

import com.skill2career.entity.JobEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JobRepository : JpaRepository<JobEntity, Long>
