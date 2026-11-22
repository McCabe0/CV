package com.skill2career.app.wiring

import com.skill2career.adapter.messaging.OutboxRelay
import com.skill2career.adapter.messaging.Topics
import com.skill2career.adapter.persistence.cv.CvRequestJpaAdapter
import com.skill2career.adapter.persistence.job.JobJpaAdapter
import com.skill2career.adapter.persistence.job.TrackedCompanyJpaAdapter
import com.skill2career.adapter.persistence.profile.ProfileJpaAdapter
import com.skill2career.adapter.web.security.SecurityConfiguration
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.kafka.config.TopicBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * What every role needs: the database and its outbox, kafka's topics, and security, as every role
 * serves its health checks. The api and the cv-worker both write the outbox, so every role relays it.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EntityScan("com.skill2career.adapter.persistence", "com.skill2career.adapter.messaging")
@EnableJpaRepositories("com.skill2career.adapter.persistence", "com.skill2career.adapter.messaging")
@Import(
    SecurityConfiguration::class,
    ProfileJpaAdapter::class,
    CvRequestJpaAdapter::class,
    TrackedCompanyJpaAdapter::class,
    JobJpaAdapter::class,
    OutboxRelay::class,
)
class CommonWiring {
    /** Partitions are also how many job-workers crawl at once. */
    @Bean
    fun jobsCrawlTopic(
        @Value("\${kafka.partitions:3}") partitions: Int,
        @Value("\${kafka.replicas:1}") replicas: Int,
    ): NewTopic = topic(Topics.JOBS_CRAWL, partitions, replicas)

    /** Partitions are how far the cv-worker can scale out: one consumer each. See decisions/messaging.md. */
    @Bean
    fun cvRequestedTopic(
        @Value("\${kafka.partitions:3}") partitions: Int,
        @Value("\${kafka.replicas:1}") replicas: Int,
    ): NewTopic = topic(Topics.CV_REQUESTED, partitions, replicas)

    @Bean
    fun cvProgressTopic(
        @Value("\${kafka.partitions:3}") partitions: Int,
        @Value("\${kafka.replicas:1}") replicas: Int,
    ): NewTopic = topic(Topics.CV_PROGRESS, partitions, replicas)

    @Bean
    fun outboxSchedule(relay: OutboxRelay): OutboxSchedule = OutboxSchedule(relay)

    private fun topic(
        name: String,
        partitions: Int,
        replicas: Int,
    ): NewTopic =
        TopicBuilder
            .name(name)
            .partitions(partitions)
            .replicas(replicas)
            .build()
}

/** Sends the outbox every moment. Each instance runs one; the relay's row locks keep them apart. */
class OutboxSchedule(
    private val relay: OutboxRelay,
) {
    @Scheduled(fixedDelayString = "\${outbox.relay-delay-ms:500}")
    fun relay() {
        relay.relay()
    }
}
