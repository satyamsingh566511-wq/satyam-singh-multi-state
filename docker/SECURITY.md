# Runtime image hardening — `multistate-api`

Covers the non-root user, the HEALTHCHECK approach, the no-secrets policy and the
OCI labels for the distroless runtime image (`uptimecrew/multistate-api`).

## 1. Non-root (USER 65532)

The runtime stage declares `USER 65532` **before** the `ENTRYPOINT`, so the JVM
runs as the unprivileged `nonroot` user the distroless base pre-creates — never
root.

```
$ docker inspect --format '{{.Config.User}}' uptimecrew/multistate-api:0.1.0
65532
$ docker top multistate-api
PID     USER    COMMAND
31850   65532   java org.springframework.boot.loader.launch.JarLauncher ...
```

**Why `docker exec multistate-api id` does not work — and that is the point.**
The distroless runtime has no shell and no coreutils, so there is no `id` binary:

```
$ docker exec multistate-api id
OCI runtime exec failed: exec: "id": executable file not found in $PATH
```

That is the hardening, not a regression: no shell means no `sh`/`bash` for an
attacker to pivot through, and a tiny attack surface. Non-root is instead
verified host-side with `docker top` (process UID `65532`) and
`docker inspect {{.Config.User}}`. If an environment strictly requires
`docker exec … id` to succeed, that implies a shell-bearing base (the staging
Temurin-Jammy variant, see §2) — at the cost of the distroless attack-surface
reduction and the < 250 MB size budget. We keep distroless for production.

## 2. HEALTHCHECK — static Go probe (path 1)

Distroless has no `curl`/`wget`/shell, so the HEALTHCHECK cannot be a shell line.
We chose **path 1**: a tiny, static, dependency-free Go probe
([`docker/healthcheck/main.go`](healthcheck/main.go)) compiled in a dedicated
build stage (`golang:1.23-alpine`) and copied into the image at
`/home/nonroot/healthcheck`. It GETs the health endpoint and exits 0 (2xx/3xx)
or 1 (anything else / request failed).

```dockerfile
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
  CMD ["/home/nonroot/healthcheck"]
```

```
$ docker inspect --format '{{.Config.Healthcheck.Test}}' uptimecrew/multistate-api:0.1.0
[CMD /home/nonroot/healthcheck]
$ docker inspect --format '{{.State.Health.Status}}' multistate-api
healthy        # within ~21 s
```

**Probe target = `/actuator/health` (the public aggregate).** The W3 resource
server secures the `/actuator/health/{liveness,readiness}` sub-paths (they return
`401` unauthenticated), while the aggregate `/actuator/health` is public and
reports both groups:

```
GET /actuator/health            -> 200 {"groups":["liveness","readiness"],"status":"UP"}
GET /actuator/health/readiness  -> 401 (secured)
```

A container HEALTHCHECK must be unauthenticated, so the aggregate is the correct
target. Override with the `HEALTHCHECK_URL` env var if a deployment exposes the
probe sub-paths unauthenticated (e.g. behind a Kubernetes-only management port).

Trade-off vs path 2 (shell `HEALTHCHECK` on a Temurin-Jammy runtime): path 2 is
simpler but requires a shell-bearing base, enlarging the attack surface and image
size. We accept the small extra build complexity of compiling a probe binary to
keep the runtime distroless.

## 3. No secrets baked into the image

The Dockerfile contains no credentials — grep returns zero matches:

```
$ grep -niE 'password|token|secret|api[_-]?key' Dockerfile   # 0 matches
```

All sensitive config (DB password, JWT issuer, Anthropic API key, etc.) is read
from **runtime environment** at `docker run` time via Spring's relaxed binding /
`SPRING_APPLICATION_JSON`, never `ARG`/`ENV`-baked. `.dockerignore` keeps secret
material out of the build context entirely:

```
.env        .env.*      *.pem      *.key      *.p12
secrets/    credentials.json       docker-compose.override.yml
```

Build context transferred is ~9 kB (`.git`, `build/`, `multistate-web/` all
ignored), well under the 2 MB bar — confirming no stray secret files or large
artifacts enter the image.

## 4. OCI image labels

The runtime stage sets the standard OCI labels; `APP_VERSION` and `GIT_SHA` build
args populate `version` and `revision`:

```
org.opencontainers.image.title=multistate-api
org.opencontainers.image.version=0.1.0          # <- ARG APP_VERSION
org.opencontainers.image.revision=64029c0       # <- ARG GIT_SHA
org.opencontainers.image.source=https://github.com/uptimecrew/multistate-api
org.opencontainers.image.licenses=Apache-2.0
```

## 5. Base images & pinned digests

| Stage     | Base image                                    | Why                                      |
|-----------|-----------------------------------------------|------------------------------------------|
| builder   | `eclipse-temurin:21-jdk-jammy`                | Full JDK + Gradle; discarded after build |
| extractor | `eclipse-temurin:21-jre-jammy`                | JRE only; runs `layertools extract`      |
| healthcheck | `golang:1.26-alpine`                        | Compiles the static probe; discarded     |
| runtime   | `gcr.io/distroless/java21-debian12:nonroot`   | No shell/apt; pre-created UID 65532      |

All three **runtime-lineage** base images are pinned by digest (`@sha256:…`), not
by tag. A tag is mutable — the same `:21-jre-jammy` reference can change bytes
across days; a digest is content-addressed and immutable.

```text
eclipse-temurin:21-jdk-jammy               @sha256:801b7e1a9c4befaf82bf9a2a58025ef43a7694bbc84779187ad0524d84742772
eclipse-temurin:21-jre-jammy               @sha256:199aebeb3adcde4910695cdebfe782ada38dadb6cc8013159b58d3724451befd
gcr.io/distroless/java21-debian12:nonroot  @sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d
```

Resolve a digest with `docker pull <ref> && docker images --digests <repo>`.
Refresh on the **first business day of each month**, or immediately if Trivy
reports a newly-discovered HIGH/CRITICAL on a current digest. Bump the
`image.version` label on every refresh.

## 6. Scan findings & waivers

`trivy image --severity HIGH,CRITICAL --ignore-unfixed` on the first build
reported 42 HIGH/CRITICAL. All but one were remediated:

| Source | Finding | Fix |
|--------|---------|-----|
| Go probe (stdlib) | 13 HIGH + 1 CRITICAL | Rebuilt probe on `golang:1.26` (go1.26.4) |
| tomcat-embed-core | 3 CRITICAL + 2 HIGH | `ext['tomcat.version'] = '11.0.22'` |
| netty-* | ~13 HIGH | `ext['netty.version'] = '4.2.15.Final'` |
| jackson-databind (3.x) | 2 HIGH | `ext['jackson-bom.version'] = '3.1.4'` |
| jackson-databind (2.x) | 2 HIGH | `ext['jackson-2-bom.version'] = '2.21.4'` |
| spring-kafka | 1 HIGH | `ext['spring-kafka.version'] = '4.0.6'` |
| postgresql | 1 HIGH | pinned dep bumped to `42.7.11` |

**Active waiver** (`.trivyignore`, suppresses one finding so the gate is green):

- **CVE-2026-41254 — `liblcms2-2`** (distroless runtime base). Fixed upstream as
  Debian `2.14-2+deb12u1`; the pinned distroless digest predates that rebuild.
  `liblcms2` is pulled by the JRE's `java.desktop` image-I/O codecs, which this
  headless API never invokes — **not reachable**. Clears on the monthly base
  digest refresh. *Added 2026-06-29, review by 2026-07-31.*

Result: `trivy … --ignorefile .trivyignore --exit-code 1` → **exit 0**.

## 7. Operator commands

```bash
# 1. Build (version + git sha populate the OCI labels).
docker build \
  --build-arg APP_VERSION=0.1.0 \
  --build-arg GIT_SHA=$(git rev-parse --short HEAD) \
  -t uptimecrew/multistate-api:0.1.0 .

# 2. Scan (fail-by-default on fixable HIGH/CRITICAL; honour the waiver file).
trivy image --severity HIGH,CRITICAL --ignore-unfixed \
  --ignorefile .trivyignore uptimecrew/multistate-api:0.1.0

# 3. Run (non-root inside, host port 8080, resource caps). Datastores must be
#    reachable at the app's configured addresses — see the operational note.
docker run -d --name multistate-api \
  --memory=512m --cpus=1.0 -p 8080:8080 \
  uptimecrew/multistate-api:0.1.0
```

## 8. Scan cadence

- **Every PR** touching the image: `.github/workflows/docker.yml` runs hadolint,
  then build → Trivy (`HIGH,CRITICAL --ignore-unfixed --exit-code 1`, honouring
  `.trivyignore`) → 60-s actuator smoke test. A new fixable finding fails the PR.
- **On merge to main**: same scan plus push to the private registry with
  `scanOnPush=true` (registry-side second opinion). *Push path documented, not
  implemented for D1.*
- **Weekly cron** (future `docker-rescan.yml`): re-scan the deployed digest —
  new CVEs land on unchanged bytes daily, so Monday-clean can be Friday-dirty.

## 9. Tagging policy

ECR repo is `tagMutability=IMMUTABLE`. Tags written per build:

- `uptimecrew/multistate-api:0.1.0` — human-friendly semver, immutable.
- `uptimecrew/multistate-api:<git-sha>` — exact source pin, immutable.
- Never `:latest`. Production manifests (Compose, Kubernetes, ECS) reference the
  digest `uptimecrew/multistate-api@sha256:…`, never a mutable tag.

## Operational note — Mongo connection

This app hardwires its Mongo connection to `localhost:27017`: the
`spring.data.mongodb.uri` value is **not** overridable via env,
`SPRING_APPLICATION_JSON`, or command-line args (the datasource URL overrides
fine — only the Mongo URI resists), so a `MongoConnectionDetails`-level source is
fixing it. This is an application-config matter, independent of the image. To run
the container against the `docker-compose.dev.yml` stack for the health check, it
shares Mongo's network namespace so the app's `localhost:27017` resolves to the
Mongo container, while Postgres/Redis/Kafka resolve over the shared compose
network:

```
docker run -d --name multistate-api --memory=512m --cpus=1.0 \
  --network container:multistate-mongo -e OTEL_SDK_DISABLED=true \
  uptimecrew/multistate-api:0.1.0 \
  --spring.datasource.url=jdbc:postgresql://postgres:5432/postgres \
  --spring.data.redis.host=redis --spring.flyway.enabled=false \
  --spring.kafka.bootstrap-servers=kafka:9092
```

In a real deployment Mongo is provided at the app's expected address (sidecar or
fixed `localhost`), and the HEALTHCHECK behaves identically.
