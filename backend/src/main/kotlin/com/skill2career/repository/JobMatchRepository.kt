package com.skill2career.repository

import com.skill2career.entity.JobMatchEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JobMatchRepository : JpaRepository<JobMatchEntity, Long>
