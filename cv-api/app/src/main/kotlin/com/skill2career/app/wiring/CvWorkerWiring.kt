package com.skill2career.app.wiring

import com.skill2career.adapter.agents.CvGuide
import com.skill2career.adapter.agents.FileCvGuide
import com.skill2career.adapter.agents.LlmCritic
import com.skill2career.adapter.agents.LlmFitExplainer
import com.skill2career.adapter.agents.LlmInterviewer
import com.skill2career.adapter.agents.LlmWriter
import com.skill2career.adapter.llm.LlmClient
import com.skill2career.adapter.llm.LlmClients
import com.skill2career.adapter.llm.LlmProperties
import com.skill2career.adapter.messaging.CvWorkListener
import com.skill2career.adapter.messaging.FitWorkListener
import com.skill2career.app.role.AppRole
import com.skill2career.app.role.ConditionalOnRole
import com.skill2career.application.cv.AdvanceCvRequest
import com.skill2career.application.cv.Critic
import com.skill2career.application.cv.CvRequestStore
import com.skill2career.application.cv.GiveUpOnCvRequest
import com.skill2career.application.cv.InterviewCandidate
import com.skill2career.application.cv.Interviewer
import com.skill2career.application.cv.WriteCv
import com.skill2career.application.cv.Writer
import com.skill2career.application.job.FitExplainer
import com.skill2career.application.job.FitSummaryStore
import com.skill2career.application.job.GiveUpOnFitSummary
import com.skill2career.application.job.JobStore
import com.skill2career.application.job.ListedJobs
import com.skill2career.application.job.TrackedCompanyStore
import com.skill2career.application.job.WriteFitSummary
import com.skill2career.application.profile.ProfileStore
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * The cv-worker: the three CV agents, and the fit summary, on the LLM `llm.provider` names, fed from
 * kafka. See decisions/agents.md.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(AppRole.CV_WORKER)
@EnableConfigurationProperties(LlmProperties::class)
@Import(CvWorkListener::class, FitWorkListener::class)
class CvWorkerWiring {
    @Bean
    fun llm(properties: LlmProperties): LlmClient = LlmClients.create(properties)

    @Bean
    fun interviewer(llm: LlmClient): Interviewer = LlmInterviewer(llm)

    @Bean
    fun writer(
        llm: LlmClient,
        guide: CvGuide,
    ): Writer = LlmWriter(llm, guide)

    @Bean
    fun critic(
        llm: LlmClient,
        guide: CvGuide,
    ): Critic = LlmCritic(llm, guide)

    /** The CV guide and examples, read once, from cv-guide/ in the image. */
    @Bean
    fun cvGuide(): CvGuide = FileCvGuide()

    @Bean
    fun interviewCandidate(
        requests: CvRequestStore,
        profiles: ProfileStore,
        interviewer: Interviewer,
    ): InterviewCandidate = InterviewCandidate(requests, profiles, interviewer)

    @Bean
    fun writeCv(
        requests: CvRequestStore,
        profiles: ProfileStore,
        writer: Writer,
        critic: Critic,
        jobs: ListedJobs,
    ): WriteCv = WriteCv(requests, profiles, writer, critic, jobs)

    @Bean
    fun advanceCvRequest(
        interview: InterviewCandidate,
        write: WriteCv,
    ): AdvanceCvRequest = AdvanceCvRequest(interview, write)

    @Bean
    fun giveUpOnCvRequest(requests: CvRequestStore): GiveUpOnCvRequest = GiveUpOnCvRequest(requests)

    /** The job a CV is aimed at, with its company, as the writer and critic are shown it. */
    @Bean
    fun listedJobs(
        jobs: JobStore,
        companies: TrackedCompanyStore,
    ): ListedJobs = ListedJobs(jobs, companies)

    @Bean
    fun fitExplainer(llm: LlmClient): FitExplainer = LlmFitExplainer(llm)

    @Bean
    fun writeFitSummary(
        summaries: FitSummaryStore,
        profiles: ProfileStore,
        jobs: JobStore,
        companies: TrackedCompanyStore,
        explainer: FitExplainer,
    ): WriteFitSummary = WriteFitSummary(summaries, profiles, jobs, companies, explainer)

    @Bean
    fun giveUpOnFitSummary(summaries: FitSummaryStore): GiveUpOnFitSummary = GiveUpOnFitSummary(summaries)
}
