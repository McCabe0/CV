package com.skill2career.repository

import com.skill2career.entity.UserProfileEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserProfileRepository : JpaRepository<UserProfileEntity, Long>
