# Cloud Run 서울 배포

2026-10-19 개발·운영 분리 진행: 개발 API는 `ansimclean-api-test`이며 기본 OFF(수동 0대)다. 테스트할 때 `./deploy/cloudrun/development.ps1 Start`, 종료 후 `./deploy/cloudrun/development.ps1 Stop`으로 제어한다. 재배포는 `deploy-development.ps1`을 사용한다. [분리 상태·사용법·비용](../../backend/docs/environment-separation.md)을 먼저 확인한다. 아래 기존 `ansimclean-api` 결과와 배포 절차는 운영 후보 서비스에 해당한다. 2026-10-21 운영 고객 자료 삭제를 완료했으며 매니저·참조 데이터는 보존했다. 이 삭제 작업에서 개발 서버·DB는 변경하지 않았다.

2026-10-16 사용자 요청으로 Lightsail 구성을 **Google Cloud Run 서울(`asia-northeast3`)**로 교체했다.
Spring API만 배포한다. Supabase는 이전한 서울 프로젝트 `qztjikulkicryvmgfuaw`를 계속 쓰고,
LiveKit Cloud도 유지한다. Cloud Run의 `https://…run.app` 주소와 자동 HTTPS를 사용한다.
고정 IP·SSH 서버·Docker Compose·Caddy는 필요 없다.

## 현재 배포 결과

- API: `https://ansimclean-api-rbtzr3o4cq-du.a.run.app/api/v1`
- 상태: `https://ansimclean-api-rbtzr3o4cq-du.a.run.app/actuator/health` → `UP`
- 리비전: `ansimclean-api-00001-zkw`, 트래픽 100%, Secret Manager 버전 `1`.
- Cloud Build: `187702cf-930a-4851-99bc-15a6ffe3db47`, `SUCCESS`.
- 이미지 digest: `sha256:b9b734e5f83e8dd14607963a683ef3272474340c7d0539595032e25dab646b00`.
- 검증: health·liveness·카탈로그 HTTP 200, 비로그인 `/auth/me`·내부 설정 `/actuator/env` HTTP 401.
- Android `https://localhost`·iOS `capacitor://localhost` CORS 허용, 다른 출처 403, 서명 없는 LiveKit 웹훅 401 확인.
- 서울 DB에 V051 적용, 기존 사용자 7명·예약 41건 보존 및 스케줄러 잠금 기록 확인.
- 고객·매니저 `.env.production`에 API 주소 적용, 두 앱 프로덕션 빌드 성공. 설치된 앱은 새 빌드를 반영해야 한다.
- LiveKit 기존 프로젝트 `p_6df4ofn7fr6`(`safe_clean`)의 웹훅을 Cloud Run 주소로 변경 완료(2026-10-17). 프로젝트 URL·서명 키가 서버와 일치함을 확인했다. LiveKit 대시보드의 `room_started` 테스트 전송을 서버가 HTTP 204로 처리했다.
- 실기기 문자 인증·예약·영상·푸시 전체 흐름은 별도 검증이 필요하다.

## 새 Google Cloud 프로젝트

현재 확인된 대상: **SafeClean**, ID `safeclean-511807`, 번호 `649043347047`.
2026-10-16 결제 연결 확인 완료: `01D409-BEEE68-795A6F`(shinsangwoon582 계정), `billingEnabled=true`.
프로젝트 관리는 `mrkiwonseo@gmail.com`, 사용료는 연결된 결제 계정의 등록 결제수단으로 청구된다.

1. [프로젝트 생성](https://console.cloud.google.com/projectcreate)에서 프로젝트를 만들고 **프로젝트 ID**를 확인한다.
2. [결제](https://console.cloud.google.com/billing)에서 해당 프로젝트에 결제 계정을 연결한다.
3. [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)를 설치하고 PowerShell에서 로그인한다.
   소스는 Cloud Build에서 빌드하므로 로컬 Docker는 필요 없다.

```powershell
gcloud auth login
$ProjectId = 'safeclean-511807'
gcloud config set project $ProjectId
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com iam.googleapis.com
```

**기본 비용:** 요청이 없을 때도 스케줄러가 돌아야 하므로 최소 인스턴스 1 + CPU 상시 할당이다.
0원 유휴 상태로 내려가지 않는다. Cloud Run·Cloud Build·Artifact Registry·Secret Manager 요금과
Google Cloud ↔ AWS Supabase 간 트래픽 비용이 발생할 수 있다. 결제 연결 전
[가격](https://cloud.google.com/run/pricing)을 확인한다.

## 실행/빌드 계정과 비밀 설정

실행 계정은 이 API 환경설정 시크릿 하나만 읽는다. 빌드 계정에는 시크릿 접근권한을 주지 않는다.
프로젝트 생성자 등 IAM 관리 권한이 있는 계정으로 한 번 실행한다.

```powershell
gcloud iam service-accounts create ansimclean-runtime --project=$ProjectId --display-name='Ansimclean API runtime'
gcloud iam service-accounts create ansimclean-builder --project=$ProjectId --display-name='Ansimclean source builder'
gcloud projects add-iam-policy-binding $ProjectId --member="serviceAccount:ansimclean-builder@$ProjectId.iam.gserviceaccount.com" --role=roles/run.builder
```

배포자는 Cloud Run Source Developer·Service Usage Consumer 및 두 서비스 계정의
Service Account User 권한이 필요하다. 공개 API 설정에는 Cloud Run IAM 정책을 변경할 권한도 필요하다.
처음 만든 프로젝트의 Owner 계정으로 설정할 수 있다. 조직 계정은 관리자가
[공식 소스 배포 권한](https://cloud.google.com/run/docs/deploying-source-code)을 부여한다.

`deploy/cloudrun/.env.example`을 `.env`로 복사해 실제 운영 값을 입력한다.
이미 `.env`가 있으면 덮어쓰지 않는다. 이 파일은 git에서 제외된다.
서울 DB·Supabase 키는 로컬 backend/.env의 값을 사용할 수 있지만,
SMS·Push·LiveKit 모드는 `solapi`·`fcm`·`cloud`여야 하며 운영용 키가 모두 필요하다.
서비스 계정 JSON은 base64 한 줄로 넣는다. 운영 가드를 local 프로필로 우회하지 않는다.

| 환경변수 | 확인/발급 위치 |
|---|---|
| SMS_API_KEY | [Solapi API Key 관리](https://console.solapi.com/credentials)의 API Key |
| SMS_API_SECRET | 같은 화면에서 발급한 API Secret |
| SMS_SENDER | Solapi 콘솔의 발신번호 관리에서 등록·인증되어 활성화된 번호, 하이픈 없이 입력 |
| SUPABASE_STORAGE_S3_ACCESS_KEY | [새 서울 프로젝트의 Storage S3](https://supabase.com/dashboard/project/qztjikulkicryvmgfuaw/storage/s3)에서 발급한 Access Key ID |
| SUPABASE_STORAGE_S3_SECRET_KEY | 같은 S3 키 발급 화면의 Secret Access Key |

Supabase의 S3 키는 service_role 키와 별개다. 앞서 Phone 로그인 설정의 Twilio 더미 값은
Supabase 로그인 활성화에만 사용하고, 실제 인증문자 발송은 이 Solapi 값들을 사용한다.
키는 로컬 `deploy/cloudrun/.env`에만 입력하고 채팅·Git에 넣지 않는다.

```powershell
# 저장소 루트에서 실행
Copy-Item deploy/cloudrun/.env.example deploy/cloudrun/.env
# 편집기로 .env를 채운 다음 업로드. 값을 명령행이나 채팅에 붙이지 않는다.
gcloud secrets create ansimclean-api-env --project=$ProjectId --replication-policy=automatic --data-file=deploy/cloudrun/.env
gcloud secrets add-iam-policy-binding ansimclean-api-env --project=$ProjectId --member="serviceAccount:ansimclean-runtime@$ProjectId.iam.gserviceaccount.com" --role=roles/secretmanager.secretAccessor
```

Secret Manager의 버전 번호를 확인한다. 첫 생성은 보통 `1`이지만 실제 목록으로 확인한다.
업데이트 때는 `gcloud secrets versions add ansimclean-api-env --project=$ProjectId --data-file=deploy/cloudrun/.env`를 쓴다.

```powershell
gcloud secrets versions list ansimclean-api-env --project=$ProjectId
```

## 배포

```powershell
# 실제 시크릿 버전으로 바꾼다. -DryRun은 설정만 출력하고 외부 변경을 하지 않는다.
./deploy/cloudrun/deploy.ps1 -ProjectId $ProjectId -SecretVersion 1 -DryRun
./deploy/cloudrun/deploy.ps1 -ProjectId $ProjectId -SecretVersion 1
```

- `backend/Dockerfile`로 Java 21 빌드와 단위·슬라이스 테스트를 실행한다.
  `backend/.gcloudignore`가 `.env`, target, 백업, 앱 소스 등을 업로드 대상에서 제외한다.
- 최소/최대 서비스 인스턴스 1, 리비전 최소 0·최대 1, vCPU 1·메모리 1GiB,
  CPU 항상 할당, 시작 CPU boost, 동시 요청 40·요청 제한시간 60초.
- `prod,cloudrun` 프로필. Cloud Run의 `PORT`에 HTTP로 응답하고 플랫폼이 HTTPS를 종료한다.
- 비밀값은 `/secrets/backend.env` 파일로 마운트한다. 시크릿 **숫자 버전**을 고정해 리비전 재현성을 유지한다.
- 모바일 로그인 API와 LiveKit 웹훅이 호출할 수 있도록 Cloud Run IAM은 공개하고,
  애플리케이션의 JWT·역할·소유권·웹훅 서명 검증을 그대로 사용한다.
- 시작 검사는 `/actuator/health`, 생존 검사는 `/actuator/health/liveness`다.
  생존 검사는 DB 일시 장애만으로 재시작하지 않는다. 배포 후 health=UP까지 확인한다.

## 스케줄러와 배포 중 중복 실행

최대 인스턴스 1은 클러스터 잠금이 아니다. 리비전 교체·인스턴스 복구 시 구·신 인스턴스가
잠시 겹칠 수 있어 모든 `@Scheduled` 진입점에 ShedLock을 적용했다.
Flyway `V051__scheduler_locks.sql`이 잠금 표를 만든다. 이 SQL을 수동 중복 실행하지 않는다.
DB UTC 시계로 조율하고, 실행 중에는 잠금을 갱신하며, 프로세스가 죽으면 약 2분 이내 만료된다.
동일 작업의 동시 실행을 막지만 정확히 한 번 실행을 보장하는 큐는 아니다.
작업의 기존 트랜잭션·행 잠금·멱등성은 계속 필요하다. Outbox 즉시 실행 경로는 기존 행 잠금으로 조율된다.
DB 장애로 잠금 갱신이 끊긴 경우의 중복 가능성은 로그로 점검한다.
같은 Supabase DB를 쓰는 기존 개발 서버도 배포 전에 종료하거나 동일한 잠금 코드를 적용한다.
잠금을 모르는 서버가 함께 실행되면 새 서버의 잠금만으로는 그 서버를 막을 수 없다.

Cloud Run 최소 인스턴스도 재시작될 수 있고, Spring의 cron은 서버가 꺼진 사이의 실행을
자동 보충하지 않는다. 매일 정리 작업의 누락까지 복구해야 할 때는 별도로 영속 스케줄러를 도입한다.
이번 변경은 기존 Spring 스케줄 주기와 업무 규칙을 유지한다.

## 연결과 완료 검증

배포 스크립트가 출력한 주소를 두 앱 `.env.production`에 설정한다.

```dotenv
VITE_API_URL=https://실제서비스주소.run.app/api/v1
```

```powershell
npm run build
npm run cap:sync
```

LiveKit 웹훅은 `https://ansimclean-api-rbtzr3o4cq-du.a.run.app/api/v1/live/webhooks/livekit`로 바꾼다.
API는 Spring만 제공하며 웹 앱 자체를 호스팅하지 않는다. 웹 앱 호스팅을 추가하면
그 HTTPS origin을 `ALLOWED_ORIGINS`에 추가한다.

완료 기준: 외부 health UP, 카탈로그 조회, 실기기 가입→로그인→예약→매칭→라이브→녹화·푸시,
요청이 없는 동안 OfferExpirer·Outbox 실행, 리비전 교체 중 스케줄러 잠금 동작 확인.
`CLIENT_IP_HEADER=X-Forwarded-For`는 기본 Cloud Run URL 직접 접근 기준이다.
임의로 넣은 헤더의 앞쪽 IP가 속도 제한 키를 바꾸지 못하는지도 실제 배포에서 검증한다.
앞단에 별도 로드밸런서/CDN을 추가하면 신뢰할 프록시 체인을 다시 설정한다.

## 로그·업데이트·롤백

```powershell
gcloud run services logs read ansimclean-api --project=$ProjectId --region=asia-northeast3 --limit=100
gcloud run revisions list --service=ansimclean-api --project=$ProjectId --region=asia-northeast3
# 코드와 필요 시 숫자 시크릿 버전을 바꾸고 deploy.ps1로 재배포
# 문제가 생기면 실제 이전 리비전 이름으로 전환
gcloud run services update-traffic ansimclean-api --project=$ProjectId --region=asia-northeast3 --to-revisions=PREVIOUS_REVISION=100
```

이미지/트래픽 롤백은 Flyway가 적용한 DB 변경을 되돌리지 않는다. 이전 코드와 호환되는
마이그레이션만 즉시 롤백한다. 새 인스턴스 시작에 필요한 시크릿의 이전 버전도 보관한다.
롤백 대상도 ShedLock을 지원하는 리비전이어야 한다. 잠금이 없는 Lightsail 버전과 동시에 실행하지 않는다.

## 공식 근거

- [CPU 상시 할당과 백그라운드 작업](https://cloud.google.com/run/docs/configuring/billing-settings)
- [최대 인스턴스 한도의 한계](https://cloud.google.com/run/docs/configuring/max-instances-limits)
- [컨테이너 실행 계약](https://cloud.google.com/run/docs/container-contract)
- [소스 배포](https://cloud.google.com/run/docs/deploying-source-code)
- [시크릿 파일 마운트](https://cloud.google.com/run/docs/configuring/services/secrets)
- [ShedLock 사용법·한계](https://github.com/lukas-krecan/ShedLock)
