package com.skill2career.domain.job

import com.skill2career.domain.profile.Skills

/**
 * What a candidate narrows their matches to. Remote only keeps just the jobs whose board says they are
 * remote, so a job whose board does not say is left out. A location keeps the jobs whose location names it.
 */
data class JobFilter(
    val remoteOnly: Boolean = false,
    val location: String? = null,
) {
    /** The location asked for, or null when none was. */
    val place: String? get() = location?.trim()?.ifEmpty { null }

    fun accepts(posting: JobPosting): Boolean {
        if (remoteOnly && posting.workplace != Workplace.REMOTE) return false
        val place = place ?: return true
        return posting.location?.contains(place, ignoreCase = true) == true
    }
}

/** A job, with the name of the company that lists it. */
data class ListedJob(
    val job: Job,
    val company: String,
)

/** A job that fits a candidate, and which of their skills it asks for, in their spelling. */
data class JobMatch(
    val job: Job,
    val company: String,
    val matchedSkills: List<String>,
)

object Matching {
    /**
     * Whether [text] mentions [skill]: whatever its case, and as a whole word, so Java is not found in
     * JavaScript, while C#, C++ and .NET still are.
     */
    fun mentions(
        text: String,
        skill: String,
    ): Boolean = wholeWord(skill).containsMatchIn(text)

    /**
     * The [candidates] that ask for at least one of the [skills], best first: those asking for the most
     * skills, then those naming more of them in their title, then the newest. Inactive jobs never match.
     */
    fun rank(
        skills: Skills,
        candidates: List<ListedJob>,
    ): List<JobMatch> {
        val patterns = skills.associateWith(::wholeWord)
        return candidates
            .filter { it.job.active }
            .map { listed ->
                val posting = listed.job.posting
                val text = posting.title + "\n" + posting.description
                val matched = patterns.filterValues { it.containsMatchIn(text) }
                val inTitle = matched.values.count { it.containsMatchIn(posting.title) }
                Ranked(JobMatch(listed.job, listed.company, matched.keys.toList()), inTitle)
            }.filter { it.match.matchedSkills.isNotEmpty() }
            .sortedWith(
                compareByDescending<Ranked> { it.match.matchedSkills.size }
                    .thenByDescending { it.inTitle }
                    .thenByDescending { it.match.job.firstSeenAt }
                    .thenBy { it.match.job.id },
            ).map { it.match }
    }

    private class Ranked(
        val match: JobMatch,
        val inTitle: Int,
    )

    /** Not next to another letter or digit, so a skill's own symbols, like the + in C++, still count. */
    private fun wholeWord(skill: String) =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(skill.trim()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
}
