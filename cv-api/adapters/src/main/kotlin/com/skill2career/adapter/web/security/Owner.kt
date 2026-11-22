package com.skill2career.adapter.web.security

import com.skill2career.domain.profile.OwnerId
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.jwt.Jwt

/** Whose data a request is about: always the token's subject. A token with none is refused. */
fun Jwt.ownerId(): OwnerId = OwnerId(subject?.takeIf { it.isNotBlank() } ?: throw AccessDeniedException("token has no subject"))
