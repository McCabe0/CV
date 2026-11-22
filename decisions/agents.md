## context
The first version sent one fixed prompt to Gemini and used whatever came back. The CV was only as good
as that prompt, and nothing checked it: invented facts, vague bullets and missed keywords got through.

## decision
Three agents, in two stages, in the `cv-worker` role. Each is a small class with one job, and none knows
which LLM it talks to.

### stage 1: the interviewer
Reads the profile and finds what a strong CV needs but the profile does not say: a bullet with no
number, a skill with no evidence, a gap in dates, a target role with no matching experience. It asks the
user up to five questions (should it ask more, the first five are kept). The user answers, or skips, in
the profile zone. The answers are saved to the profile as facts the user gave, so a later CV, eg one
tailored to a job, reuses them, and the interviewer does not ask again what they already say. Skipping
is always allowed, so a CV is never blocked on it.

### stage 2: the writer and the critic
```
profile + answers ──→ writer ──draft──→ critic ──approved──→ CV
                        ↑                  │
                        └────feedback──────┘   (up to 3 rounds)
```
- the writer drafts the CV from the profile, the user's answers and, after the first round, the
  critic's feedback.
- the critic scores the draft against a rubric and returns structured feedback, never prose to be
  parsed:
  - every claim is in the profile or the answers (no invented employers, dates, numbers or skills)
  - bullets show impact, with numbers where the profile has them
  - the target role's keywords are present, for applicant tracking systems
  - the summary is specific to this person and role
- the loop ends when the critic approves, or after 3 rounds, when the best scoring draft is kept.
- every round is stored: the draft, the score and the feedback. So the ui can show the agents at work,
  and a bad CV can be traced to the round that made it.

The prompts are built each time from the profile, the answers and the last feedback. There is no
stored prompt to go stale.

## the ports
The application owns the loop and its rules, and sees each agent as a port:
```kotlin
interface Interviewer { fun questionsFor(profile: Profile): List<Question> }
interface Writer { fun write(brief: Brief): Cv }
interface Critic { fun review(brief: Brief, draft: Cv): Review }
```
The adapters implement them with an LLM: each builds its prompt, asks for json against a schema and
reads the answer. So the application stays pure kotlin with no json library, and its tests use plain
fakes. (Decided 2026-11-22, in place of agents in the application on an `LlmClient` port.)

Under the agents, in the adapters:
```kotlin
interface LlmClient {
    fun complete(request: LlmRequest): LlmResponse
}
```
`LlmRequest` holds a system instruction, the messages and a json schema for the answer. A response that
does not fit the schema is a failure, retried once, not a guess.

Two adapters, chosen by `llm.provider=gemini|claude`. One contract test suite runs against both, each
against wiremock playing that provider's api, so changing provider is a tested change of setting.
The interviewer, writer and critic can each use a different provider or model
(`llm.writer.provider`, ...), so a cheaper model can critique a stronger one's work, if wanted.

## costs
- up to 7 LLM calls per CV (1 interviewer, 3 writer, 3 critic), against 1 before. Mitigated by the
  round limit, and by the critic usually approving by round 2.
- json schema output depends on the provider supporting it. Both do today.
