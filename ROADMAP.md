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
- [ ] **Agreed SLOs** — the DORA alerts' thresholds are the first ones, and want agreeing.
- [ ] **Mutation survivors** — work through the rest of the ones worth a test (`decisions/mutation.md`).

---

### Suggested order
1. My CVs, then export — what a user asks for first once they have a CV.
2. Cover letters and skill-gap insights — both reuse what exists.
3. Deploy, when there is somewhere to deploy to.
