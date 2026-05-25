$ErrorActionPreference = 'Stop'

$script = Get-Content -Raw (Join-Path $PSScriptRoot '..\docker-entrypoint.sh')
if ($script -notmatch 'postgresql://\*\)') {
    throw 'docker-entrypoint.sh does not handle Render postgresql:// URLs.'
}
if ($script -notmatch 'jdbc:\$DATABASE_URL') {
    throw 'docker-entrypoint.sh does not convert DATABASE_URL to jdbc: format.'
}

Write-Output 'Render DATABASE_URL conversion check passed.'
