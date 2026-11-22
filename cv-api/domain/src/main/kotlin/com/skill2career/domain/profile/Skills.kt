package com.skill2career.domain.profile

/**
 * A candidate's skills: each once, trimmed, the first spelling kept, as matching ignores case.
 * At least one, as a profile with none can match no job.
 */
class Skills(
    raw: List<String>,
) : List<String> by normalise(raw) {
    init {
        require(isNotEmpty()) { "a profile needs at least one skill" }
    }

    override fun equals(other: Any?): Boolean = other is List<*> && toList() == other

    override fun hashCode(): Int = toList().hashCode()

    override fun toString(): String = toList().toString()

    private companion object {
        fun normalise(raw: List<String>): List<String> =
            raw
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
    }
}
