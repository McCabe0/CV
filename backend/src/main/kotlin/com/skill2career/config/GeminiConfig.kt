package com.skill2career.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class GeminiConfig {

    @Bean
    fun geminiWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com/v1beta")
            .build()
    }
}
