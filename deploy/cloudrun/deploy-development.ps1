param(
    [switch]$DryRun,
    [ValidatePattern('^[1-9][0-9]*$')][string]$SecretVersion
)
$ErrorActionPreference = 'Stop'
$projectId = 'safeclean-511807'
$region = 'asia-northeast3'
$service = 'ansimclean-api-test'
$secretName = 'ansimclean-test-env'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtimeAccount = "ansimclean-test-runtime@$projectId.iam.gserviceaccount.com"
$envFile = Join-Path $PSScriptRoot 'environments/development/.env'
$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim() }
}
if ($settings.SUPABASE_URL -ne 'https://xedkwtdkpxejlqpbdstb.supabase.co' -or $settings.DB_USER -ne 'postgres.xedkwtdkpxejlqpbdstb') { throw '새 서울 개발 DB만 배포할 수 있습니다.' }
if ($settings.LIVEKIT_MODE -ne 'cloud' -or $settings.LIVEKIT_URL -ne 'wss://safe-clean-test-443ul74r.livekit.cloud') { throw '개발 전용 LiveKit 프로젝트 safe_clean_test만 배포할 수 있습니다.' }
foreach ($key in @('DB_PASSWORD', 'SUPABASE_SERVICE_ROLE_KEY', 'SUPABASE_STORAGE_S3_ACCESS_KEY', 'SUPABASE_STORAGE_S3_SECRET_KEY', 'OTP_SALT', 'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET')) {
    if ([string]::IsNullOrWhiteSpace($settings[$key])) { throw "개발 설정 누락: $key" }
}
if ($settings.SUPABASE_STORAGE_S3_ENDPOINT -notin @('https://xedkwtdkpxejlqpbdstb.storage.supabase.co/storage/v1/s3', 'https://xedkwtdkpxejlqpbdstb.supabase.co/storage/v1/s3')) { throw '개발 Storage endpoint 불일치' }
if ($settings.TOSS_CLIENT_KEY -notlike 'test_ck_*' -or $settings.TOSS_SECRET_KEY -notlike 'test_sk_*') { throw '개발 서버에는 토스 테스트 키만 사용합니다.' }
$bundledCli = Join-Path $repo '.env.cloudrun-build/sdk/google-cloud-sdk/bin/gcloud.cmd'
$gcloud = if (Get-Command gcloud -ErrorAction SilentlyContinue) { 'gcloud' } elseif (Test-Path -LiteralPath $bundledCli) { $bundledCli } else { throw 'Google Cloud CLI가 필요합니다.' }
if ($gcloud -eq $bundledCli) { $env:CLOUDSDK_CONFIG = Join-Path $repo '.env.cloudrun-build/gcloud-config' }
function Invoke-DevelopmentDeploy([string[]]$Arguments) {
    $result = & $gcloud @Arguments
    if ($LASTEXITCODE -ne 0) { throw '개발 서버 배포 작업 실패. 운영 서비스는 변경하지 않았습니다.' }
    return $result
}
$argsForDeploy = @(
    'run', 'deploy', $service, "--project=$projectId", "--region=$region",
    "--source=$(Join-Path $repo 'backend')",
    "--build-service-account=projects/$projectId/serviceAccounts/ansimclean-builder@$projectId.iam.gserviceaccount.com",
    "--service-account=$runtimeAccount", '--execution-environment=gen2', '--port=8080',
    '--cpu=1', '--memory=1Gi', '--concurrency=40', '--timeout=60',
    '--scaling=0',
    '--no-cpu-throttling', '--cpu-boost', '--ingress=all', '--allow-unauthenticated',
    '--labels=environment=development', "--env-vars-file=$(Join-Path $PSScriptRoot 'runtime.yaml')",
    '--startup-probe=httpGet.path=/actuator/health,periodSeconds=10,timeoutSeconds=5,failureThreshold=24',
    '--liveness-probe=httpGet.path=/actuator/health/liveness,periodSeconds=30,timeoutSeconds=5,failureThreshold=3', '--quiet'
)
if ($DryRun) {
    [pscustomobject]@{ Service=$service; Database='xedkwtdkpxejlqpbdstb'; RuntimeAccount=$runtimeAccount; Secret=$secretName; Default='manual/0'; DeployArgs=$argsForDeploy } | ConvertTo-Json -Depth 4
    return
}
$accounts = Invoke-DevelopmentDeploy @('iam', 'service-accounts', 'list', "--project=$projectId", "--filter=email:$runtimeAccount", '--format=value(email)')
if ($runtimeAccount -notin @($accounts)) {
    $null = Invoke-DevelopmentDeploy @('iam', 'service-accounts', 'create', 'ansimclean-test-runtime', "--project=$projectId", '--display-name=Ansimclean development runtime')
}
$secrets = Invoke-DevelopmentDeploy @('secrets', 'list', "--project=$projectId", "--filter=name:$secretName", '--format=value(name)')
if (-not $secrets) {
    $null = Invoke-DevelopmentDeploy @('secrets', 'create', $secretName, "--project=$projectId", '--replication-policy=automatic')
}
$version = $SecretVersion
if (-not $version) {
    $versionName = Invoke-DevelopmentDeploy @('secrets', 'versions', 'add', $secretName, "--project=$projectId", "--data-file=$envFile", '--format=value(name)')
    $version = ($versionName | Select-Object -Last 1).Trim().Split('/')[-1]
}
if ($version -notmatch '^[1-9][0-9]*$') { throw '숫자 시크릿 버전 확인 실패' }
# 명시적으로 과거 버전을 고르더라도 주소 검색 키가 없는 설정으로 퇴행시키지 않는다.
# 값은 메모리에서 검사만 하고 로그/파일에 출력하지 않는다.
$runtimeSettings = @{}
$secretLines = Invoke-DevelopmentDeploy @('secrets', 'versions', 'access', $version, "--secret=$secretName", "--project=$projectId")
foreach ($line in $secretLines) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $runtimeSettings[$matches[1]] = $matches[2].Trim() }
}
$secretLines = $null
if ($runtimeSettings.SUPABASE_URL -ne 'https://xedkwtdkpxejlqpbdstb.supabase.co' -or $runtimeSettings.DB_USER -ne 'postgres.xedkwtdkpxejlqpbdstb') {
    throw '개발 DB가 아닌 시크릿 버전은 배포할 수 없습니다.'
}
if ([string]::IsNullOrWhiteSpace($runtimeSettings.KAKAO_REST_API_KEY)) {
    throw '카카오 주소 검색 키가 없는 시크릿 버전은 배포할 수 없습니다. 현재 정상 개발 설정 버전을 사용하세요.'
}
$runtimeSettings = $null
$null = Invoke-DevelopmentDeploy @('secrets', 'add-iam-policy-binding', $secretName, "--project=$projectId", "--member=serviceAccount:$runtimeAccount", '--role=roles/secretmanager.secretAccessor', '--format=none')
$argsForDeploy += "--set-secrets=/secrets/backend.env=${secretName}:$version"
try {
    $null = Invoke-DevelopmentDeploy $argsForDeploy
    $null = Invoke-DevelopmentDeploy @('run', 'services', 'update-traffic', $service, "--project=$projectId", "--region=$region", '--to-latest', '--clear-tags', '--quiet')
} finally {
    # A deployment must never leave the on-demand development service running.
    $null = & $gcloud run services describe $service "--project=$projectId" "--region=$region" '--format=value(metadata.name)' 2>$null
    if ($LASTEXITCODE -eq 0) {
        $null = Invoke-DevelopmentDeploy @('run', 'services', 'update', $service, "--project=$projectId", "--region=$region", '--scaling=0', '--quiet')
    }
}
& (Join-Path $PSScriptRoot 'development.ps1') Status
