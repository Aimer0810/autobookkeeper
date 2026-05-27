$ErrorActionPreference = 'Stop'

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$jarPath = Join-Path $projectRoot 'target\autobookkeeper-0.1.0.jar'

if ([string]::IsNullOrWhiteSpace($env:AUTOBOOKKEEPER_API_TOKEN)) {
    throw 'AUTOBOOKKEEPER_API_TOKEN is required. Example: $env:AUTOBOOKKEEPER_API_TOKEN="your-local-token"'
}

if ([string]::IsNullOrWhiteSpace($env:VISION_API_KEY)) {
    throw 'VISION_API_KEY is required. Example: $env:VISION_API_KEY="your-dashscope-api-key"'
}

if (-not (Test-Path $jarPath)) {
    throw "Jar not found: $jarPath. Run: mvn package `"-DskipTests`""
}

$endpoint = if ([string]::IsNullOrWhiteSpace($env:VISION_API_ENDPOINT)) { 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions' } else { $env:VISION_API_ENDPOINT }
$model = if ([string]::IsNullOrWhiteSpace($env:VISION_MODEL)) { 'qwen3-vl-flash' } else { $env:VISION_MODEL }
$timeoutMs = if ([string]::IsNullOrWhiteSpace($env:AUTOBOOKKEEPER_AI_TIMEOUT_MS)) { '30000' } else { $env:AUTOBOOKKEEPER_AI_TIMEOUT_MS }

Write-Output 'Starting AutoBookkeeper locally...'
Write-Output "Profile: local"
Write-Output "Endpoint: $endpoint"
Write-Output "Model: $model"
Write-Output "TimeoutMs: $timeoutMs"
Write-Output 'API token: configured'
Write-Output 'Vision API key: configured'

& java -jar $jarPath `
    --spring.profiles.active=local `
    "--autobookkeeper.api-token=$env:AUTOBOOKKEEPER_API_TOKEN" `
    '--autobookkeeper.ai.provider=cloud' `
    "--autobookkeeper.ai.endpoint=$endpoint" `
    "--autobookkeeper.ai.model=$model" `
    "--autobookkeeper.ai.timeout-ms=$timeoutMs" `
    "--autobookkeeper.ai.api-key=$env:VISION_API_KEY"
