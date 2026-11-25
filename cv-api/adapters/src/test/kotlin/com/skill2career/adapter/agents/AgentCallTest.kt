package com.skill2career.adapter.agents

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.skill2career.adapter.llm.LlmRequest
import com.skill2career.adapter.llm.LlmUnavailable
import com.skill2career.adapter.llm.LlmUnusableAnswer
import com.skill2career.application.cv.AgentFailure
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.slf4j.LoggerFactory
import kotlin.test.Test

class AgentCallTest {
    private data class Answer(
        val word: String,
    ) {
        init {
            require(word.isNotBlank())
        }
    }

    private val request = LlmRequest("system", "prompt", mapOf("type" to "object"))

    private fun ask(llm: ScriptedLlm) = AgentCall(llm).ask("The tester", request, Answer::class.java) { it }

    /** What the call logs, kept to check. */
    private val logged =
        ListAppender<ILoggingEvent>().apply {
            start()
            (LoggerFactory.getLogger(AgentCall::class.java) as Logger).addAppender(this)
        }

    @AfterEach
    fun stopListening() {
        (LoggerFactory.getLogger(AgentCall::class.java) as Logger).detachAppender(logged)
    }

    @Test
    fun `reads the answer`() {
        assertThat(ask(ScriptedLlm("""{"word":"hello"}"""))).isEqualTo(Answer("hello"))
    }

    @Test
    fun `asks once more after an unusable answer`() {
        val llm = ScriptedLlm(LlmUnusableAnswer("refused"), """{"word":"hello"}""")

        assertThat(ask(llm)).isEqualTo(Answer("hello"))
        assertThat(llm.requests).hasSize(2)
    }

    @Test
    fun `asks once more after json that does not fit`() {
        assertThat(ask(ScriptedLlm("not json", """{"word":"hello"}"""))).isEqualTo(Answer("hello"))
        assertThat(ask(ScriptedLlm("""{"other":1}""", """{"word":"hello"}"""))).isEqualTo(Answer("hello"))
    }

    @Test
    fun `asks once more after an answer the domain refuses`() {
        assertThat(ask(ScriptedLlm("""{"word":" "}""", """{"word":"hello"}"""))).isEqualTo(Answer("hello"))
    }

    @Test
    fun `fails the agent after a second unusable answer`() {
        val llm = ScriptedLlm(LlmUnusableAnswer("refused"), "not json")

        assertThatThrownBy { ask(llm) }
            .isInstanceOf(AgentFailure::class.java)
            .hasMessageStartingWith("The tester gave no usable answer")
    }

    @Test
    fun `lets an unavailable provider through, so the work is retried later`() {
        assertThatThrownBy { ask(ScriptedLlm(LlmUnavailable("rate limited"))) }.isInstanceOf(LlmUnavailable::class.java)
    }

    @Test
    fun `logs which agent answered, and how long it took`() {
        ask(ScriptedLlm("""{"word":"hello"}"""))

        val info = logged.list.single()
        assertThat(info.level).isEqualTo(Level.INFO)
        assertThat(info.formattedMessage).containsPattern("The tester answered in \\d+ms")
    }

    @Test
    fun `warns why an answer was unusable before asking once more`() {
        ask(ScriptedLlm(LlmUnusableAnswer("refused"), """{"word":"hello"}"""))

        val (warning, info) = logged.list
        assertThat(warning.level).isEqualTo(Level.WARN)
        assertThat(warning.formattedMessage).contains("The tester", "refused", "asking once more")
        assertThat(info.formattedMessage).contains("The tester answered")
    }
}
