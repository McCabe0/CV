package com.skill2career.adapter.web.profile

import com.skill2career.adapter.web.security.ownerId
import com.skill2career.application.profile.FindProfile
import com.skill2career.application.profile.SaveProfile
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The signed in candidate's own profile. The owner is always the token's subject, never the body. */
@Tag(name = "profile")
@RestController
@RequestMapping("/profile")
@PreAuthorize("hasRole('CANDIDATE')")
class ProfileResource(
    private val saveProfile: SaveProfile,
    private val findProfile: FindProfile,
) {
    @Operation(summary = "Your profile", description = "404 until you have saved one.")
    @GetMapping
    fun find(
        @AuthenticationPrincipal jwt: Jwt,
    ): ProfileResponse = ProfileResponse.of(findProfile.find(jwt.ownerId()))

    @Operation(summary = "Save your profile", description = "Creates it, or replaces the one you have.")
    @PutMapping
    fun save(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ProfileRequest,
    ): ProfileResponse = ProfileResponse.of(saveProfile.save(request.toProfile(jwt.ownerId())))
}
