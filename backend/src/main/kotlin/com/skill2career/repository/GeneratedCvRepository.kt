package com.skill2career.repository

import com.skill2career.entity.GeneratedCvEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GeneratedCvRepository : JpaRepository<GeneratedCvEntity, Long>
