# Builds one app. docker build --build-arg APP=shell -t cv-shell .
FROM node:24-alpine@sha256:a34ab6d7f6f4813f4d0c5469d74685604b962fbdbf5793a7bc7b4998ecb867c8 AS builder
ARG APP
RUN test -n "$APP" || (echo "APP build arg is required: shell, profile, cv or jobs" && false)
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

FROM node:24-alpine@sha256:a34ab6d7f6f4813f4d0c5469d74685604b962fbdbf5793a7bc7b4998ecb867c8
ARG APP
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0

# Only node runs here, so the image's package managers, and their vulnerabilities, are left out.
RUN rm -rf /usr/local/lib/node_modules /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack

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
