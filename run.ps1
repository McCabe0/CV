#!/usr/bin/env pwsh
# Windows PowerShell launcher for the backend + frontend (equivalent to run.sh).
$ErrorActionPreference = "Stop"

$RootDir = $PSScriptRoot
$BackendDir = Join-Path $RootDir "backend"
$FrontendDir = Join-Path $RootDir "frontend"

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Write-Error "npm is not installed or not in PATH."
    exit 1
}

if ([string]::IsNullOrEmpty($env:GEMINI_API_KEY)) {
    Write-Warning "GEMINI_API_KEY is not set. The backend will start, but Gemini"
    Write-Warning "         calls will fail and fall back to degraded responses."
    Write-Warning '         Set it with: $env:GEMINI_API_KEY = "your-key"'
}

# Prefer the Gradle wrapper that ships with the repo; fall back to a global gradle.
$GradlewBat = Join-Path $BackendDir "gradlew.bat"
if (Test-Path $GradlewBat) {
    $GradleCmd = $GradlewBat
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
    $GradleCmd = "gradle"
} else {
    Write-Error "Neither backend\gradlew.bat nor a global gradle was found in PATH."
    exit 1
}

$Backend = $null
try {
    Write-Host "Starting backend ($GradleCmd bootRun)..."
    $Backend = Start-Process -FilePath $GradleCmd -ArgumentList "bootRun" `
        -WorkingDirectory $BackendDir -PassThru

    Write-Host "Waiting for backend to initialize..."
    Start-Sleep -Seconds 3

    Write-Host "Starting frontend (npm run dev)..."
    Push-Location $FrontendDir
    try {
        # npm is a .cmd shim on Windows; invoke through cmd so it resolves reliably.
        & cmd /c "npm run dev"
    } finally {
        Pop-Location
    }
} finally {
    if ($null -ne $Backend -and -not $Backend.HasExited) {
        Write-Host "Stopping backend (PID $($Backend.Id))..."
        # /T kills the gradle wrapper and the Java child process it spawned.
        taskkill /PID $Backend.Id /T /F 2>$null | Out-Null
    }
}
