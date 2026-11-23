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

Every reader passes one contract test (`JobSourceContract`), with wiremock playing its board:
- descriptions are kept as plain text, a line per paragraph, heading and list item, for matching. Lever and
  Ashby send it; Greenhouse and Workable send html, which jsoup turns into text.
- a posting missing its id, title or link is skipped, so one bad posting loses no other.
- a board that is unknown (404) is `UnknownJobBoard`, not retried: its token is wrong. Any other failure,
  or an answer without its list of jobs, is `JobBoardUnavailable`, retried. Never an empty list, which
  would mark every job inactive.

## decision: tracked companies, crawled on a schedule
- an admin keeps the list of tracked companies (name, source, board token) in the admin zone.
- a scheduler in the `job-worker` puts one `jobs.crawl` event per company on kafka, so the work spreads
  over every job-worker, and one slow board holds up no other.
- a crawl upserts each posting by `(company, external id)`: the board's own id, unique per company. A job
  missing from its board's latest crawl is marked inactive, with when, and is not matched while it is.
  So every job matched is one the board listed at its last crawl.
- a board that fails is retried, then skipped until the next crawl. Its jobs stay as they were, so one
  failing board does not empty the results.

## decision: matching against our own copy
A user's match runs against the jobs in postgres: skill overlap, then postgres full text search on the
title and description. It is fast and does not touch the boards, so the boards' rate limits are only
ever hit by the crawl, never by users. An optional LLM explanation of the top matches goes through
kafka, as the CV does.

In two steps (`MatchJobs`):
1. the `JobIndex` port finds up to 200 active jobs that the filter accepts and that mention any of the
   candidate's skills, best postgres text match first.
2. the domain's `Matching.rank` keeps those asking for at least one skill and orders them: most of the
   candidate's skills asked for, then most named in the title, then newest. The best 50 are shown, each
   with the skills it asks for.

A skill is mentioned whatever its case, as a whole word, so Java is not found in JavaScript, while C#,
C++ and .NET are. Short skills that are also words, like Go, can match by accident; good enough to start.

## decision: remote only, and location (2026-11-23)
A candidate can ask for remote jobs only, and for a place, eg "London" or "UK", which keeps jobs whose
location names it. Both together keep remote jobs in that place. Remote only trusts the board: a job
whose board does not say where it is done is left out, never guessed.

| source | where it is done |
|---|---|
| Lever | `workplaceType`: remote, hybrid, onsite |
| Ashby | `workplaceType`: Remote, Hybrid, OnSite. Not `isRemote`, which is also true for hybrid jobs |
| Workable | `telecommuting`: true is remote, otherwise not said |
| Greenhouse | location text only: "Remote" or "Distributed" (Cloudflare's word) is remote, otherwise not said |

## decision: why a job fits, on asking (2026-11-23)
A match has a "why it fits" button. The first ask queues an LLM summary through kafka and the answer is
saved, so each candidate's summary for a job is written once. Not written for every match up front, as
each costs a request from the LLM's free daily quota.

## decision: once a day, one crawl at a time
- the crawl runs once a day. Job posts rarely change faster, and it keeps us polite to the boards.
- only one job-worker queues the day's crawl, however many run, using postgres alone: a `crawl_runs` row
  per day, the date its primary key. Each job-worker asks a minute after it starts, then hourly; the
  first to insert today's row queues the crawl, in the same transaction, and every later ask finds the
  row and does nothing. So a worker that was down at the usual time still crawls that day, and none
  crawls it twice. (Decided 2026-11-22. An advisory lock was the first idea, but a lock only stops two
  workers queueing at the same moment, not one after the other.)
- a crawl that fails is tried 3 times in all, a minute apart (`jobs.crawl.attempts`,
  `jobs.crawl.retry-delay-ms`), then parked on `jobs.crawl.dlq` until the next day. An unknown board, a
  company no longer tracked, or a board with no reader goes there at once.
- a job its board lists again, after going inactive, is active again.

## starting companies
Checked live on 2026-11-22: Monzo, GitLab and Cloudflare (Greenhouse), Palantir and Spotify (Lever),
Ramp and Linear (Ashby), Blueground (Workable). The admin zone takes over the list in slice 6.

## running it locally
`local/crawl-now.sh` crawls every board now on the compose stack: it clears today's `crawl_runs` row and
recreates the job-worker, which asks for the crawl a minute after it starts, then counts each company's
jobs as they arrive. First run, 2026-11-22: 1,431 jobs from the eight boards in under a minute.
