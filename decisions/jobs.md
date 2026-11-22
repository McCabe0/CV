## context
The first version asked Gemini to make up job listings. None were real, so none could be applied for.
Jobs must be live, on the company's own website, and match the user's skills.

## decision: applicant tracking system boards
Most companies publish their jobs through an applicant tracking system, and the main ones give each
company a free, public, json job board. The apply link on each is the company's own careers page.

| source | board url | apply link |
|---|---|---|
| Greenhouse | `boards-api.greenhouse.io/v1/boards/{company}/jobs?content=true` | `absolute_url` |
| Lever | `api.lever.co/v0/postings/{company}?mode=json` | `hostedUrl` / `applyUrl` |
| Ashby | `api.ashbyhq.com/posting-api/job-board/{company}` | `jobUrl` |
| Workable | `apply.workable.com/api/v1/widget/accounts/{company}` | `url` |

Each is an adapter behind one port:
```kotlin
interface JobSource {
    val type: SourceType
    fun fetch(company: TrackedCompany): List<JobPosting>
}
```
No api keys, no scraping, and no aggregators, whose links lead back to the aggregator.

## decision: tracked companies, crawled on a schedule
- an admin keeps the list of tracked companies (name, source, board token) in the admin zone.
- a scheduler in the `job-worker` puts one `jobs.crawl` event per company on kafka, so the work spreads
  over every job-worker, and one slow board holds up no other.
- a crawl upserts each posting by `(source, external id)`. A job missing from its board's latest crawl is
  marked inactive, with when, and is never matched again. So every job matched is one the board listed
  at its last crawl.
- a board that fails is retried, then skipped until the next crawl. Its jobs stay as they were, so one
  failing board does not empty the results.

## decision: matching against our own copy
A user's match runs against the jobs in postgres: skill overlap, then postgres full text search on the
title and description. It is fast and does not touch the boards, so the boards' rate limits are only
ever hit by the crawl, never by users. An optional LLM explanation of the top matches goes through
kafka, as the CV does.

## decision: once a day, one crawl at a time
- the crawl runs once a day. Job posts rarely change faster, and it keeps us polite to the boards.
- a postgres advisory lock lets only one job-worker queue the day's crawl, however many run. No extra
  library: the lock is postgres's own.
- a job its board lists again, after going inactive, is active again.

## starting companies
Checked live on 2026-11-22: Monzo, GitLab and Cloudflare (Greenhouse), Palantir and Spotify (Lever),
Ramp and Linear (Ashby), Blueground (Workable). The admin zone takes over the list in slice 6.
