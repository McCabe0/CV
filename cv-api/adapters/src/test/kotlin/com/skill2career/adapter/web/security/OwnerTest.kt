package com.skill2career.adapter.web.security

import com.skill2career.domain.profile.OwnerId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.jwt.Jwt
import kotlin.test.Test

class OwnerTest {
    private fun token(sub: String?): Jwt =
        Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .claim("scope", "openid")
            .apply { sub?.let { subject(it) } }
            .build()

    @Test
    fun `the owner is the token's subject`() {
        assertThat(token("abc").ownerId()).isEqualTo(OwnerId("abc"))
    }

    @Test
    fun `refuses a token with no subject, or a blank one`() {
        assertThatThrownBy { token(null).ownerId() }.isInstanceOf(AccessDeniedException::class.java)
        assertThatThrownBy { token(" ").ownerId() }.isInstanceOf(AccessDeniedException::class.java)
    }
}
