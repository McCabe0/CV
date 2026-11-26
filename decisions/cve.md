## context
Nothing scans the service for known vulnerabilities: not the images, and not the dependencies. Following
the digital-resolutions-poc's decision, adapted (decided 2026-11-24).

## decision
A grype scan of all six images (`cv-api`, `cv-shell`, `cv-profile`, `cv-cv`, `cv-jobs`, `cv-admin`) on
every push to main, once they are built.

- the built images, not the lock files. One scan covers the os packages, the jars and the node modules,
  and it checks what ships.
- grype, not trivy. Trivy's releases and its github action were compromised in March 2026
  (CVE-2026-33634). A scanner sits next to images and tokens, and grype scans images as well.
- grype's image is pinned by digest. It gets a saved copy of each image (`docker save`), with no docker
  socket and no credentials.
- a run fails on a high or critical vulnerability with a fix, as then there is something to do. Ones
  with no fix fail nothing, but a second scan of each image keeps every match, and the job's summary
  lists the high and critical ones with no fix (`ci/cve/unfixed.mjs`), so they are seen. No readme badge.
- a vulnerability with a fix is fixed, not accepted. Dependabot moves the base images. When alpine has
  released a fixed package that a base image does not have yet, that image's Dockerfile upgrades the
  one package by name, with the CVE beside it, until dependabot moves the base image to one that has it.
- accepted vulnerabilities go in `ci/cve/grype.yaml`, each with its reason and the date to look again.
  Only for ones with no fix, or a fix that does not apply.
- `_cve.yml` is a job of its own, not part of `_image.yml`, so a failed build cannot show as a failed
  scan. It pulls the pushed images by commit.
- `nightly.yml` scans the newest full set of images each morning, as new vulnerabilities appear with
  no push, beside the other nightly checks. It keeps one issue open while any fails, and closes it when
  all pass.
- dependabot raises the fixes, weekly and grouped, with security fixes as they appear: the base images
  of both `cv-api` and `cv-ui`, and the ui's npm packages beside the api's gradle ones. It never moves a
  runtime's major version: java's is the api's gradle toolchain and node's the ui's `.nvmrc`, changed by
  hand with the code.
- actions are pinned to commits and base images to digests, as a tag can be moved, which is how
  trivy's action was attacked.

## running it locally
`ci/cve.sh` scans the images docker compose built: `CVE_PROJECT=cv-e2e ci/cve.sh` for the e2e stack's.
Reports go to `ci/cve-report`.

## found on the way
The images were pushed as `ghcr.io/McCabe0/...`, and image names must be lowercase, so `_image.yml` now
lowercases the owner. The scan pulls them by the same name.

The first scan, 2026-11-24, found high and critical vulnerabilities with a fix in every image. Fixed:
- the ui's base image moved to the newest `node:24-alpine` (node 24.22.3), and the api's are pinned by
  digest, as the ui's were and this decision asks.
- zlib (CVE-2026-4738, critical) in every image, and openssl (four highs) in the ui's: alpine has the
  fixes, the base images not yet, so each Dockerfile upgrades those packages by name, with the CVEs.
- `snappy-java` and `lz4-java`, which kafka's client brings older than their fixes: raised by gradle
  constraints in `adapters/build.gradle.kts`, each with its advisory.

Five openssl highs have no fix yet. They fail nothing, and the job's summary lists them.

2026-11-26: spring boot 4.1.2 brings tomcat 11.0.27, with two critical and six high vulnerabilities fixed
in 11.0.29. Boot's `tomcat.version` is set to 11.0.29 in `cv-api/build.gradle.kts` until boot brings it.
11.0.29 then has its own critical, CVE-2026-133325, fixed in 11.0.30, which is not on maven central yet,
so `ci/cve/grype.yaml` accepts it for 11.0.29 only, to look at again on 2026-12-03.

## costs
- a new vulnerability can fail a push that has nothing to do with it. That is correct, but it will happen.
- base images get no patches unless a dependabot PR is merged, so those PRs need keeping up with.
- dependabot's alerts and security updates are repo settings, which may need turning on.
- runner time per push and per day, and reports kept for 7 days.
