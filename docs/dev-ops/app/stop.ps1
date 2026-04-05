$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$devOpsDir = (Resolve-Path (Join-Path $scriptDir "..")).Path
$pidFile = Join-Path $scriptDir "mozhi-app.pid"
$composeFile = Join-Path $devOpsDir "docker-compose-environment.yml"

if (Test-Path $pidFile) {
    $backendPid = (Get-Content $pidFile | Select-Object -First 1).Trim()
    if ($backendPid) {
        Stop-Process -Id $backendPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $pidFile -Force
}

docker compose -f $composeFile down
