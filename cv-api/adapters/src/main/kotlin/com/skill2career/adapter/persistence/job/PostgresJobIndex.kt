package com.skill2career.adapter.persistence.job

import com.skill2career.application.job.JobIndex
import com.skill2career.domain.job.JobFilter
import com.skill2career.domain.job.ListedJob
import com.skill2career.domain.job.Workplace
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * The [JobIndex] port, on postgres full text search (`jobs.search`, V7). Each term is searched as a
 * phrase, any of them matching, so "Spring Boot" is not found in "Spring cleaning, then boot camp".
 * Terms are only ever bound as values: what they say is never read as search syntax or sql.
 */
@Repository
@Transactional(readOnly = true)
class PostgresJobIndex(
    private val entities: EntityManager,
    private val companies: TrackedCompanyJpaRepo,
) : JobIndex {
    override fun search(
        terms: List<String>,
        filter: JobFilter,
        limit: Int,
    ): List<ListedJob> {
        val query = query(terms) ?: return emptyList()
        val place = filter.place

        val conditions =
            listOfNotNull(
                "j.inactive_since IS NULL",
                "j.search @@ websearch_to_tsquery('simple', :query)",
                "j.workplace = :remote".takeIf { filter.remoteOnly },
                // strpos, not like, so a % or _ in the place is taken as written.
                "strpos(lower(j.location), lower(:place)) > 0".takeIf { place != null },
            )
        val sql =
            """
            SELECT j.* FROM {h-schema}jobs j
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY ts_rank(j.search, websearch_to_tsquery('simple', :query)) DESC, j.first_seen_at DESC, j.id
            LIMIT :limit
            """.trimIndent()

        val found =
            entities
                .createNativeQuery(sql, JobEntity::class.java)
                .setParameter("query", query)
                .setParameter("limit", limit)
                .apply { if (filter.remoteOnly) setParameter("remote", Workplace.REMOTE.name) }
                .apply { if (place != null) setParameter("place", place) }
                .resultList
                .filterIsInstance<JobEntity>()

        val names = companies.findAllById(found.map { it.companyId }.toSet()).associate { it.id to it.name }
        return found.map { ListedJob(it.toDomain(), names.getValue(it.companyId)) }
    }

    /**
     * The terms as one websearch query: each a quoted phrase, joined by or. Quotes are taken out of a term
     * first, so it cannot end its phrase early; any other syntax inside a phrase is only punctuation.
     */
    private fun query(terms: List<String>): String? =
        terms
            .map { it.replace("\"", " ").trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { return null }
            .joinToString(" or ") { "\"$it\"" }
}
