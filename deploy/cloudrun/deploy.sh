#!/usr/bin/env bash
# Google Cloud Shell에서 실행한다. Windows에서는 deploy.ps1을 사용한다.
set -euo pipefail
project_id="${1:?Usage: deploy.sh PROJECT_ID SECRET_VERSION}"
secret_version="${2:?Secret Manager의 숫자 버전이 필요합니다}"
[[ "$project_id" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] || { echo 'Invalid project ID' >&2; exit 1; }
[[ "$secret_version" =~ ^[1-9][0-9]*$ ]] || { echo 'Use a numeric secret version' >&2; exit 1; }
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"
region=asia-northeast3
service=ansimclean-api
gcloud run deploy "$service" \
  --project="$project_id" --region="$region" \
  --source="$repo_dir/backend" \
  --build-service-account="projects/$project_id/serviceAccounts/ansimclean-builder@$project_id.iam.gserviceaccount.com" \
  --service-account="ansimclean-runtime@$project_id.iam.gserviceaccount.com" \
  --execution-environment=gen2 --port=8080 \
  --cpu=1 --memory=1Gi --concurrency=40 --timeout=60 \
  --min=1 --max=1 --min-instances=0 --max-instances=1 \
  --no-cpu-throttling --cpu-boost --ingress=all --allow-unauthenticated \
  --env-vars-file="$script_dir/runtime.yaml" \
  --set-secrets="/secrets/backend.env=ansimclean-api-env:$secret_version" \
  --startup-probe='httpGet.path=/actuator/health,periodSeconds=10,timeoutSeconds=5,failureThreshold=24' \
  --liveness-probe='httpGet.path=/actuator/health/liveness,periodSeconds=30,timeoutSeconds=5,failureThreshold=3'
gcloud run services update-traffic "$service" --project="$project_id" --region="$region" --to-latest
service_url="$(gcloud run services describe "$service" --project="$project_id" --region="$region" --format='value(status.url)')"
[[ "$service_url" =~ ^https://[a-z0-9.-]+\.run\.app$ ]] || { echo 'Unexpected service URL' >&2; exit 1; }
curl --fail --silent --show-error --max-time 30 "$service_url/actuator/health" | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'
echo "API: $service_url/api/v1"
echo "LiveKit webhook: $service_url/api/v1/live/webhooks/livekit"
