package com.skill2career.app.role

import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import kotlin.test.Test

class ConditionalOnRoleTest {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnRole(AppRole.CV_WORKER)
    class CvWorkerOnly

    private val runner = ApplicationContextRunner().withUserConfiguration(CvWorkerOnly::class.java)

    @Test
    fun `loads when the process runs the role`() {
        runner.withPropertyValues("app.roles=api,cv-worker").run {
            assertThat(it).hasSingleBean(CvWorkerOnly::class.java)
        }
    }

    @Test
    fun `does not load when the process does not run the role`() {
        runner.withPropertyValues("app.roles=api").run {
            assertThat(it).doesNotHaveBean(CvWorkerOnly::class.java)
        }
    }

    @Test
    fun `fails to start when the roles are misspelt`() {
        runner.withPropertyValues("app.roles=cv-wroker").run {
            assertThat(it).hasFailed()
        }
    }
}
