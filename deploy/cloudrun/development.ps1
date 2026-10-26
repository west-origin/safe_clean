param(
    [Parameter(Mandatory)][ValidateSet('Start', 'Stop', 'Status')][string]$Action,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
# This controller deliberately has no service/project override: it cannot stop production.
$projectId = 'safeclean-511807'
$region = 'asia-northeast3'
$service = 'ansimclean-api-test'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$bundledCli = Join-Path $repo '.env.cloudrun-build/sdk/google-cloud-sdk/bin/gcloud.cmd'
$gcloud = if (Get-Command gcloud -ErrorAction SilentlyContinue) { 'gcloud' } elseif (Test-Path -LiteralPath $bundledCli) { $bundledCli } else { throw 'Google Cloud CLI가 필요합니다.' }
if ($gcloud -eq $bundledCli) { $env:CLOUDSDK_CONFIG = Join-Path $repo '.env.cloudrun-build/gcloud-config' }
$targetArgs = @("--project=$projectId", "--region=$region")
function Invoke-DevelopmentCloud([string[]]$Arguments) {
    $result = & $gcloud @Arguments
    if ($LASTEXITCODE -ne 0) { throw '개발 Cloud Run 작업 실패. 운영 서비스는 변경하지 않았습니다.' }
    return $result
}
if ($DryRun) {
    $scaling = if ($Action -eq 'Start') { 1 } else { 0 }
    if ($Action -eq 'Status') { @('run', 'services', 'describe', $service) + $targetArgs | ConvertTo-Json }
    else { @('run', 'services', 'update', $service, "--scaling=$scaling") + $targetArgs | ConvertTo-Json }
    return
}
if ($Action -eq 'Stop') {
    # Traffic tag URLs can start a revision separately; remove tags when switching off.
    $null = Invoke-DevelopmentCloud (@('run', 'services', 'update-traffic', $service, '--clear-tags', '--quiet') + $targetArgs)
    $null = Invoke-DevelopmentCloud (@('run', 'services', 'update', $service, '--scaling=0', '--quiet') + $targetArgs)
}
if ($Action -eq 'Start') {
    try {
        $null = Invoke-DevelopmentCloud (@('run', 'services', 'update', $service, '--scaling=1', '--quiet') + $targetArgs)
        $url = (Invoke-DevelopmentCloud (@('run', 'services', 'describe', $service, '--format=value(status.url)') + $targetArgs)).Trim()
        if ($url -notmatch '^https://[a-z0-9.-]+\.run\.app$') { throw '개발 API 주소 확인 실패' }
        $healthy = $false
        for ($attempt = 0; $attempt -lt 6; $attempt++) {
            try { $healthy = (Invoke-RestMethod -Uri "$url/actuator/health" -TimeoutSec 15).status -eq 'UP' } catch { $healthy = $false }
            if ($healthy) { break }
            Write-Output '개발 서버 시작 확인 중...'
            Start-Sleep -Seconds 5
        }
        if (-not $healthy) { throw '개발 서버 health 확인 실패' }
        Write-Output "개발 API: $url/api/v1"
        Write-Output '테스트를 마치면 development.ps1 Stop을 실행하세요. 실행 중에는 사용량이 과금될 수 있습니다.'
    } catch {
        $startError = $_
        $null = Invoke-DevelopmentCloud (@('run', 'services', 'update', $service, '--scaling=0', '--quiet') + $targetArgs)
        throw $startError
    }
}
$state = (Invoke-DevelopmentCloud (@('run', 'services', 'describe', $service, '--format=json') + $targetArgs)) | ConvertFrom-Json
$mode = $state.metadata.annotations.'run.googleapis.com/scalingMode'
$count = $state.metadata.annotations.'run.googleapis.com/manualInstanceCount'
if ($Action -eq 'Stop' -and ($mode -ne 'manual' -or "$count" -ne '0')) { throw '개발 서버 OFF 상태를 확인하지 못했습니다.' }
if ($Action -eq 'Start' -and ($mode -ne 'manual' -or "$count" -ne '1')) { throw '개발 서버 ON 상태를 확인하지 못했습니다.' }
[pscustomobject]@{ Service = $service; Scaling = $mode; Instances = $count; Url = $state.status.url }
