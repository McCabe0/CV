package com.skill2career.adapter.agents

import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/** What the writer and critic work to: the guide, and an example CV for the target role, if one fits. */
data class Guidance(
    val guide: String,
    /** A strong CV for a similar role, for its style only. None when nothing fits. */
    val example: String?,
)

/** The CV guide. From files now; from the database, kept in the admin zone, later. See decisions/agents.md. */
fun interface CvGuide {
    fun forRole(targetRole: String?): Guidance
}

/**
 * The guide and examples in `cv-guide/` on the classpath, read once. An example's front matter names the
 * roles it suits; the one matching most of them, as whole words in the target role, is given.
 */
class FileCvGuide : CvGuide {
    private class Example(
        val roles: List<Regex>,
        val text: String,
    )

    private val resources = PathMatchingResourcePatternResolver()

    private val guide = resources.getResource("classpath:cv-guide/guide.md").getContentAsString(Charsets.UTF_8).trim()

    private val examples =
        resources
            .getResources("classpath:cv-guide/examples/*.md")
            .sortedBy { it.filename }
            .map { example(it.getContentAsString(Charsets.UTF_8)) }

    override fun forRole(targetRole: String?): Guidance {
        val role = targetRole?.lowercase().orEmpty()
        val best =
            examples
                .map { example -> example to example.roles.count { it.containsMatchIn(role) } }
                .filter { (_, matches) -> matches > 0 }
                .maxByOrNull { (_, matches) -> matches }
        return Guidance(guide, best?.first?.text)
    }

    private fun example(file: String): Example {
        val (frontMatter, body) = FRONT_MATTER.find(file)!!.destructured
        val roles =
            frontMatter
                .lines()
                .first { it.startsWith("roles:") }
                .removePrefix("roles:")
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .map { Regex("\\b${Regex.escape(it)}\\b") }
        return Example(roles, body.replace(NOTE, "").trim())
    }

    private companion object {
        val FRONT_MATTER = Regex("""^---\s*\n(.*?)\n---\s*\n(.*)$""", RegexOption.DOT_MATCHES_ALL)

        // A note for us, eg that the example is invented, not for the writer.
        val NOTE = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    }
}
