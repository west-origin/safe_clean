param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9-]{4,28}[a-z0-9]$')][string]$ProjectId,
    [Parameter(Mandatory)][ValidatePattern('^[1-9][0-9]*$')][string]$SecretVersion,
    [ValidatePattern('^[a-z][a-z0-9-]{0,47}[a-z0-9]$')][string]$Service = 'ansimclean-api',
    [ValidatePattern('^[a-z][a-z0-9-]+$')][string]$SecretName = 'ansimclean-api-env',
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
if ($Service -eq 'ansimclean-api-test') { throw '개발 서버는 기본 OFF를 유지하는 deploy-development.ps1로 배포하세요.' }
$taskRepo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$region = 'asia-northeast3'
$runtimeAccount = "ansimclean-runtime@$ProjectId.iam.gserviceaccount.com"
$buildAccount = "projects/$ProjectId/serviceAccounts/ansimclean-builder@$ProjectId.iam.gserviceaccount.com"
$deployArgs = @(
    'run', 'deploy', $Service,
    "--project=$ProjectId", "--region=$region",
    "--source=$(Join-Path $taskRepo 'backend')",
    "--build-service-account=$buildAccount",
    "--service-account=$runtimeAccount",
    '--execution-environment=gen2', '--port=8080',
    '--cpu=1', '--memory=1Gi', '--concurrency=40', '--timeout=60',
    '--min=1', '--max=1', '--min-instances=0', '--max-instances=1',
    '--no-cpu-throttling', '--cpu-boost', '--ingress=all', '--allow-unauthenticated',
    "--env-vars-file=$(Join-Path $PSScriptRoot 'runtime.yaml')",
    "--set-secrets=/secrets/backend.env=${SecretName}:$SecretVersion",
    '--startup-probe=httpGet.path=/actuator/health,periodSeconds=10,timeoutSeconds=5,failureThreshold=24',
    '--liveness-probe=httpGet.path=/actuator/health/liveness,periodSeconds=30,timeoutSeconds=5,failureThreshold=3'
)
if ($DryRun) {
    $deployArgs | ConvertTo-Json
    return
}
if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) { throw 'Google Cloud CLI가 필요합니다. deploy/cloudrun/README.md를 확인하세요.' }
& gcloud @deployArgs
if ($LASTEXITCODE -ne 0) { throw 'Cloud Run 배포 실패. 트래픽 변경과 성공 처리를 중단합니다.' }
& gcloud run services update-traffic $Service "--project=$ProjectId" "--region=$region" '--to-latest'
if ($LASTEXITCODE -ne 0) { throw '새 리비전 트래픽 전환 실패' }
$serviceUrl = & gcloud run services describe $Service "--project=$ProjectId" "--region=$region" '--format=value(status.url)'
if ($LASTEXITCODE -ne 0 -or -not $serviceUrl) { throw '서비스 주소 확인 실패' }
$serviceUrl = $serviceUrl.Trim()
if ($serviceUrl -notmatch '^https://[a-z0-9.-]+\.run\.app$') { throw '예상하지 못한 서비스 주소' }
$health = Invoke-RestMethod -Uri "$serviceUrl/actuator/health" -TimeoutSec 30
if ($health.status -ne 'UP') { throw '배포 후 health가 UP이 아닙니다.' }
Write-Output "API: $serviceUrl/api/v1"
Write-Output "LiveKit webhook: $serviceUrl/api/v1/live/webhooks/livekit"
Write-Output '실기기 가입·예약·푸시·영상과 IP 속도 제한 검증은 README의 완료 기준을 따르세요.'
