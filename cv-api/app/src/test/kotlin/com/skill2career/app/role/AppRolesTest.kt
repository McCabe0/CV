package com.skill2career.app.role

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class AppRolesTest {
    @Test
    fun `reads one role`() {
        assertThat(AppRoles.parse("api").roles).containsExactly(AppRole.API)
    }

    @Test
    fun `reads several roles, ignoring spaces and case`() {
        assertThat(AppRoles.parse(" API, cv-worker ,job-worker").roles)
            .containsExactlyInAnyOrder(AppRole.API, AppRole.CV_WORKER, AppRole.JOB_WORKER)
    }

    @Test
    fun `says which roles it runs`() {
        val roles = AppRoles.parse("cv-worker")

        assertThat(roles.runs(AppRole.CV_WORKER)).isTrue()
        assertThat(roles.runs(AppRole.API)).isFalse()
    }

    @Test
    fun `refuses an unknown role, so a misspelt setting fails at start`() {
        assertThatThrownBy { AppRoles.parse("api,cv-wroker") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cv-wroker")
    }

    @Test
    fun `refuses no roles, as the process would do nothing`() {
        assertThatThrownBy { AppRoles.parse(" , ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
