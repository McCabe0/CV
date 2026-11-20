# Skill2Career — Feature Roadmap

A running checklist of proposed features. Tick items off (`[x]`) as they ship.
Ordered roughly by impact: the **Core gaps** turn this from a demo into a product.

---

## 🚀 Core gaps (highest value)

- [ ] **Accounts & authentication** — let users log back in and see their saved profiles, CVs, and past matches. The backend already persists these; it just needs auth (email magic-link or OAuth) tied to the existing entities.
- [ ] **My CVs / History view** — a dashboard listing previously generated CVs and past job-match runs, with the ability to reopen or delete each.
- [ ] **Save & compare multiple CV versions** — regenerating currently overwrites. Allow keeping variants tailored to different roles, with a side-by-side diff.
- [ ] **Real job sources** — matches are AI-generated mock listings today. Integrate a live feed (Adzuna, JSearch/RapidAPI, or public Greenhouse/Lever boards) so results are real and applyable.
- [ ] **Cover letter generation** — generate a tailored cover letter from the CV + a selected job (all inputs already exist).
- [ ] **Skill-gap insights** — aggregate `requiredSkillsMissing` across matches into "learn these 3 skills to unlock 8 more roles," with suggested course links.

## ✨ UX & product polish

- [ ] **Application tracker** — a kanban board (Interested → Applied → Interview → Offer) layered on the match results.
- [ ] **Inline validation & autosave** for the profile form — it's long; persist a draft to localStorage so a refresh doesn't lose it.
- [ ] **More export formats** — DOCX, plain-text ATS version, and a shareable read-only link, in addition to the current PDF.
- [ ] **Onboarding / empty states** — a guided first run and friendly empty states instead of bare "no data" text.
- [ ] **Dark-mode-aware PDF export** — ensure the generated PDF stays light/print-friendly regardless of the app theme.

## 🛠️ Technical / platform

- [ ] **Gemini usage & rate-limit indicator** — surface remaining quota / call counts so users understand the "uses more API calls" trade-offs.
- [ ] **Request de-duplication & optimistic UI** — avoid redundant calls and make interactions feel instant.
- [ ] **End-to-end tests** — Playwright coverage for the full 3-step flow (profile → CV → matches).
- [ ] **Code-splitting** — dynamic-import the heavy PDF stack (jspdf/html2canvas) to clear the >500 kB bundle warning.

---

### Suggested build order
1. Accounts + History view (unlocks everything else)
2. Real job data
3. Cover letters + skill-gap insights
4. Application tracker

> Tip: when you start an item, link the PR/branch next to it, e.g. `- [ ] Accounts — _in progress (branch: feat/auth)_`.
