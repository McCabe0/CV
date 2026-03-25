# Skill2Career

AI-powered app to:
- collect a user's skills and experience
- generate a CV
- search for relevant jobs
- match jobs against the user's skills

## Structure

```text
frontend/   React + TypeScript + Vite
backend/    Kotlin + Spring Boot
```

## Frontend

```bash
cd frontend
npm install
npm run dev
```

## Backend

```bash
cd backend
gradle bootRun
```


## Run both services

You can run backend + frontend together from the repo root:

```bash
./run.sh
```

This starts:
- backend with `gradle bootRun`
- frontend with `npm run dev`

Press `Ctrl+C` to stop both processes.

## Test URLs

- `http://localhost:8080/health`
- `http://localhost:8080/hello`
