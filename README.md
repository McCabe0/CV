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

## Configuration

The backend calls the Gemini API and reads the key from the `GEMINI_API_KEY`
environment variable (`backend/src/main/resources/application.properties` maps
`gemini.api.key=${GEMINI_API_KEY:}`). **Never commit a real key** — set it in
your environment instead.

```bash
# macOS / Linux
export GEMINI_API_KEY="your-key"
```

```powershell
# Windows PowerShell — current session
$env:GEMINI_API_KEY = "your-key"

# Windows — persist for future shells
setx GEMINI_API_KEY "your-key"
```

If the variable is unset the backend still starts, but Gemini calls fail and
fall back to degraded responses. Get a key from
[Google AI Studio](https://aistudio.google.com/app/apikey).

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
# macOS / Linux / Git Bash / WSL
./run.sh
```

```powershell
# Windows PowerShell
.\run.ps1
```

Both scripts start:
- backend with `gradle bootRun`
- frontend with `npm run dev`

and shut the backend down again when you stop them.

Make sure `GEMINI_API_KEY` is set in your shell first (see
[Configuration](#configuration)).

Press `Ctrl+C` to stop both processes.

## Test URLs

- `http://localhost:8080/health`
