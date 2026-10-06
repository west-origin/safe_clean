# 운영 문서 (runbook)

> `BACKEND_PLAN.md` §12 를 B1(2026-09-19)에 옮겨 왔다. 이후 배포·설정 변경은 여기에 기록한다.

## 로컬 실행
```bash
cd backend
cp .env.example .env            # 값 채우기 (아래 "외부 서비스 설정")
./mvnw -q verify                # 단위·슬라이스·통합(*IT, Docker 필요). Docker 가 없으면 -DskipITs 후 PROGRESS 에 "IT 미실행"
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl -s localhost:8080/actuator/health          # {"status":"UP"}
curl -s localhost:8080/api/v1/catalog/categories/home/options
```
`application.yml` 이 `backend/.env` 를 읽는다(`spring.config.import`). 배포 환경은 진짜 환경변수를 쓴다.

## 외부 서비스 설정 (B0 체크리스트 — 끝나면 날짜와 함께 아래에 기록)
| 서비스 | 설정 | 상태 |
|---|---|---|
| Supabase `ansimclean-dev` | 리전 Seoul. Auth › Providers: Phone **끔**, Email **끔**. "Allow new users to sign up" **끔**. JWT Signing Keys 비대칭(ES256) → `SUPABASE_JWT_MODE=jwks`. Storage 버킷 `recordings`·`profile-images`(비공개) + S3 access key | 대기 |
| Supabase Auth 검증 | **2026-09-19 완료(일부)**: `POST /auth/v1/token?grant_type=password` 에 `phone` → `422 phone_provider_disabled`, `email` → `400 invalid_credentials`. → 휴대폰 로그인을 쓰려면 Phone provider 를 켜야 한다 (계획 §7.1.1). 남은 확인: service_role 키로 admin 사용자 생성 → 로그인 → refresh → logout | 부분 완료 |
| **Auth 보안(보안 검토 2026-09-22)** | 대시보드 → Settings → API Keys: **anon(publishable) key 를 비활성화**(서버는 service_role 만 쓴다). 불가하면 Authentication → Settings 에서 "Secure phone change"(번호 변경 시 새 번호 확인) 를 켠다. 이유: 키가 새면 사용자 JWT 로 `PUT /auth/v1/user` 를 불러 자기 번호를 남의 번호로 바꿔 그 번호의 가입을 막을 수 있다. 확인: anon key + 사용자 JWT 로 `PUT /auth/v1/user {"phone":"+8210…"}` → 401/403 | 대기 |
| **Auth 토글** | **2026-09-19 완료**: "Allow new users to sign up" 끔 · Phone provider 켬(Twilio 칸은 더미 `ACxxx…`/32자/`MGxxx…`, Supabase 의 문자 발송은 쓰지 않는다) · "Enable phone confirmations" 끔. 새 프로젝트로 옮길 때 똑같이 한다. 확인: `GET /auth/v1/settings` → `phone:true, disable_signup:true, phone_autoconfirm:true` | 완료 |
| DB 스키마 검증 | **2026-09-19 완료**: V001~V005 를 dev 프로젝트에서 트랜잭션으로 실행 후 롤백 — 테이블 36, RLS 36, 인덱스 62, 겹침 방지 제약 생성, seed 개수·한국어 문구·`\n` 이스케이프·시간대 모두 정상. DB 에는 아무것도 남기지 않았다(Flyway 가 최초 기동 때 적용) | 완료 |
| LiveKit Cloud | 프로젝트 1개, Ingress·Egress 활성, API key/secret, 웹훅 URL `https://<api>/api/v1/live/webhooks/livekit` (로컬은 cloudflared) | 대기 |
| Solapi | 계정, 발신번호 사전등록, API key/secret. `SMS_PROVIDER=solapi` | 대기 |
| Firebase | 프로젝트, 두 앱 등록, 서비스 계정 JSON(base64) | B6 |

### 확인된 프로젝트 정보 (dev, 2026-09-19)
- 프로젝트 `ckvanmhgepdlxvfhdxes`, 리전 **ap-northeast-1(도쿄)** — 계획은 서울(ap-northeast-2)이었다. 테스트용이라 그대로 두고, 운영 프로젝트는 서울로 만든다.
- Postgres **17.6**, `search_path = "$user", public, extensions`. `pgcrypto`·`uuid-ossp` 설치됨, `btree_gist` 는 V001 이 `public` 에 설치한다(Supabase 권고는 `extensions` 스키마지만, Testcontainers 와 동일하게 동작시키려고 기본 스키마를 쓴다 — 보안 경고 lint 는 무시).
- `auth`·`storage` 스키마 준비됨. **Storage 버킷 `recordings`·`profile-images` 는 아직 없다(만들어야 함).** `repair-photos`(비공개, 수리 사진) 는 2026-09-21 dev 프로젝트에 SQL 로 만들었다: `insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values ('repair-photos','repair-photos',false,5242880,'{image/jpeg,image/png,image/webp,image/heic,image/heif}')` — prod 에도 같은 문장
- `GET /auth/v1/settings` (설정 후): `email:true, phone:true, disable_signup:true, phone_autoconfirm:true, sms_provider:"twilio"(더미)`.

## 배포·운영

### 환경변수 (`.env.example` — 값 없이 이 목록 그대로)
```
SPRING_PROFILES_ACTIVE=local|prod
# local·test 가 아닌 프로필(prod, 또는 프로필을 빠뜨린 배포)은 ProductionGuard 가 기동 전에 검사한다 — SMS_PROVIDER=solapi · PUSH_MODE=fcm · LIVEKIT_MODE=cloud(wss URL·키) ·
# SUPABASE_URL https · service_role · S3 endpoint https·키 · ALLOWED_ORIGINS 에 * / http:// 없음 · ADMIN_TOKEN 은 비우거나 32자 이상. 하나라도 어기면 기동하지 않는다 (감사 M-10)
SERVER_PORT=8080
ALLOWED_ORIGINS=capacitor://localhost,https://localhost,ionic://localhost,http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:5176
DB_URL=jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require   # Session pooler
DB_USER=postgres.<project-ref>
DB_PASSWORD=
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=            # 서버에만. 앱·git 금지
SUPABASE_JWT_MODE=jwks|secret
SUPABASE_JWT_SECRET=                  # secret 모드일 때만
SUPABASE_STORAGE_S3_ENDPOINT=https://<project-ref>.supabase.co/storage/v1/s3
SUPABASE_STORAGE_S3_ACCESS_KEY=
SUPABASE_STORAGE_S3_SECRET_KEY=
SUPABASE_STORAGE_S3_REGION=ap-northeast-2
LIVEKIT_URL=wss://<project>.livekit.cloud
LIVEKIT_API_KEY=
LIVEKIT_API_SECRET=
SMS_PROVIDER=solapi|logging
SMS_API_KEY=
SMS_API_SECRET=
SMS_SENDER=01000000000                # 사전등록한 발신번호
OTP_SALT=                             # 32바이트 이상 난수
FIREBASE_SERVICE_ACCOUNT_JSON=        # base64 한 줄 (B6)
```
- 앱 origin: Capacitor iOS 는 `capacitor://localhost`, Android 는 `https://localhost`(`androidScheme: https`), 웹 개발은 5174/5175.
- DB 접속은 **Session pooler(5432)**. Transaction pooler(6543)는 JPA 의 prepared statement 와 충돌한다. 커넥션 풀 `HikariCP maximumPoolSize=10`(Supabase 무료/프로 플랜 한도 안).
- 배포: `./mvnw -DskipTests spring-boot:build-image -Dspring-boot.build-image.imageName=ansimclean/api:<git-sha>` → 레지스트리 → Fly.io(`fly deploy`, Tokyo `nrt`, 1 머신 shared-cpu-1x 1GB, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`). 헬스체크 `/actuator/health`. 스케줄러(`@Scheduled`)가 있으므로 **인스턴스는 1개**(§13-11 전까지).
- 마이그레이션: 기동 시 Flyway 자동. 실패하면 기동 실패 → 이전 이미지로 롤백(`fly releases rollback`), SQL 은 새 버전으로 수정 후 재배포. 운영 DB 는 Supabase 일일 백업 + 위험한 마이그레이션 전 수동 백업.
- 로그: JSON 한 줄(ECS — `logging.structured.format.console=ecs`, local/test 프로필은 텍스트), 요청마다 `rid`(MDC, 응답 헤더 `X-Request-Id`, 500 응답 `requestId`) 로 사용자 신고와 짝지음, 개인정보 마스킹(§3-10), Fly 로그 → 필요 시 외부 수집. 지표는 `/actuator/metrics`(외부 노출 금지 — `management.endpoints.web.exposure.include=health`).
- 키 회전: Supabase service_role·LiveKit secret·Solapi 키는 환경변수만 바꾸고 재배포. 바디캠 스트림 키 회전 = `POST /admin/devices` 에 `rotate: true`(옛 Ingress 삭제 + 재생성) + 기기 재설정. JWT 서명키 회전은 Supabase 가 JWKS 로 알아서(앱 영향 없음).
- 장애 대응 표: Supabase Auth 다운 → 로그인만 실패(`UPSTREAM`), 기존 토큰은 JWKS 캐시로 계속 검증됨 / 문자 API 다운 → 가입·인증만 실패(`UPSTREAM`), 로그인은 정상 / LiveKit 다운 → 라이브만 실패, 작업 상태는 진행 가능 / 바디캠 끊김 → `waiting_camera`, 매니저 푸시 / Egress 실패 → `recording_status=failed`, 완료 화면 "업로드 실패", 운영자 확인. / 매칭 실패(후보 0명·회차 소진) → `bookings.matching_failed_at` 표시 + 고객 푸시 1회 + ERROR 로그, 예약은 `assigning` 으로 남는다 → 운영자가 아래 SQL 로 찾아 매니저에게 직접 연락하거나 고객과 일정을 조정한다.

## 운영자 SQL (관리 화면 전까지 — §13-8)
매니저 승인·정산 완료·문의 답변·매칭 실패 확인은 Supabase 대시보드 SQL 로 한다. **승인은 `approved` 만 바꾸면 매칭에서 빠진다** — 지역·시도·활동 시작일을 같이 넣어야 한다(`ManagerProfile.approve` 와 같은 컬럼).
```sql
-- 승인 대기 매니저
select p.id, p.name, p.phone, p.created_at from manager_profiles m join profiles p on p.id = m.manager_id where m.approved = false and p.deleted_at is null;
-- 승인 (areas = '시도 시군구' 문자열 배열, sido = 대표 시도). 목록을 본 뒤 탈퇴했으면 0행 — 그 계정은 승인하지 않는다 (자체 점검 #55: 예전 SQL 은 없는 컬럼 m.id·m.created_at 을 썼고 탈퇴를 보지 않았다)
update manager_profiles set approved = true, areas = array['서울특별시 강남구','서울특별시 서초구'], sido = '서울특별시', active_since = current_date, updated_at = now()
 where manager_id = '<manager uuid>' and exists (select 1 from profiles p where p.id = manager_profiles.manager_id and p.deleted_at is null);
-- 매칭 실패로 멈춘 예약 (운영자 조치 필요)
select b.id, b.customer_id, b.service_date, b.service_time, b.sido, b.sigungu, b.dispatch_round, b.matching_failed_at
  from bookings b where b.status = 'assigning' and b.matching_failed_at is not null order by b.service_date;
-- 자동: 매시 05분(08:05~20:05) MatchingRetry 가, 약속 시각이 3시간보다 더 남은 예약에 한해 지금 매칭 구간에 이 예약의 콜을 받은 적 없는 새 후보(새로 승인·근무시간·지역 변경)에게만 다시 보낸다 — 매니저를 승인하면 그 조건에서 다음 실행 때 간다.
-- 약속이 3시간 안이거나 밤이라 기다릴 수 없으면 아래 redispatch API 로 바로 보낸다.
-- 밤(서울 21시~08시)에 생긴 새 콜은 아침 08:05 MatchingRetry 가 보낸다(급한 예약은 바로 나간다, 문제 목록 2번). 미뤄 둔 예약 — 급하면 아래 redispatch API 로 바로 (보류를 지운다.
--   dispatch_held_rescheduled 면 일정 변경 전 매니저에게는 '새 콜' 푸시 대신 '예약 일정이 변경됐어요' 하나만 간다):
select b.id, b.service_date, b.service_time, b.dispatch_held_at, b.dispatch_held_manager_id, b.dispatch_held_rescheduled
  from bookings b where b.status = 'assigning' and b.dispatch_held_at is not null order by b.id;
-- 08:05 뒤에도 남아 있으면 '밤에 미룬 매칭 보내기 실패' 로그를 확인한다 (매시 05분에 다시 시도한다)
-- 이미 받았던 매니저에게까지 다시 보내려면(예: 전화로 조정) SQL 이 아니라 관리 API 로 (표시만 지우면 자동 재매칭 대상에서도 빠진다).
-- 새 회차로 후보 전원에게 다시 보내고 { bookingId, offered } 를 돌려준다. offered 가 0 이면 다시 매칭 실패(고객에게 또 알림):
--   curl -X POST https://<api>/api/v1/admin/bookings/<booking uuid>/redispatch -H "X-Admin-Token: $ADMIN_TOKEN"
-- 고객이 일정을 바꿔도 다시 매칭된다(표시도 지워진다)
-- 문의 답변은 SQL 이 아니라 관리 API 로 (푸시가 같이 나간다):
--   curl -X POST https://<api>/api/v1/admin/inquiries/<id>/answer -H "X-Admin-Token: $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"answer":"…"}'
-- 답변 대기 문의
select id, user_id, content, created_at from inquiries where status = 'open' order by created_at;
-- 정산 완료 처리 (입금 뒤)
update settlements set status = 'complete', settled_at = now() where id = '<settlement uuid>' and status = 'pending';
-- '청소 완료' 를 누르지 않고 떠난 작업 (매일 03:10 StaleBookingCleaner 가 ERROR 로그를 남긴다). 매니저에게 확인한 뒤 관리 API 로 닫는다
-- (매니저 완료와 같다: 정산 pending·완료 수·고객 푸시·시리즈 마감, 라이브는 재조정기가 1분 안에 멈춘다. 자체 점검 #17):
--   curl -X POST https://<api>/api/v1/admin/jobs/<booking uuid>/complete -H "X-Admin-Token: $ADMIN_TOKEN"
select id, manager_id, customer_id, service_date, status, started_at from bookings where status in ('in_progress','paused') and service_date < current_date - 1 order by service_date;
-- 패널티 면제(증빙 — 진단서·사고 확인서·장비 고장 확인 뒤)는 SQL 이 아니라 관리 API 로 (docs/manager-policy.md "증빙이 있으면 면제").
-- SQL 로 status 만 'waived' 로 바꾸면 점수는 빠지지만 콜 배정 정지(manager_profiles.suspended_until)가 남는다 — API 는 둘을 한 트랜잭션에서 바꾼다 (문제 목록 3번).
-- note 는 면제 근거(필수). 응답 { penaltyId, managerId, points(면제 뒤 누적 점수), suspendedUntil(아직 정지면 마지막 날, 풀렸으면 null) }.
-- 면제 뒤에도 5점 이상이면 정지는 그대로다(기간을 줄이지 않는다). 같은 요청을 다시 보내도 안전하다(이미 면제면 그대로):
--   curl -X POST https://<api>/api/v1/admin/penalties/<penalty uuid>/waive -H "X-Admin-Token: $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"note":"진단서 확인"}'
-- 면제할 기록 찾기 (매니저 id 는 위 승인 대기 쿼리처럼 profiles 에서)
select id, category, points, status, detail, created_at from manager_penalties where manager_id = '<manager uuid>' order by created_at desc;
-- 6회 실패한 아웃박스 이벤트 다시 살리기 (원인을 고친 뒤)
update outbox_events set status = 'pending', attempts = 0, next_attempt_at = now() where status = 'failed' and id = '<event uuid>';
```



## 라이브 (B5) — LiveKit Cloud · 바디캠 · 녹화본
1. **LiveKit Cloud 프로젝트**: 대시보드 → Settings → Keys 에서 API key/secret. `backend/.env` 에 `LIVEKIT_MODE=cloud`, `LIVEKIT_URL=wss://<project>.livekit.cloud`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`. (`logging` 모드면 LiveKit 없이 돌고 카메라는 붙지 않는다 — 로컬 기본값)
2. **웹훅**: LiveKit 대시보드 → Settings → Webhooks → URL `https://<api 호스트>/api/v1/live/webhooks/livekit` (같은 API key 로 서명). 유실돼도 `LiveReconciler` 가 1분마다 조회로 맞춘다.
3. **녹화 저장소**: Supabase Storage 에 비공개 버킷 `recordings`(그리고 `profile-images`) 생성 → Storage → S3 access keys 발급 → `SUPABASE_STORAGE_S3_*`. Egress 는 이 S3 자격증명으로 `recordings/<bookingId>.mp4` 를 올리고, 고객 재생은 service_role 로 만든 서명 URL(6시간).
4. **바디캠 등록**(§13-20, 관리 화면 전까지): `ADMIN_TOKEN` 을 `.env` 에 두고
   ```bash
   curl -X POST https://<api>/api/v1/admin/devices -H "X-Admin-Token: $ADMIN_TOKEN" -H "Content-Type: application/json" \
     -d '{"managerId":"<매니저 uuid>","name":"안심캠 #AC-00231","serial":"AC-00231"}'
   ```
   응답의 `streamUrl`·`streamKey` 를 기기에 **한 번만** 설정한다(앱 촬영 장비 화면은 마스킹된 키만 보여준다). 같은 매니저로 다시 부르면 Ingress 는 재사용된다. **분실·도난이면 `"rotate": true`** 를 붙여 옛 Ingress 를 지우고 새 키를 받는다. 매니저가 탈퇴하면 `AccountMediaDeleted` 핸들러가 Ingress·기기 행을 지운다.
5. **바디캠 없이 검증**(§8.1): 노트북이 바디캠 역할 —
   ```bash
   ffmpeg -re -stream_loop -1 -i sample.mp4 -c:v libx264 -preset veryfast -b:v 2M -an -f flv "<streamUrl>/<streamKey>"
   ```
   매니저 앱 청소 시작 → 바디캠(ffmpeg) 켬 → `ingress_started` → 고객 앱 시청 → 일시정지(검은 화면) → 완료 → 3분 안에 녹화본. 실기기는 §8.3 검증 5항목만 남는다.
6. **운영 확인 지점**: `live_sessions.status` 가 `waiting_camera` 로 5분 넘게 남으면 매니저 푸시 1회 + WARN 로그. `recording_status=failed` 는 Egress 오류(대시보드 Egress 탭에서 원인). `manager_devices.pending_room` 이 남아 있으면 바디캠이 대기 방에 계속 송출 중인 것 — 기기를 껐다 켜면 풀린다.

## 고객센터 전화번호 (사용성 검토 2026-09-22)
`app_settings.support_phone` 이 비어 있으면 앱은 "고객센터 전화하기" 버튼을 그리지 않는다.
운영 번호가 정해지면 채운다 — 현장에서 바디캠이 죽었을 때 매니저가 쓸 유일한 즉시 경로다.

```sql
update app_settings set value = '"1588-0000"'::jsonb where key = 'support_phone';
```

## 세션 폐기 (감사 M-03, 2026-09-24)
- 로그아웃·비밀번호 재설정으로 끊은 Supabase 세션은 `revoked_sessions`(V023)에 90일 남는다. 인증 필터와 `/auth/refresh` 가 거부한다(서버 메모리에 두고 1분마다 DB 에서 다시 읽는다).
- Supabase 대시보드 Auth › Sessions 의 **비활성 만료(Inactivity timeout)를 90일 이하**로 둔다 — 그보다 오래 사는 refresh 토큰은 목록에서 지워진 뒤 다시 쓸 수 있다.
- 비밀번호 재설정은 `auth.sessions` 를 **읽기만** 한다(쓰기 금지 원칙 그대로). 서버 DB 계정에 `auth.sessions` select 권한이 있어야 한다(없으면 재설정이 500 — dev 에서 확인).
- 수리 사진(감사 M-04): 올린 뒤 24시간 안에 예약에 붙지 않은 사진은 매일 03:40 삭제(`repair_photo_uploads`, V024). 탈퇴하면 `repair-photos/<고객id>/` 폴더 전체를 지운다.

## 일시정지 중 바디캠 잠금 (감사 H-02, 2026-09-24)
- 일시정지 중 카메라가 끊겨 있으면 서버가 Ingress 를 `enabled=false` 로 잠근다(`manager_devices.ingress_enabled=false`). 재개·작업 종료·다음 작업 시작 때 연다. 로그: "일시정지 — 바디캠 Ingress 잠금", "바디캠 Ingress 잠금 해제".
- 작업이 진행 중인데 카메라가 안 붙으면 먼저 `select ingress_enabled from manager_devices where manager_id=…` 를 본다. false 로 남았으면 재조정기(1분)가 연다 — 계속 false 면 LiveKit API 오류 로그를 본다.
- "일시정지 중 바디캠이 다시 붙음 … 사후 mute" 경고는 잠금보다 재접속이 빨랐다는 뜻이다(남은 위험). 잦으면 알린다.
