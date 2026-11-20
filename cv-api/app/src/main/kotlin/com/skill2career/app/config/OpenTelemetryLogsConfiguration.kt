package com.skill2career.app.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component

/** Logback starts before spring, so the appender in logback-spring.xml cannot be given opentelemetry until now. */
@Component
class OpenTelemetryLogsConfiguration(
    private val openTelemetry: OpenTelemetry,
) : InitializingBean {
    override fun afterPropertiesSet() = OpenTelemetryAppender.install(openTelemetry)
}
