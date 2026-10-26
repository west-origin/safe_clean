# 개발·운영 환경 분리

2026-10-19 사용자 요청으로 진행 중. 이 문서는 완료한 작업과 미완료 작업을 구분한다.

## 현재 상태

| 대상 | 개발 | 운영 후보 |
|---|---|---|
| Supabase 이름 | safe_clean_test | safe_clean |
| Supabase ref | xedkwtdkpxejlqpbdstb | qztjikulkicryvmgfuaw |
| 리전 | 서울(ap-northeast-2) | 서울(ap-northeast-2) |
| DB | 앱 데이터 복원, 개발 배포에서 Flyway V054까지 적용 | 2026-10-21 고객 자료 삭제 완료, 매니저 3명·참조 데이터 보존 |
| Auth | 사용자 7명·identities 복원, 기존 세션 제외 | 고객 계정·세션 삭제, 매니저 계정 3명 보존 |
| Storage | 비공개 버킷 3개·파일 4개 복원, SHA256 일치 | 고객 관련 파일 4개 삭제, 객체 0개 |
| Cloud Run | ansimclean-api-test, 기본은 OFF. 2026-10-19 APK 테스트 요청으로 현재 수동 1대(ON) 유지 | 기존 ansimclean-api가 원본 DB 사용 중 |
| LiveKit | safe_clean_test (`p_52jkb3emx8d`), 개발 전용 키·웹훅 | safe_clean (`p_6df4ofn7fr6`), 기존 운영 키·웹훅 |

도쿄 `ckvanmhgepdlxvfhdxes`는 사용자 최종 승인 후 삭제했다. 삭제 전 DB 69개 테이블·파일 3개를 `.env.environment-split/ckvanmhgepdlxvfhdxes/`에 백업했다. 현재 서울 원본도 `.env.environment-split/qztjikulkicryvmgfuaw/`에 백업했다. 두 경로와 모든 환경 파일은 Git 제외 대상이다. 백업에는 개인정보·인증 정보가 있어 외부 공유하지 않는다.

새 개발 DB에는 인증 세션·기기 푸시 토큰·세션 폐기 목록·스케줄 잠금을 복제하지 않았다. 로그인은 새 환경에서 다시 해야 한다. Spring Flyway 이력 49건(V051까지)을 복원했고, DB 시퀀스도 복원된 최대 ID에 맞췄다. V052 이후 변경은 이후 해당 코드 배포 시 Flyway로 적용한다.

## 다음 순서

1. 새 개발 DB의 비밀번호·Session pooler TLS 접속 및 S3 키 인증 확인 완료. Auth는 Phone 로그인 켬·Email 로그인 끔·직접 가입 끔·익명 로그인 끔. JWT 공개키 확인 완료.
2. 개발 Storage 전용 S3 키 발급/연결 완료. 2026-10-22 LiveKit 프로젝트·키·웹훅 분리 완료(아래 검증 기록). Firebase/FCM 및 SMS 자격증명 분리는 아직 미완료이며 현재 개발 설정은 기존 공급자 설정을 사용한다.
3. 개발 Cloud Run 서비스·실행 계정·Secret Manager를 별도로 만들고, 개발 앱 빌드를 새 API에 연결한다. 새 환경에서 로그인·예약·영상·테스트 결제를 검증한다.
4. 2026-10-21 사용자 요청으로 운영 고객 4명·Auth 계정/세션, 연결 예약 41건·정기예약 5건, 주소·고객 부가정보·관련 이력·파일 4개 삭제 완료. 삭제 후 고객·주소·주거정보·예약·정기예약·결제수단·Storage 객체 0건 확인. 매니저 프로필/Auth 3명, 서비스 분류 5건·가격표 10건 보존. 개발 서버·DB는 변경하지 않았다. 삭제 전 비공개 백업과 실행 결과는 Git 제외 경로 `.env.production-customer-cleanup-20261021/`에 보관한다.
5. 운영 환경에서는 테스트 결제 키를 사용하지 않는다. 현재 분리용 운영 환경 파일은 TOSS 키가 비어 있어 결제가 비활성이다.

## 설정 파일

### LiveKit 분리 (2026-10-22)

- 개발 프로젝트: `safe_clean_test`, ID `p_52jkb3emx8d`, URL `wss://safe-clean-test-443ul74r.livekit.cloud`.
- 개발 웹훅: `ansimclean-development` → `https://ansimclean-api-test-rbtzr3o4cq-du.a.run.app/api/v1/live/webhooks/livekit`. 서명 키는 개발 서버와 같은 개발 전용 키를 사용한다.
- 운영 프로젝트: 기존 `safe_clean` (`p_6df4ofn7fr6`, `wss://safe-clean-8i92pm9b.livekit.cloud`). 기존 운영 웹훅은 `ansimclean-api` 주소를 유지한다.
- `backend/.env`와 `deploy/cloudrun/environments/development/.env`의 LiveKit URL·API 키·시크릿을 개발 전용으로 교체했다. 운영 환경 파일은 유지했다. 키 값은 문서나 Git에 기록하지 않는다.
- Cloud Run 개발 서비스는 기존 이미지를 유지하고 Secret Manager `ansimclean-test-env` 버전 `3`만 적용했다. 리비전 `ansimclean-api-test-00003-cvz`, 트래픽 100%, 수동 1대 유지. 이전 버전과 비교해 변경된 값은 `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` 세 개뿐이다.
- 검증: LiveKit 대시보드 테스트 이벤트 → 개발 서버 HTTP 204. 개발 프로젝트의 실제 임시 방 생성·종료 → 개발 DB에 `room_started`·`room_finished` 수신. 개발 서명은 개발 서버 204·운영 서버 401, 운영 서명은 운영 서버 204·개발 서버 401. 개발 health `UP`.
- 프로젝트 데이터 지역은 미국(생성 화면 선택지는 미국·프랑크푸르트). 개발 Agent observability는 비활성. 녹화 업로드 목적지는 기존 서울 개발 Supabase Storage를 유지한다.
- 기존 13:38~13:39 테스트는 웹훅이 운영으로 들어가 개발 세션의 녹화 작업이 시작되지 않았다. 분리 후 실제 휴대폰 송출·녹화 파일 저장은 앱에서 새 세션으로 재검증해야 한다. 기존 IntelliJ 프로세스는 재시작해야 새 환경 파일이 적용된다.
- 비공개 이전 설정·검증 결과: `.env.livekit-split-20261022/` (Git 제외). 개발 재배포 스크립트는 개발 LiveKit URL과 키 존재 여부도 검사한다.

- 로컬 실행 설정: `backend/.env` — 새 개발 DB/Auth/Storage·테스트 결제 키로 전환했다. 이전 로컬 설정은 `.env.environment-split/backend-before-development.env`에 보관했다.
- 기존 배포 설정: `deploy/cloudrun/.env` — 운영 후보 DB 연결을 유지하며 테스트 결제 키를 제거했다. 원격 운영 리비전/시크릿 버전은 변경하지 않았다.
- 개발 설정: `deploy/cloudrun/environments/development/.env` — 새 Supabase ref·service_role·DB 비밀번호·전용 S3 키·별도 OTP salt·관리자 토큰. Secret Manager `ansimclean-test-env`와 전용 실행 계정 `ansimclean-test-runtime` 사용.
- 운영 설정 초안: `deploy/cloudrun/environments/production/.env` — 기존 운영 후보 DB. 테스트 결제 키 제거. 이 운영 초안은 아직 배포하지 않았다.

## 개발 Cloud Run 켜기·끄기

`deploy/cloudrun/development.ps1`은 개발 서비스 이름을 고정해 운영 서비스에 적용할 수 없게 했다.

```powershell
./deploy/cloudrun/development.ps1 Start
./deploy/cloudrun/development.ps1 Stop
./deploy/cloudrun/development.ps1 Status
```

- OFF는 자동 스케일링의 최소 0대가 아니라 **수동 스케일링 0대**다. 외부 요청이 와도 자동으로 기동하지 않는다. Stop은 별도 URL로 시작할 수 있는 트래픽 태그도 제거한다.
- Start는 수동 1대로 켜고 health=UP을 확인한다. 실패하면 다시 0대로 되돌린다. 테스트 종료 시 Stop을 실행한다. 꺼진 동안 API·백그라운드 예약 작업·웹훅 처리는 중단된다.
- `deploy-development.ps1`은 항상 0대로 배포하고, 실패한 경우에도 생성된 개발 서비스를 0대로 되돌린다. 기존 `deploy.ps1`로 개발 서비스에 배포하는 것은 차단했다. 코드만 업데이트할 때 `-SecretVersion 2`처럼 기존 숫자 버전을 재사용할 수 있다.
- 개발 API 주소: `https://ansimclean-api-test-rbtzr3o4cq-du.a.run.app/api/v1`.
- 앱의 개발 서버는 `.env.development.local`, 개발용 설치 빌드는 `.env.staging.local`을 사용한다. `npm run build:development`로 두 앱을 개발 API에 연결해 빌드한다. `npm run cap:sync:development`는 개발 빌드를 네이티브 프로젝트에 복사한다. 기존 설치 앱은 재빌드/재설치 전까지 이전 API를 계속 사용한다.
- 실제 결제/영상 세션이 처리 중일 때 강제로 끄면 완료 확인·웹훅 재시도가 다음 기동까지 지연될 수 있다. 테스트의 승인·환불 결과 확인 후 끈다.
- 2026-10-19 검증 완료: 리비전 `ansimclean-api-test-00002-tzr`, Secret Manager 버전 `2`, Cloud Build `eb69c8ff-18db-4932-98fe-5f55014c46a8`. Start 후 health UP·카탈로그 HTTP 200·비로그인 auth/me HTTP 401. Stop 후 manual/0·HTTP 503 확인. 검증 결과는 `.env.environment-split/cloudrun-development-verification.json`에 보관한다. 코드·데이터를 삭제하지 않고 다음 Start에서 같은 개발 환경을 다시 켠다.

## 배포 중 발견한 기동 오류

첫 개발 배포에서 `TossPaymentGateway`에 실제 생성자와 테스트용 생성자가 함께 있어 Spring이 생성자를 선택하지 못했다. 실제 생성자에 `@Autowired`를 지정하고, Spring 컨텍스트가 해당 빈을 생성하는 회귀 검사를 추가했다. 로컬 승인 실행에서 관련 검사 4개 통과, 재빌드에서 백엔드 558개 검사 통과. 이 수정은 개발 배포에 포함하며 기존 운영 리비전은 변경하지 않았다.

## 비용 기준 (2026-10-19 확인)

- 현재 Supabase 조직은 Free이며 활성 프로젝트 2개까지 가능하다. 새 서울 개발 프로젝트 생성 견적은 월 $0이다. Free는 1주간 비활성 시 정지될 수 있다.
- Pro는 조직당 월 $25부터이며 첫 Micro 컴퓨트가 크레딧으로 충당된다. 같은 Pro 조직에 Micro 2개를 운영하면 대략 월 $35부터다. 초과 사용량·추가 옵션은 별도다.
- 기존 Cloud Run은 vCPU 1·1GiB·최소 인스턴스 1·CPU 상시 할당이다. 같은 구성을 두 벌 상시 실행하면 추가 컴퓨트 비용이 발생한다. 공식 리전별 요금·실행 시간·월 무료 사용량으로 산정해야 한다. 무료 사용량은 결제 계정 전체에 공유되므로 프로젝트를 나눠도 두 배가 되지 않는다.
- 개발을 필요할 때만 켜면 비용을 줄일 수 있지만, 서버가 꺼져 있는 동안 Spring 스케줄러·아웃박스·환불 재시도는 동작하지 않는다.
- Cloud Build·Artifact Registry·Secret Manager·외부 트래픽·문자·영상 녹화 비용은 별도다.

공식 문서: [리전 변경](https://supabase.com/docs/guides/troubleshooting/change-project-region-eWJo5Z), [Supabase 요금](https://supabase.com/pricing), [Cloud Run 요금](https://cloud.google.com/run/pricing).
