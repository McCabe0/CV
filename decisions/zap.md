## context
The tests say the service does what it should. Nothing checks what an attacker could do: the headers,
cookies and error pages the ui sends, or how the api answers input built to break it. Following the
digital-resolutions-poc's decision, adapted (decided 2026-11-24).

## decision
OWASP ZAP. `ci/zap.sh` runs three scans at once, or the ones named (`ci/zap.sh candidate admin`):

- **candidate:** a passive baseline scan of the ui from home, signed in as the test realm's candidate,
  through the profile, cv and jobs zones. Passive: it only reads what the pages send.
- **admin:** the same, of the admin zone, signed in as the admin.
- **api:** a scan of the api from its openapi docs, with the admin's token (the admin is a candidate too,
  so every endpoint answers it), which sends attacks: injection, path traversal, malformed input. Signed
  in, they reach the api's code and change its data, so it only ever runs on a throwaway stack: CI's, or
  the local e2e one, never a shared one.

- signed in before the scans: playwright signs in on keycloak's own page for each session cookie
  (`cv-ui/e2e/zap-auth.mts`), and the password grant gives the api's token. The scan outlasts the realm's
  5 minute tokens and ZAP cannot refresh one, so on the running stack only, the `cv-api` client's
  tokens are given an hour through keycloak's admin api. The realm file is not changed.
- a scan that was not signed in after all would pass clean, having met only keycloak's page or 401s, so
  `zap.sh` also fails when a ui scan met keycloak's sign in form, or no api call succeeded.
- any warning not accepted fails the run. Accepted ones are in `ci/zap/ui.tsv` and `ci/zap/api.tsv`,
  each with its reason. `ci/zap/api.context` names what the api is not built on (it is Kotlin on Spring
  and Tomcat, on Linux, with PostgreSQL), so attacks for those skip themselves.
- signing out is out of the ui scans' scope, as it would end the session they scan with.

## the first scan, 2026-11-24
All three were signed in, and nothing was at ZAP's fail level. Five warnings:

| rule | what | decision |
|---|---|---|
| 90004 | no Cross-Origin-Embedder-Policy | fixed: `require-corp` in every ui response (`@cv/auth`'s headers). Every page loads only from its own origin, so it costs nothing, and with the opener policy the pages are cross origin isolated |
| 10049 | signed in pages not cacheable; static files cacheable | accepted: both on purpose |
| 10031 | user input in html attributes | accepted: next's server action fields, and forms showing back what was entered, all escaped by react |
| 10202 | forms without anti-csrf tokens | accepted: next refuses a server action from another origin, the logout route refuses the same (tested), and the session cookie is SameSite=Lax |
| 100001 | api answers that are not json | accepted: tomcat's own plain 400 page for encoded attack paths such as `%00`, before spring, with no server version; spring's answers are json |

## when
The pipeline must stay quick (see workflow.md, keeping it quick), so:
- **every push:** the two passive ui scans, in the smoke job after playwright, on the same stack. They
  only read, so they cannot disturb it, and they add a few minutes.
- **nightly, or by hand:** all three, the api's attacks included, in `nightly.yml` through `_zap.yml`,
  on a stack of its own, as the attacks change data. A regression the attacks find is caught the next
  morning, not on its own push.

## running it locally
On the e2e stack (see decisions/ui.md): `ci/zap.sh`. ZAP runs in docker on the host's network, which
docker desktop allows. Reports go to `ci/zap-report/<scan>`.

## costs
- a few minutes per push for the ui scans, and about 15 minutes each night, with a stack of its own.
- a new warning fails a push until it is fixed or accepted with its reason.
- passive scans see only what a spider finds by following links; the api scan sees only what its
  openapi docs describe.
