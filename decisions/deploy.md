## context
Nothing deploys. The pipeline ends with images pushed and checked. The only description of the running
system is `docker-compose.yml`, which is for people and ci, not production, so nothing says how the
service would run in a real environment, and nothing tests that it could.

Following the digital-resolutions-poc's decision, adapted (decided 2026-11-25). Nothing here goes to a
cloud: the charts are the deliverable, and ci proves them on a local cluster.

## decision
Helm charts for the service, written for EKS, and tested in ci on a kind cluster. Nothing is deployed, so
there is no terraform: there is no cloud to make.

- helm, not a cloud's own task definitions. Charts run on EKS, AKS or a local cluster unchanged.
- EKS, when there is somewhere to deploy. What the cloud would give the charts, managed postgres (RDS),
  managed kafka (MSK), a secret store (Secrets Manager, through External Secrets), a load balancer and
  its firewall, would come from terraform then, and helm would not make any of it.
- an umbrella chart, `charts/skill2career`, of one chart per kind of workload, with a library chart,
  `charts/common`, for what they all share: labels, a non-root user, a read-only root filesystem, no
  added capabilities, resource requests and limits, and probes.
- one image, three roles, as in compose: the `api` chart is installed three times, as `api`, `cv-worker`
  and `job-worker`, differing only by `APP_ROLES`, so each scales on its own. Only `api` has a route.
  `job-worker` is one replica, as a database row keeps the day's crawl to one worker anyway.
- the shell and the four zones share the `ui` chart, installed five times, as their images differ only by
  the app. Only the shell has a route: it routes each zone's base path to the zone, as in compose.
- in the charts: deployments, services, routes, autoscalers, disruption budgets, network policies, and
  config. Secrets are named, never in values: an environment's External Secrets fill them from its store.
- not in the charts: postgres, kafka and keycloak, which would be managed or the organisation's, and the
  observability backends, which are the platform's. The OpenTelemetry collector's own chart is a
  dependency, and values say where it sends.
- the gateway api (`HTTPRoute`), not an ingress. ingress-nginx was retired in March 2026, and EKS's load
  balancer controller takes gateway api routes. On kind, envoy gateway. One gateway routes by path:
  `/api` to the api and `/` to the shell. On kind, keycloak has a listener of its own, as it has its own
  port in compose and its own host in production, so the test reaches everything on compose's ports and
  the test realm needs no change.
- images by digest in production, so what runs is what the pipeline tested.
- containers run as user 10001, above any user on a node. The images' own users are 2000; what the
  charts set wins, and the images' files are readable by all.
- network policies between the service's own pods are the umbrella chart's: the shell and zones reach
  the api, the shell reaches the zones, the api's roles reach the collector, and nothing else. An
  environment adds its own for the gateway, postgres, kafka, keycloak, the LLM and the job boards, as
  those differ between kind and a cloud.
- compose stays, for local development, smoke, ZAP and the load test.

### the deploy test
`deploy-test/run.sh`, the same locally and in ci (`_deploy-test.yml`):

1. `helm lint`, and a policy scan of the rendered charts (checkov, pinned by digest), which fails on a
   container that could run as root, write its filesystem or has no limits. Accepted checks are in
   `deploy-test/checkov.yaml`, each with its reason.
2. a kind cluster. Its own network plugin, kindnet, enforces network policies since kind 0.24, so there is
   no calico, as the poc has: calico's node cannot start on docker desktop's wsl2 kernel, which has no
   securityfs, and step 5 shows the policies are enforced.
3. `deploy-test/platform`, a chart for the test only, of what a cloud and the organisation would give
   the service: postgres, kafka, keycloak with the test realm, the gateway, plain secrets in place of the
   store's, and wiremock playing gemini and the job boards with the e2e stack's mappings. Then the
   umbrella chart, with the commit's images and `deploy-test/values-kind.yaml`. The collector logs what it
   gets and sends nothing on.
4. every pod ready.
5. the network policies: a pod labelled as one of the api's roles reaches postgres and kafka, and the
   same pod with another label does not. A control first checks the probe's own network works.
6. every playwright journey, through the gateway, as the smoke job runs them on compose.

When: nightly, in `nightly.yml`, on the newest full set of images, and by hand; and in `pipeline.yml`
on a push that changes `charts/`, `deploy-test/` or the workflow. Not on every push, as it is slow and
rarely fails while the charts stay the same (workflow.md, keeping it quick).

### not in scope
Deploying: terraform, an AWS account, environments, `helm upgrade` after the pipeline, smoke against a
real environment, and rollback. Nor the firewall, which only a cloud has. Each can follow the deploy test
unchanged if there is ever somewhere to deploy.

## running it locally
`docker compose build`, then `./deploy-test/local.sh`, with docker and about 6GB free for it. kind, helm
and kubectl need not be installed: it runs `run.sh` in playwright's linux image, on the docker vm's
network so it reaches kind's cluster, with the docker socket, installing what ci installs. It takes the
working tree, uncommitted changes and all. `KEEP=1` leaves the cluster running.

## trial
Run locally on 2026-11-25 with `local.sh`, on docker desktop (wsl2, 8GB), before the workflow first ran.

- `helm lint` passed, and checkov passed all 795 checks of the rendered charts, none skipped beyond the
  two in `checkov.yaml`.
- the cluster, the gateway, the platform and every pod of the service came up and ready in about 4.5
  minutes, and the gateway served the shell and the api on compose's ports.
- the probe reached keycloak, and a pod labelled as a role reached postgres and kafka.
- every playwright journey on the desktop project, 17, passed through the gateway. One, a candidate
  saving their profile, failed once at 21.5s and passed on its retry. The reflow project was part way,
  every journey so far passing, when the machine ran short of memory and the run was stopped.

What the trial changed:

- docker desktop's wsl2 vm ran cgroup v1, which kubernetes 1.37 will not start on. `.wslconfig` with
  `kernelCommandLine = cgroup_no_v1=all`, then `wsl --shutdown`, made it v2. Ci's runners are v2.
- calico's node could not start on wsl2's kernel, which has no securityfs, so the test uses kindnet,
  which enforces network policies since kind 0.24.
- kindnet cannot enforce them on wsl2's kernel either: its nftables rules fail with "operation not
  supported", so a pod with another label still reached postgres and kafka. The policies are made as
  they should be. The check can only pass where the kernel has what it needs, which ci's runners do,
  so it is first proven there. Locally, it fails the test at the end, after the journeys.
- the cluster is deleted when the test fails at any step, not only after the gateway, and one kept with
  `KEEP=1` is used again.

## costs
- one more description of the system beside compose, which can drift. The deploy test catches drift the
  night after, or on the push when the charts change, but a change to the app that the charts needed and
  did not get, such as a new setting, is found only the next night.
- the collector's image is pinned by digest in the umbrella chart's values, which dependabot does not
  update. When compose's collector moves, the chart's moves with it, by hand.
- the charts are proven on kind, not on EKS. What only a cloud has, its load balancer, firewall, managed
  postgres and kafka, and secret store, is not tested.
- about 15 to 20 minutes of runner time each night.
