$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$devOpsDir = (Resolve-Path (Join-Path $scriptDir "..")).Path
$repoRoot = (Resolve-Path (Join-Path $devOpsDir "..\..")).Path
$backendDir = Join-Path $repoRoot "mozhi-backend"
$pidFile = Join-Path $scriptDir "mozhi-app.pid"
$logsDir = Join-Path $repoRoot "logs"
$stdoutLog = Join-Path $logsDir "mozhi-app.out.log"
$stderrLog = Join-Path $logsDir "mozhi-app.err.log"
$fallbackJavaHome = "C:\Users\Huli\.jdks\ms-21.0.10"
$composeFile = Join-Path $devOpsDir "docker-compose-environment.yml"
$jarPath = Join-Path $backendDir "mozhi-app\target\mozhi-app-1.0.0-SNAPSHOT.jar"

function Test-Java21Home {
    param(
        [string]$Candidate
    )

    if (-not $Candidate) {
        return $false
    }

    $candidateJavaExe = Join-Path $Candidate "bin\java.exe"
    if (-not (Test-Path $candidateJavaExe)) {
        return $false
    }

    $versionOutput = cmd.exe /c """$candidateJavaExe"" -version 2>&1"
    return ($versionOutput | Select-String 'version "21\.').Count -gt 0
}

$javaHome = if (Test-Java21Home $env:JAVA_HOME) { $env:JAVA_HOME } elseif (Test-Java21Home $fallbackJavaHome) { $fallbackJavaHome } else { $null }
$javaExe = if ($javaHome) { Join-Path $javaHome "bin\java.exe" } else { $null }

if (-not $javaExe) {
    throw "JAVA_HOME is not configured with a usable Java 21 runtime. Checked: '$($env:JAVA_HOME)' and '$fallbackJavaHome'"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

if (Test-Path $pidFile) {
    $existingPid = (Get-Content $pidFile | Select-Object -First 1).Trim()
    if ($existingPid -and (Get-Process -Id $existingPid -ErrorAction SilentlyContinue)) {
        Write-Host "mozhi-app is already running with PID $existingPid"
        exit 0
    }
    Remove-Item $pidFile -Force
}

docker compose -f $composeFile up -d

if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose environment startup failed."
}

$mysqlHealthy = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $health = docker inspect --format "{{.State.Health.Status}}" mozhi-mysql 2>$null
    if ($LASTEXITCODE -eq 0 -and $health.Trim() -eq "healthy") {
        $mysqlHealthy = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $mysqlHealthy) {
    throw "MySQL did not become healthy in time."
}

Push-Location $backendDir
try {
    .\mvnw.cmd -q -pl mozhi-app -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Backend packaging failed."
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path $jarPath)) {
    throw "Packaged backend jar was not created: $jarPath"
}

$process = Start-Process `
    -FilePath $javaExe `
    -ArgumentList @("-jar", $jarPath, "--spring.profiles.active=dev") `
    -WorkingDirectory $backendDir `
    -PassThru `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog

Set-Content -Path $pidFile -Value $process.Id

Write-Host "mozhi-app started with PID $($process.Id)"
Write-Host "Logs: $stdoutLog"
Write-Host "Health endpoint: http://127.0.0.1:8090/actuator/health"
Write-Host "API health endpoint: http://127.0.0.1:8090/api/health"
