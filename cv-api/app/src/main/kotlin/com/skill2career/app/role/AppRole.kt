package com.skill2career.app.role

/** What a process does. One image runs one or more of these. See decisions/architecture.md. */
enum class AppRole(
    val key: String,
) {
    API("api"),
    CV_WORKER("cv-worker"),
    JOB_WORKER("job-worker"),
    ;

    companion object {
        fun of(key: String): AppRole =
            entries.firstOrNull { it.key == key.trim().lowercase() }
                ?: throw IllegalArgumentException("unknown app role '$key', expected one of ${entries.map { it.key }}")
    }
}
