# Skill2Career — Roadmap

A running checklist. Tick items off (`[x]`) as they ship, and link the branch or pull request beside one
in progress. Why each built thing is built the way it is lives in `decisions/`.

---

## ✅ The rebuild (slices 1 to 7)

- [x] **Skeleton** — hexagonal Kotlin api in gradle modules, compose, core ci.
- [x] **Accounts** — Keycloak sign in, through the shell; profiles, CVs and matches belong to the user.
- [x] **Profile** — the profile zone, saved in postgres, with the interviewer's answers kept on it.
- [x] **CV agents** — interviewer, writer and critic, on Gemini or Claude, through an outbox and kafka,
  with live progress over server sent events.
- [x] **Real job sources** — Greenhouse, Lever, Ashby and Workable boards, crawled once a day.
- [x] **Matching** — the jobs zone: matches by skill, remote only and location, why each fits, and a CV
  aimed at one job.
- [x] **Admin** — tracked companies and their crawls: add, stop tracking, crawl now.
- [x] **End-to-end tests** — Playwright journeys for every zone, against the whole stack.
- [x] **The pipeline** — grype, ZAP, k6, mutation testing, helm on kind, DORA.

## 🚀 Product

- [ ] **My CVs** — a list of past CV requests and the CVs they made, to reopen or delete. The api keeps
  them; it serves one at a time today.
- [ ] **Notifications** — a bell in the shared header, with an unread count, for "your questions are
  ready", "your CV is ready" and "your CV failed, try again", each linking to the request. Saved by the
  cv-worker in the same transaction as the status change; the bell polls every 30s or so. So a user can
  look around while a CV is written. Browser push and email later.
- [ ] **Per-user CV limits** — one CV request in progress per user, so one user cannot fill the queue.
- [ ] **Export** — PDF, DOCX and a plain-text ATS version of a finished CV.
- [ ] **Cover letter** — from the CV and a chosen job, with the same writer and critic loop.
- [ ] **Skill-gap insights** — the skills the closest matches ask for that the profile lacks, as "learn
  these 3 to match 8 more roles".
- [ ] **Application tracker** — Interested → Applied → Interview → Offer, on the matched jobs.
- [ ] **Compare CV versions** — keep CVs aimed at different jobs side by side, with a diff.

## ✨ Polish

- [ ] **Profile drafts** — the profile form is long; keep a draft so a refresh does not lose it.
- [ ] **Onboarding** — a guided first run, from profile to first CV to matches.
- [ ] **LLM usage** — calls and quota used, so the cost of the critic's rounds is visible.

## 🛠️ Platform

- [ ] **Ollama as a local LLM provider** — free and private, chosen by `llm.provider=ollama`. See
  [the ticket](docs/tickets/ollama-llm-provider.md).
- [ ] **Deploy** — the helm charts to a real EKS, with terraform, from the pipeline; the DORA events then
  come from the deploy job (`decisions/deploy.md`, `decisions/dora.md`).
- [ ] **Rate limiting** — on the api or at the gateway.

### CV throughput

Many users writing CVs at once is limited by the LLM's quota and speed, not by the system. Today one
cv-worker writes one CV at a time, and an LLM refusal fails a CV after 4 tries 30s apart.

- [ ] **LLM rate limiter** — the LLM adapter holds calls back to stay under the provider's per-minute
  quota, so a burst of users waits a little instead of failing on 429s.
- [ ] **Longer backoff on 429 and 503** — back off further on each try, and honour `Retry-After`, so a
  spike in Gemini's demand is waited out rather than failing the CV.
- [ ] **A model per agent** — `llm.model` for each of the interviewer, writer and critic: a fast, cheap
  model (eg Flash-Lite) for the interviewer and critic, the stronger one for the writer.
- [ ] **Fewer rounds** — track the writer and critic rounds per CV, and improve the writer's prompt
  until most pass the critic on the first round.
- [ ] **Interviews on their own topic** — so a new user's questions never wait behind someone else's
  whole CV.
- [ ] **Scale the cv-worker** — more `cv.requested` partitions (3 today, so at most 3 workers), and the
  cv-worker autoscaled on the topic's lag, as `decisions/architecture.md` intends. Only once on a paid
  quota: on the free tier, more workers only reach the quota sooner.
- [ ] **Agreed SLOs** — the DORA alerts' thresholds are the first ones, and want agreeing.
- [ ] **Mutation survivors** — work through the rest of the ones worth a test (`decisions/mutation.md`).

---

### Suggested order
1. My CVs, notifications and per-user CV limits, with the LLM rate limiter and longer backoff — so a CV
   can be left to write, and a busy LLM means a wait, not a failure.
2. Export — what a user asks for first once they have a CV.
3. A model per agent and fewer rounds, with the move to a paid LLM quota.
4. Cover letters and skill-gap insights — both reuse what exists.
5. Deploy, when there is somewhere to deploy to; scale the cv-worker then.
