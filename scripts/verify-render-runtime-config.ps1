$ErrorActionPreference = 'Stop'

$application = Get-Content -Raw (Join-Path $PSScriptRoot '..\src\main\resources\application.yml')
$render = Get-Content -Raw (Join-Path $PSScriptRoot '..\render.yaml')

if ($application -notmatch 'port:\s*\$\{PORT:8080\}') {
    throw 'application.yml must use server.port=${PORT:8080} for Render compatibility.'
}

if ($render -notmatch 'healthCheckPath:\s*/api/health') {
    throw 'render.yaml must define healthCheckPath: /api/health.'
}

Write-Output 'Render runtime config check passed.'
