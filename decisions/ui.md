## context
The first ui is one vite page with no tests. It is rebuilt, as the digital-resolutions-poc's ui was.

## decision
- react, on next.js, with the app router.
- next.js multi zones as the micro frontend wrapper. The shell owns the domain, sign in and routing.
  Each zone has its own base path and builds and deploys on its own:

| zone | for |
|---|---|
| `shell` | sign in, routing, the header |
| `profile` | the user's details, and the interviewer's questions |
| `cv` | the writer and critic at work, the finished CV, and its pdf |
| `jobs` | matched jobs, each linking to the company's own page |
| `admin` | tracked companies and crawl health. ADMIN role only |

- a pnpm workspace, with shared packages: `ui` (design), `auth`, `api-client` and `telemetry`.
- keycloak sign in on the server. The ui is a confidential client: tokens stay on the server, in an
  encrypted session cookie, never in the browser.
- the api is not changed to suit the ui. The ui calls the endpoints the api has.

## testing
- TDD: tests first, always.
- jest and react testing library for each page and component: its states, behaviour and accessibility
  (jest-axe).
- a page that loads data on the server is a thin loader around a view. The view gets the component tests.
- playwright for journeys through the shell: sign in, roles, keyboard use, reflow at 320px. Keycloak runs
  as a real container with the api's test realm, the api is wiremock.
- 90% line and branch coverage.
