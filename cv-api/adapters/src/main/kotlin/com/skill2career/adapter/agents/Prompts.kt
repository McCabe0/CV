package com.skill2career.adapter.agents

import com.skill2career.domain.cv.Cv
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.Fact
import com.skill2career.domain.profile.Profile

/** Json schema, as maps, written so both providers accept it: every property required, no others allowed. */
internal object Schema {
    val string = mapOf("type" to "string")
    val integer = mapOf("type" to "integer")
    val boolean = mapOf("type" to "boolean")

    fun array(items: Map<String, Any>) = mapOf("type" to "array", "items" to items)

    fun oneOf(values: List<String>) = mapOf("type" to "string", "enum" to values)

    fun obj(vararg properties: Pair<String, Map<String, Any>>): Map<String, Any> =
        mapOf(
            "type" to "object",
            "properties" to properties.toMap(),
            "required" to properties.map { it.first },
            "additionalProperties" to false,
        )

    val cv: Map<String, Any> =
        obj(
            "summary" to string,
            "skills" to array(string),
            "roles" to
                array(obj("title" to string, "company" to string, "dates" to string, "bullets" to array(string))),
            "education" to array(string),
            "projects" to array(string),
            "certifications" to array(string),
            "languages" to array(string),
        )
}

/**
 * What the agents are shown. The candidate's words are marked off in tags, so they are read as facts
 * about the candidate, never as instructions.
 */
internal object Prompts {
    fun profile(profile: Profile): String =
        buildString {
            appendLine("<profile>")
            line("Name", profile.name)
            line("Target role", profile.targetRole)
            line("Years of experience", profile.yearsOfExperience)
            line("Location", profile.location)
            line("Right to work", profile.workAuthorization)
            line("Skills", profile.skills.joinToString(", "))
            line("Experience", profile.experience)
            profile.workHistory.forEach { role ->
                line("Role", listOfNotNull(role.title, role.company, dates(role.startDate, role.endDate)).joinToString(", "))
                role.bullets.forEach { appendLine("  - $it") }
            }
            line("Education", profile.education)
            list("Projects", profile.projects)
            list("Certifications", profile.certifications)
            list("Languages", profile.languages)
            appendLine("</profile>")
            append(answers(profile.facts))
        }

    /** The candidate's answers to the interviewer, as facts as good as the profile's. */
    private fun answers(facts: List<Fact>): String =
        if (facts.isEmpty()) {
            ""
        } else {
            buildString {
                appendLine("<answers>")
                facts.forEach { appendLine("Q: ${it.question}\nA: ${it.answer}") }
                appendLine("</answers>")
            }
        }

    fun draft(cv: Cv): String =
        buildString {
            appendLine("Summary: ${cv.summary}")
            appendLine("Skills: ${cv.skills.joinToString(", ")}")
            cv.roles.forEach { role ->
                appendLine("Role: ${listOfNotNull(role.title, role.company, role.dates).joinToString(", ")}")
                role.bullets.forEach { appendLine("  - $it") }
            }
            cv.education.forEach { appendLine("Education: $it") }
            cv.projects.forEach { appendLine("Project: $it") }
            cv.certifications.forEach { appendLine("Certification: $it") }
            if (cv.languages.isNotEmpty()) appendLine("Languages: ${cv.languages.joinToString(", ")}")
        }

    fun feedback(round: Round): String =
        buildString {
            appendLine("<earlier>")
            appendLine("Earlier draft:")
            append(draft(round.draft))
            appendLine("The critic scored it ${round.review.score} out of 100, and found:")
            round.review.issues.forEach { appendLine("- ${it.kind}: ${it.detail}") }
            appendLine("</earlier>")
        }

    private fun dates(
        start: String?,
        end: String?,
    ) = start?.let { "$it to ${end ?: "now"}" }

    private fun StringBuilder.line(
        label: String,
        value: String?,
    ) {
        if (!value.isNullOrBlank()) appendLine("$label: $value")
    }

    private fun StringBuilder.list(
        label: String,
        values: List<String>,
    ) {
        if (values.isNotEmpty()) appendLine("$label: ${values.joinToString("; ")}")
    }
}
