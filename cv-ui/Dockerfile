# Builds one app. docker build --build-arg APP=shell -t cv-shell .
FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS builder
ARG APP
RUN test -n "$APP" || (echo "APP build arg is required: shell, profile, cv, jobs or admin" && false)
WORKDIR /app
RUN corepack enable

# The package files first, so a change to the code does not install the dependencies again.
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/${APP}/package.json apps/${APP}/
COPY packages/ui/package.json packages/ui/
COPY packages/auth/package.json packages/auth/
COPY packages/api-client/package.json packages/api-client/
RUN pnpm install --frozen-lockfile --filter "@cv/${APP}..."

COPY tsconfig.base.json ./
COPY packages/ui packages/ui
COPY packages/auth packages/auth
COPY packages/api-client packages/api-client
COPY apps/${APP} apps/${APP}
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter "@cv/${APP}" build

FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5
ARG APP
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0

# Only node runs here, so the image's package managers, and their vulnerabilities, are left out.
RUN rm -rf /usr/local/lib/node_modules /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack

# Fixed in alpine, not yet in the base image: CVE-2026-4738 (zlib, critical) and CVE-2026-95691, -95692,
# -88823 and -107496 (openssl, high). Goes once dependabot moves the base image to one that has them.
RUN apk upgrade --no-cache zlib libssl3 libcrypto3

RUN addgroup --gid 2000 --system appgroup && \
    adduser --uid 2000 --system appuser -G appgroup

WORKDIR /app
# The build script copies the static files into the standalone output.
COPY --from=builder --chown=appuser:appgroup /app/apps/${APP}/.next/standalone ./
RUN ln -s apps/${APP}/server.js server.js

USER appuser
EXPOSE 3000

# The commit, on the image. Last, so a new commit rebuilds nothing above.
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision=$GIT_SHA
ENV SERVICE_VERSION=$GIT_SHA
CMD ["node", "server.js"]
