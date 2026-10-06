# Production Readiness & Security Audit

검사일: 2026-09-24 (Asia/Seoul)  
대상: 안심클린 고객·매니저 앱 및 API  
기준 커밋: `488c9ebad8b504f60d5caae13945df58baede102` + 이번 감사 수정  
판정: **NOT READY** — 현재 구현된 라이브의 일시정지 개인정보 보호에 배포 차단 문제가 남아 있음.

## 1. Executive Summary

**결제 기능은 현재 미구현 상태이며 이번 배포 평가 범위에서 제외됨.** 포인트 충전·결제 승인·환불·PG webhook·결제 멱등성 및 해당 테스트 유무는 점수에 반영하지 않았다. `payment` 패키지의 interface/noop, 결제수단 조회 stub은 향후 항목으로 분리했다. 매니저 작업 완료 시 생성되는 예상 정산 원장·조회는 실제 구현된 업무 데이터이며 송금/결제로 취급하지 않았다.

### 실제 확인한 기능과 구조

- `apps/customer`: React/Vite/Capacitor 고객 앱. 휴대폰 인증·회원가입·로그인·비밀번호 재설정·탈퇴, 약관 동의, 주소/주거/프로필/알림 관리, 청소·정기청소·수리 예약과 견적·변경·취소, 사진 업로드, 예약 조회, 라이브·녹화본, 문의·공지·FAQ, 푸시.
- `apps/manager`: 동일 스택 매니저 앱. 가입·로그인, 승인 상태 안내, 콜 조회·수락·거절, 작업 시작·중단·재개·완료·체크리스트·방문 취소, 근무시간/휴무, 패널티, 예상 정산 내역, 바디캠 상태·라이브 미리보기, 문의·공지·푸시.
- `packages/ui`: 공통 UI·토큰·날짜/비동기/폴링 훅. JavaScript/JSX이며 TypeScript type check는 해당 없음.
- `backend`: Java 21 / Spring Boot 4.1.1 / Spring Security / JPA / Flyway / PostgreSQL. 운영 인증은 Supabase JWT, SMS는 Solapi, 영상은 LiveKit Ingress/Egress, 파일은 Supabase Storage, 푸시는 Firebase.
- Controller 매핑 **91개**(결제 stub 1개 포함), 자동 구성 health 별도. [전체 API 목록](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/API_INVENTORY.md>). Java main 파일 306개, V001–V022 migration, SQL `CREATE TABLE` 37개 확인.
- 관리자: `X-Admin-Token` 기반 기기 등록·키 회전 및 문의 답변. 매니저 승인 등 일부 운영 작업은 runbook의 수동 SQL.
- CI는 프론트 빌드/테스트/린트 및 백엔드 verify를 정의. Dockerfile/compose/nginx/Fly 배포 manifest는 찾지 못했고 runbook에 배포 계획이 있다.

### 검사 범위와 한계

컨트롤러 경로·SecurityFilterChain → CurrentUser/DB role → 서비스 소유권·상태 전이 → Repository/제약조건 → 외부 어댑터·outbox → 앱 API/session/화면을 추적했다. 인증, 예약, 콜 수락, 작업, 파일, 라이브, 관리자와 개인정보 삭제 흐름을 우선 검토했다. 전체 구조와 위험 지점을 조사했으나 모든 화면 조합의 수동 E2E를 수행한 것은 아니다.

실제 운영 API·DB·Supabase 콘솔·Storage ACL·LiveKit·SMS·FCM에 요청하지 않았다. 실제 메일/SMS 발송·과금·운영 데이터 삭제·배포는 수행하지 않았다. 백엔드 테스트에서는 실제 `.env` import를 덮어쓰고 외부 대역을 사용했다.

운영 TLS/프록시/headers/CORS 값, Supabase Auth 토글·세션 설정, 백업 복구, 알림 수신, 실제 RLS/Storage 정책, 실기기 영상, 서명된 APK/IPA는 **NOT VERIFIED**. Docker CLI/socket이 없어 PostgreSQL Testcontainers IT는 **미실행**이다. 기존 결과를 이번 실행으로 간주하지 않았다.

### 발견 건수

동일 원인은 하나로 집계했다. 미검증 운영 체크리스트 자체를 취약점 개수에 더하지 않았다.

| 심각도 | 발견 | 이번 변경 | 남은 상태 |
|---|---:|---|---|
| Critical | 0 | — | 확인된 Critical 없음 |
| High | 2 | H-01 코드 수정·동일 단위 재현 통과 | H-02 미해결, H-01 실제 DB 경쟁 재검증 필요 |
| Medium | 10 | M-01 수정·회귀 테스트 통과 | M-02–M-10 후속 조치 필요 |
| Low | 1 | — | L-01 |

가장 위험한 문제 TOP 5:

1. **H-02:** 일시정지 중 바디캠 재접속 시 새 영상 트랙을 사후 mute하는 구조. 집 안 영상/녹화 보호를 보장하지 못함.
2. **H-01:** 인증 토큰 소비 실패 전에 외부 비밀번호가 변경되던 경쟁 조건. 수정했으나 Postgres 동시성 IT 확인 필요.
3. **M-03:** 로그아웃·비밀번호 변경 후 기존 access JWT가 만료 전 재사용 가능하고, upstream 세션 폐기 실패를 성공 응답과 구분하지 않음.
4. **M-06:** 탈퇴 가드와 예약 생성이 동일 사용자 잠금에 참여하지 않아 탈퇴 뒤 예약이 남을 수 있음.
5. **M-05:** 예약/문의 등 쓰기 API의 남용 제한 부족. 하나의 계정으로 예약·오퍼·outbox를 증폭시킬 수 있음.

### 실제 실행 결과

| 검증 | 결과 | 증거/한계 |
|---|---|---|
| 프론트 production build | PASS, 두 앱 | `npm run build`, [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/build-final.log>) |
| 프론트 테스트 | **130 files / 271 tests PASS** | 수정 전 129/265, 추가 native 테스트 6개. [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/frontend-tests-final.log>) |
| ESLint | PASS | [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/lint-final.log>) |
| 디자인 token 검사 | PASS, 455 files | [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/tokens.log>) |
| 백엔드 package/verify | **60 suites / 305 tests PASS**, jar 생성 | Java 21, `-DskipITs`, 명시적 Mockito/JaCoCo javaagent. [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/backend-final.log>) |
| 실제 DB integration | NOT RUN | Docker 없음. 신규 `OtpConsumptionIT` 컴파일만 확인 |
| Type check | NOT APPLICABLE | JS/JSX 프로젝트 |
| npm audit | NOT VERIFIED | 일반 실행 DNS 실패. 네트워크 확대 실행은 자동 승인 검토가 의존성 메타데이터 외부 전송 이유로 거부함. 외부 전송 승인 질문을 남김. [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/npm-audit.json>) |
| Maven vulnerability scan | NOT VERIFIED | offline dependency tree는 생성, CVE DB 대조는 미수행 |
| Secret scan | 범위 내 실제 secret 일치 없음 | 현재 Git 추적 파일 1,488개 + 생성 dist, 로컬 secret 3개 값과 비교 및 키/JWT/리터럴 패턴 검사. 값은 보고서·로그에 출력하지 않음 |

초기 백엔드 실행은 sandbox의 Mockito self-attach 문제로 실패했다. dependency나 보안 설정을 완화하지 않고 JVM 시작 시 agent를 지정해 다시 실행했다. 최종 통과는 **단위·슬라이스·아키텍처 테스트**이며 IT 통과가 아니다. Maven 로그의 ERROR 일부는 테스트가 의도한 오류 시나리오다. 테스트 수는 Surefire XML로 집계했다. [요약 JSON](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/test-summary.json>)

## 2. Critical — 즉시 수정

확인된 Critical은 **0개**다. 이는 운영 환경과 외부 서비스까지 안전하다는 인증을 뜻하지 않는다.

## 3. High

### [H-01] 일회용 인증 토큰 소비 전에 비밀번호를 변경하는 경쟁 조건

**상태:** CONFIRMED — 수정 전 테스트 재현, 코드 수정 및 같은 테스트 통과. 실제 DB 동시 요청은 NEEDS VERIFICATION.  
**위험도:** High  
**위치:** [AuthService.java:109](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthService.java:109>), [AuthService.java:140](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthService.java:140>), [OtpService.java:107](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/OtpService.java:107>), [PhoneVerificationRepository.java:19](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/PhoneVerificationRepository.java:19>)  
**함수/API:** `resetPassword`, `signup`, `OtpService.consume`; `POST /auth/password/reset`, `POST /auth/signup`.

**문제 설명:** 기존 순서는 `peek → Supabase.updatePassword → consume`이었다. 두 요청이 peek를 통과하면 소비가 실패한 요청도 비밀번호를 이미 변경한다. consume도 단순 읽기/필드 변경이라 DB 수준 compare-and-set이 없었다. 가입 잔재 계정 재사용 경로에도 같은 순서가 있었다.

**공격/장애 시나리오:** 같은 유효 verificationToken을 갖는 두 요청을 동시에 보내거나, 정상 요청과 탈취한 토큰 요청을 경쟁시킨다. 후발 요청이 오류를 받더라도 계정 비밀번호는 후발 값일 수 있다. OTP 없이 임의 계정을 바로 탈취하는 취약점이라고 주장하지 않는다.

**영향:** 토큰 1회 사용 보장 위반, 비밀번호 최종 값 불일치, 로그인 실패·계정 통제 경쟁.

**재현 방법:** 새 `AuthServiceTest.consumedResetTokenMustNotChangePasswordEvenAfterSuccessfulPeek` 및 `consumedSignupTokenMustNotChangeOrDeleteUpstreamAccount`에서 peek 성공 후 consume을 OTP_EXPIRED로 설정한다. 수정 전 **2/2 실패**, 외부 `updatePassword`가 호출된 것을 확인했다. [수정 전 로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/auth-before.log>)

**수정 방법 / 반영:** 외부 변경 전에 별도 트랜잭션의 토큰 소비를 완료한다. Repository에서 `consumedAt is null AND verifiedAt > :oldest AND role=:role` 조건부 UPDATE의 영향 행 수가 정확히 1일 때만 진행한다. 재설정과 가입 양쪽에 적용했다.

```java
if (repository.consumeIfUnused(token, role, now, now.minus(TOKEN_TTL)) != 1)
    throw new ApiException(ErrorCode.OTP_EXPIRED);
// AuthService: consume의 트랜잭션 커밋 후에만 외부 비밀번호 변경
```

**재검증:** 같은 2개 공격 시나리오 및 stale peek 소비 패배 테스트 통과, 최종 백엔드 전체 305개 통과. 신규 [OtpConsumptionIT.java:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/test/java/kr/ansimclean/api/auth/OtpConsumptionIT.java:1>)은 실제 Postgres에서 동시 2요청 중 정확히 1개만 성공하는 테스트지만 **미실행**이다.

**수정의 영향:** 토큰 소비 후 upstream/DB 실패 시 토큰을 되살리지 않는다. 다시 SMS 인증해야 한다. 이미 가입된 계정은 로그인으로 이어간다. API 문서를 수정했다. 여러 번 발급한 서로 다른 유효 토큰의 경합과 upstream timeout 후 최종 상태는 추가 IT 대상이다.

### [H-02] 일시정지 중 바디캠 재접속의 사후 mute로 영상 보호 공백

**상태:** CONFIRMED — 코드 및 기존 단위 테스트의 상태 확인. 실제 영상 패킷·유출 시간은 NEEDS VERIFICATION. **미해결 / 배포 차단.**  
**위험도:** High  
**위치:** [LiveService.java:321](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveService.java:321>), [LiveService.java:243](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveService.java:243>), [LiveReconciler.java:61](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveReconciler.java:61>), [LiveServiceTest.java:221](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/test/java/kr/ansimclean/api/live/LiveServiceTest.java:221>)  
**함수/API:** `cameraAttached`, `viewerToken`, `reconcileOne`; LiveKit `ingress_started`, `POST /live/sessions/{id}/token`.

**문제 설명:** 작업이 PAUSED여도 새 track은 `cameraAttached(trackSid, false, ...)`로 LIVE가 된다. 이후 mute를 시도하고 실패하면 `mute_pending`만 기록한다. 이때 예약은 PAUSED인 채 유지되며 `ensureRecording`까지 호출된다. 재조정기는 60초 주기다. PAUSED도 ACTIVE에 포함돼 새 고객 시청 토큰 발급이 허용된다. 상태를 LIVE로 정확히 표시하는 것은 영상 접근을 차단하는 조치가 아니다.

**공격/장애 시나리오:** 정상적으로 중단한 뒤 바디캠 네트워크가 끊겼다가 새 track으로 재연결된다. mute API 장애·웹훅 지연 중 기존 시청자/유효 viewer 토큰이 새 트랙을 구독할 수 있다. recording이 이미 실행 중인 경우 영상 보존까지 영향을 받을 수 있다.

**영향:** 고객 주거 공간 및 매니저의 중단 시간 영상 노출. LiveKit 기본 구독 동작은 [공식 미디어 문서](https://docs.livekit.io/transport/media/publish/)와 일치한다. 실제 바디캠 설정에 따른 노출 길이는 추정하지 않았다.

**재현 방법:** 실행한 `LiveServiceTest`의 `카메라_재접속은_mute_가_확인된_뒤에만_paused_트랙이_없으면_live_가_아니다`가 예약 PAUSED + 확인 결과 unmuted일 때 세션 LIVE/mute_pending을 직접 단언한다. 실기기 재검증은 격리된 테스트 방에서 합성 영상으로 pause → 끊김 → 재연결 → mute 실패를 주입하고 기존 viewer·새 viewer·녹화 결과를 확인해야 한다. 이번에는 외부 LiveKit 호출을 실행하지 않았다.

**수정 방법:** 일시정지를 UI/DB 상태나 기존 track SID mute에만 의존하지 말고 미디어 계층에서 새 발행·구독·녹화까지 기본 차단하도록 설계해야 한다. 재연결 전 권한 유지, 기존 토큰/참가자의 권한 철회, paused 중 새 토큰 제한, 명시적 재개 뒤 허용을 하나의 상태 전이로 검증한다. [LiveKit 권한 관리](https://docs.livekit.io/intro/basics/rooms-participants-tracks/participants/)는 canPublish 철회 시 트랙 해제를 설명하지만, 이 프로젝트의 Ingress 재연결·Egress까지 안전하다고 보장하는 증거는 아직 없다.

**미수정 이유:** 단순히 상태를 PAUSED로 덮거나 고객 화면을 숨기면 실제 송출을 가릴 뿐이다. Ingress·Egress와 재연결 권한의 변경 및 실서비스 대역 검증 없이는 안전한 해결을 확정할 수 없다. 이 항목을 수정 완료로 표시하지 않으며, 라이브가 포함된 현재 배포는 승인하지 않는다.

## 4. Medium

### [M-01] 네이티브 인증 토큰의 평문 중복 저장 및 삭제 실패 후 복원

**상태:** CONFIRMED — **수정 및 재검증 완료**(실기기 저장소는 별도 확인).  
**위험도:** Medium  
**위치:** [session.js:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/customer/src/api/session.js:1>), [session.js:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/manager/src/api/session.js:1>), [session.native.test.js:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/customer/src/api/session.native.test.js:1>)  
**함수:** `write`, `hydrate`, `session.clear`.

**설명/시나리오:** 기존 write는 native에서도 access/refresh를 localStorage에 먼저 저장하고 Keychain/Keystore에도 썼다. 보안 저장소 삭제 실패는 무시하며 다음 실행에서 저장된 옛 세션을 다시 읽었다. 기기/WebView 데이터 접근이나 비정상 종료·저장 실패가 조건이다. XSS가 발견되었다는 뜻은 아니다.

**영향:** 보안 저장소 도입 효과 약화, 로컬 로그아웃 상태 복원, 공유 기기에서 이전 계정 노출 가능성.

**재현:** 두 앱에 대해 평문 미저장, 기존 평문 이전 후 삭제, secure.remove 실패 후 재실행을 테스트. 수정 전 6/6 실패, 수정 후 6/6 통과. [전](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/native-before.log>) / [후](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/native-after.log>).

**수정:** native는 보안 저장소만 사용하고 평문 fallback을 제거했다. 저장 작업 직렬화 및 비밀이 아닌 pending 표식으로 실패 시 옛 값을 복원하지 않는다. 웹 저장 방식은 현행 계약을 유지한다. 저장소 오류 시 로그인 지속성이 사라질 수 있으나 평문으로 보안 수준을 낮추지 않는다. Keychain·Keystore 실제 암호화/잠금 중 동작은 mock 테스트만으로 확인되지 않는다.

### [M-02] 업로드 MIME만 검사하고 실제 파일 내용을 검증하지 않음

**상태:** CONFIRMED  
**위험도:** Medium  
**위치:** [RepairPhotoService.java:37](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/RepairPhotoService.java:37>), [RepairPhotoServiceTest.java:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/test/java/kr/ansimclean/api/booking/RepairPhotoServiceTest.java:1>)  
**함수/API:** `upload`, `POST /customer/repair-photos`(정확한 매핑은 API inventory 참조).

**설명/시나리오:** 클라이언트가 `Content-Type: image/jpeg`라고 주장하면 `{1,2,3}` 같은 비이미지도 Storage에 전달된다. 실제 기존 정상 테스트가 그 바이트를 사용하고 통과했다.

**영향:** 임의 콘텐츠 저장, 깨진 이미지·후속 이미지 처리 취약점 노출면. 서버 UUID 파일명/확장자, private bucket 전제와 `<img>` 렌더링을 고려하면 이것만으로 stored XSS나 RCE가 확정되는 것은 아니다.

**재현:** storage mock에 임의 바이트와 허용 MIME을 넘기고 upload 호출 확인. HTML/SVG MIME은 차단되지만 MIME을 허용 이미지로 위장하면 본문은 검증하지 않는다.

**수정:** magic bytes와 MIME 일치, 지원 디코더로 구조/픽셀 수 제한, 필요시 안전한 재인코딩. HEIC/HEIF 호환성과 처리 메모리 상한을 함께 검증한다. 5MB 제한과 UUID 경로는 유지한다. 단순 확장자 검사만 추가하는 것은 충분하지 않다.

### [M-03] 로그아웃/비밀번호 변경 후 세션 폐기 보장 부족

**상태:** CONFIRMED(서버 검사·실패 처리), 운영 토큰 TTL/실제 upstream 동작은 NEEDS VERIFICATION  
**위험도:** Medium  
**위치:** [JwtToCurrentUserConverter.java:26](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/common/security/JwtToCurrentUserConverter.java:26>), [SupabaseAuthClient.java:118](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/common/supabase/SupabaseAuthClient.java:118>), [SupabaseAuthClient.java:132](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/common/supabase/SupabaseAuthClient.java:132>)  
**함수/API:** `logout`, `logoutOtherSessions`, JWT converter; `/auth/logout`, `/auth/password/reset`.

**설명/시나리오:** JWT 서명·만료·role은 검사하지만 폐기 session_id는 검사하지 않는다. 로그아웃/다른 세션 폐기 upstream 실패는 catch 후 warn으로 끝나 호출자는 성공 처리한다. 이미 탈취한 access JWT는 만료 전 계속 사용할 수 있고, upstream 폐기가 실패하면 refresh도 남을 수 있다.

**영향:** 사용자가 로그아웃/비밀번호 변경으로 기대한 접근 차단과 실제 접근 기간의 차이. Supabase의 일반 JWT 동작 자체를 signature 우회로 오해하면 안 된다. [공식 sign-out 문서](https://supabase.com/docs/guides/auth/signout), [session 검증 지침](https://supabase.com/docs/guides/auth/sessions).

**재현:** 테스트 IdP 대역에서 logout 호출을 실패시키고 성공 응답 경로를 확인한 뒤, 같은 서명 JWT가 converter에서 계속 수락되는지 검사한다. 실제 계정 세션 폐기는 이번에 실행하지 않았다.

**수정:** 민감 API에 session_id 생존 확인 또는 서버 폐기 목록/사용자 token-valid-after를 적용하고 TTL 정책을 명시한다. 폐기 실패는 재시도 가능한 작업으로 기록하거나 호출자에게 정확히 알린다. 모든 API가 외부 IdP 장애에 연쇄 실패하지 않도록 캐시/실패 정책도 설계한다.

### [M-04] 예약에 연결하지 않은 사진의 삭제·보존 경로 누락

**상태:** CONFIRMED(코드 경로), 실제 Storage lifecycle은 NOT VERIFIED  
**위험도:** Medium  
**위치:** [RepairPhotoService.java:44](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/RepairPhotoService.java:44>), [AccountMediaDeletionHandler.java:56](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/AccountMediaDeletionHandler.java:56>)  
**함수:** `upload`, `AccountMediaDeletionHandler.handle`.

**설명/시나리오:** 사진 업로드 후 예약을 중단하면 경로가 Booking에 저장되지 않는다. 탈퇴 핸들러는 `customerBookings → getRepairPhotos`만 삭제하며 사용자 폴더 전체 목록/미연결 업로드 원장을 조회하지 않는다. RecordingRetention은 녹화본 전용이다.

**영향:** 미제출 주거 사진과 저장 비용이 남음. 기존 예약 사진 삭제가 실패한다고 일반화하지 않는다.

**재현:** storage fake에 사진만 저장 → 예약 생성 없이 탈퇴 이벤트 처리 → 해당 경로 delete 호출 없음. 실제 운영 파일을 삭제하는 재현은 하지 않았다.

**수정:** 소유자·생성시각·첨부 상태를 가진 upload 원장과 TTL 정리, 탈퇴 시 해당 사용자 전체 미디어 정리를 구현한다. 삭제 진행 중 늦게 완료되는 업로드/녹화도 재조정 대상으로 포함한다.

### [M-05] 인증 후 쓰기와 일부 공개 검증의 남용 제어 부족

**상태:** CONFIRMED(제한 부재), 운영 edge 제한은 NOT VERIFIED  
**위험도:** Medium  
**위치:** [BookingService.java:127](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingService.java:127>), [SupportService.java:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/SupportService.java:1>), [RateLimiter.java:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/RateLimiter.java:1>), [AuthService.java:83](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthService.java:83>)  
**API:** 예약 생성, 문의 생성, 인증번호 verify 등.

**설명/시나리오:** 로그인/OTP 발송/refresh/사진에는 제한이 있지만 예약과 문의에는 사용자별 횟수/총량 상한이 없다. 멱등 키는 없거나 매번 새로 보내면 남용 방어가 아니다. 예약 한 건이 후보 매니저 전원 오퍼·outbox로 커진다. verify는 전화번호별 제한이라 임의 번호를 바꿔 전역 DB 호출과 메모리 키를 늘릴 수 있다.

**영향:** DB·운영자 업무·푸시 호출 증폭, 부하 장애. 부하 테스트로 장애 임계치를 측정한 것은 아니다.

**재현:** 외부 전송을 mock한 환경에서 동일 계정의 서로 다른 키 예약/문의 반복 요청을 보낸다. 익명 verify는 서로 다른 정상 형식 전화번호로 반복한다. 운영에는 실행하지 않는다.

**수정:** 예약·문의·verify에 사용자/IP/burst 제한과 동시 진행 예약 상한, 총 발송 비용 상한을 정책으로 정하고 적용한다. RateLimiter의 키 수/TTL을 제한하고 다중 인스턴스 전에는 공유 저장소 또는 edge 제한을 사용한다. 현재 runbook의 단일 인스턴스 전제를 유지해야 한다.

### [M-06] 탈퇴와 예약 생성의 경합으로 탈퇴 후 예약이 생성될 가능성

**상태:** LIKELY — 잠금 불일치는 코드 확인, DB 경쟁 실행 미검증  
**위험도:** Medium  
**위치:** [AuthService.java:238](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthService.java:238>), [BookingService.java:130](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingService.java:130>), [ProfileService.java:426](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ProfileService.java:426>)  
**API:** `DELETE /auth/account` vs `POST /customer/bookings`.

**설명/시나리오:** 탈퇴는 profile 행을 잠그고 열린 예약 유무를 검사하지만, 예약 생성은 그 잠금에 참여하거나 live profile을 재검사하지 않는다. 이미 인증을 통과한 생성 요청이 탈퇴 검사 뒤 insert/commit할 수 있다. profile은 soft delete이므로 FK만으로 차단되지 않는다.

**영향:** 탈퇴한 고객의 신규 예약·주소 스냅샷·오퍼 생성, 삭제 흐름과 업무 무결성 불일치.

**재현:** 격리 Postgres에서 두 트랜잭션을 barrier로 제어해 탈퇴의 가드 직후 예약 생성을 커밋한다. 고객 신규 예약도 동일 profile lock을 획득하고 deleted_at을 재확인해야 한쪽을 차단할 수 있다. 테스트는 제안이며 이번에 실행하지 않았다.

**수정:** 예약 생성 및 개인정보 쓰기에서 현재 사용자 상태를 동일 트랜잭션·동일 잠금으로 확인한다. profile→manager→booking 등 전체 잠금 순서를 먼저 정리해 deadlock을 새로 만들지 않도록 한다.

### [M-07] 배정 정지 매니저가 이미 받은 콜을 수락할 수 있음

**상태:** CONFIRMED(서비스 검사 누락), 실 DB 시나리오는 미실행  
**위험도:** Medium  
**위치:** [OfferService.java:186](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/offer/OfferService.java:186>), [ProfileService.java:292](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ProfileService.java:292>) (`isApprovedManager`, `approvedManagers`).  
**함수/API:** `OfferService.doAccept`, `POST /manager/offers/{id}/accept`.

**설명/시나리오:** 후보 생성은 suspendedUntil을 확인하지만 수락은 잠근 ManagerProfile의 approved만 확인한다. 콜을 받은 뒤 다른 작업 패널티로 정지돼도 아직 pending·미만료인 콜을 직접 수락할 수 있다.

**영향:** 서버 운영 제재 우회. 남의 콜을 수락하는 IDOR는 아니며 본인 pending offer가 있어야 한다.

**재현:** 승인된 매니저에 pending offer 생성 → suspendedUntil을 미래로 설정 → accept 요청. 조건 분기에 suspension 검사가 없음을 확인했다. 단위/IT에 이 시나리오를 추가해야 한다.

**수정:** 잠근 매니저에 `isSuspended(clock.today())`를 수락 시 재검사한다. 정지 전 이미 확정된 업무는 별도 정책으로 다루고, pending offer 정리 여부를 결정한다.

### [M-08] 외부 API 호출을 포함하는 긴 DB 트랜잭션

**상태:** CONFIRMED(트랜잭션 범위), 장애 규모는 LIKELY  
**위험도:** Medium  
**위치:** [OutboxDispatcher.java:82](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/common/outbox/OutboxDispatcher.java:82>), [OutboxDispatcher.java:93](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/common/outbox/OutboxDispatcher.java:93>), [LiveService.java:184](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveService.java:184>)  
**함수:** `dispatchBatch`, `setMuted`, Storage 서명/삭제·LiveKit 호출 흐름.

**설명/시나리오:** outbox를 도입했지만 한 트랜잭션으로 최대 50개 행을 가져온 뒤 handler의 외부 호출을 순차 실행한다. LiveService도 트랜잭션/세션 lock을 유지한 채 외부 조회와 polling을 한다. upstream 지연 시 DB connection/lock 점유가 길어진다. 각 호출 timeout이 있어도 배치 전체 제한을 뜻하지 않는다.

**영향:** outbox 지연, pool(설정 10개) 압박, pause 응답 지연, 부분 외부 성공 후 DB rollback 시 중복 재시도. 재시도/멱등 처리가 있으므로 곧바로 데이터 손상이라고 단정하지 않는다.

**재현:** 테스트 gateway에 지연과 마지막 handler 실패를 주고 connection 점유·배치 rollback·기기 중복 수신을 계측한다. 이번에는 부하 주입을 실행하지 않았다.

**수정:** 짧은 claim/lease 트랜잭션 → 외부 실행 → 건별 완료 기록을 분리하고 consumer 멱등성을 유지한다. 라이브는 개인정보 보호를 보장하는 상태 전이와 함께 재설계한다. 단순히 `@Transactional`을 삭제하면 기록 유실을 만들 수 있다.

### [M-09] 목록 무제한 조회와 후보/프로필 N+1

**상태:** CONFIRMED(쿼리 구성), 대용량 지연 수치는 NOT VERIFIED  
**위험도:** Medium  
**위치:** [BookingService.java:196](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingService.java:196>), [ProfileService.java:338](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ProfileService.java:338>), [OfferService.java:140](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/offer/OfferService.java:140>)  
**함수/API:** `reservations`, `nextVisit`, `managerIntros`, `findCandidates`, 문의/패널티 목록.

**설명/시나리오:** 예약 목록은 전체 이력을 읽고 홈의 다음 방문도 전체 목록을 만든 뒤 최소값을 찾는다. managerIntros는 매니저 ID별 findById, 매칭은 승인 매니저별 근무시간·휴무·겹침을 조회한다. 데이터와 후보 수에 따라 조회 수가 증가한다.

**영향:** 장기 사용자/매니저 증가 시 응답·메모리·DB 비용 증가. 실제 full table scan은 EXPLAIN 없이 확정하지 않는다.

**재현:** 격리 DB에 많은 이력/후보를 넣고 Hibernate 통계와 EXPLAIN ANALYZE로 쿼리 수·페이지 단위 메모리를 측정한다. 이번에는 정적 쿼리 구조만 확인했다.

**수정:** cursor pagination/범위 상한, 다음 방문 전용 LIMIT 1 조회, manager profile batch 조회, 후보 선정 SQL/미리 가져오기. 프론트 계약도 함께 페이지 처리로 바꾼다.

### [M-10] production에서 logging 대역 사용을 막는 환경 가드 부재

**상태:** CONFIRMED(구성), 실제 운영 설정은 NOT VERIFIED  
**위험도:** [LoggingLiveKitGateway.java:22](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LoggingLiveKitGateway.java:22>), [LoggingSmsSender.java:14](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/common/sms/LoggingSmsSender.java:14>), [application.yml:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/resources/application.yml:1>)  
**위험도:** Medium  
**함수/설정:** `LIVEKIT_MODE`, `SMS_PROVIDER`, `PUSH_MODE` 및 logging bean 활성화.

**설명/시나리오:** LiveKit은 설정 누락 시 logging이 기본이며 실제 카메라가 붙지 않는다. SMS/Push는 값 누락 시 실패하도록 했지만 명시적으로 logging을 넣으면 prod에서도 허용된다. SMS logging은 OTP 본문을 로그에 쓴다. local/test 전용이라는 주석과 런타임 제한이 일치하지 않는다.

**영향:** health는 정상인데 문자/영상/푸시가 동작하지 않는 배포, prod 로그 OTP 노출. 로컬 `.env`의 logging 사용 자체를 운영 사고로 간주하지 않았다.

**재현:** 외부 호출 없는 컨텍스트 테스트에서 prod + logging 속성으로 대역 빈 선택을 확인한다. 코드상 Profile 가드가 없다. 실제 prod 앱 기동은 실행하지 않았다.

**수정:** prod 기동 시 실제 provider 및 필수 키/HTTPS URL/bucket을 검증하고 미설정은 fail closed. logging 구현은 local/test에서만 허용한다. 배포 환경 주입을 검증하는 smoke test를 CI에 추가한다.

## 5. Low

### [L-01] CI 외부 Actions가 immutable commit으로 고정되지 않음

**상태:** CONFIRMED  
**위험도:** Low  
**위치:** [ci.yml:1](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/.github/workflows/ci.yml:1>)  
**대상:** `actions/checkout@v4`, `actions/setup-node@v4`, `actions/setup-java@v4`.

**설명/시나리오:** mutable major tag가 가리키는 코드가 변경되면 같은 저장소 커밋의 CI 실행 코드도 달라진다. 해당 Actions가 침해됐다는 증거는 없다.

**영향:** 빌드 공급망 재현성·검토 범위 약화.

**재현:** workflow uses 선언에서 `@v4` 확인. 공격 실행은 불필요하다.

**수정:** 검증한 commit SHA로 고정하고 업데이트 PR 자동화를 사용한다. workflow 권한을 최소화하고 의존성 감사 job을 별도 추가한다.

## 6. 잘 구현된 부분

- JWT decoder가 서명 알고리즘을 제한하고 issuer/audience/만료를 검증한다. 잘못된 signature/issuer/aud/expired 토큰을 실제 decoder 단위 테스트에서 거부했다. JWKS ES256/RS256 설정도 검사한다. 운영 JWKS 통신 성공은 별도다.
- role은 클라이언트 JWT claim을 그대로 쓰지 않고 profiles에서 읽는다. 가입 중 PENDING 권한을 구분하고 `/customer/**`, `/manager/**`를 서버에서 제한한다.
- 예약·주소·콜·작업·근무불가·문의·녹화본의 조회/변경에 사용자 소유권 조건이 있다. 남의 ID에 404를 반환하는 흐름과 슬라이스/서비스 테스트가 있다. 이번 점검에서 확정된 일반 IDOR는 찾지 못했다.
- 관리자 토큰은 빈 값일 때 닫히고 constant-time 비교를 사용한다. 전역 AdminTokenFilter와 개별 controller 검증이 있으며, health 외 actuator는 노출하지 않는다.
- OTP는 SecureRandom, hash 저장, TTL·시도/재발송 제한, phone advisory lock을 사용한다. 이번 수정으로 소비에도 DB 조건부 갱신을 추가했다.
- SQL/JPA 조회는 바인딩 파라미터와 고정 쿼리다. 사용자 입력을 OS 명령·템플릿 실행에 넘기는 경로는 발견하지 못했다. migration의 SQL 문자열 생성은 사용자 요청 입력이 아니다.
- Flyway, `ddl-auto=validate`, `open-in-view=false`, FK/NOT NULL/CHECK, 활성 phone unique, 기본 주소 부분 unique, 매니저 작업 겹침 exclusion, 오퍼 수락 unique, 예약별 정산 unique가 있다.
- 예약·콜·작업·문의의 멱등 기록은 업무 저장과 같은 트랜잭션이며 요청 본문 변경을 hash로 탐지한다. 매니저/예약/오퍼 잠금과 상태 전이 메서드가 있다. 실제 DB 경쟁은 별도 확인해야 한다.
- UUID 생성 파일명, 크기 5MB·전체 요청 6MB, 사용자별 경로 검사, 파일 소유권 후 signed URL 발급이 있다. 영상은 backend byte[]로 통째로 받지 않고 Egress→Storage로 전송한다. 사진만 제한된 byte[]를 사용한다.
- React 텍스트 렌더링을 사용하며 조사한 앱 소스에 dangerouslySetInnerHTML/eval/new Function sink는 없었다. production CSP는 script self, base-uri/form-action 제한을 포함한다.
- HTTP timeout/refresh 단일 실행, 비동기 오류 화면, route lazy import, LiveKit dynamic import, 주요 timer/listener cleanup이 있다.
- 구조화 ECS 로그, request ID와 500 응답 추적, DB 제약 오류의 민감 상세 제거, 아웃박스 backoff/재조정, 녹화 보존 정리, 탈퇴 보상 흐름이 있다.

## 7. 기능별 검사 결과

`정상`은 정적 흐름 및 명시한 로컬 테스트 기준이다. 실제 서비스 E2E를 뜻하지 않는다. 결제/충전/stub은 표에서 제외했다.

| 기능 | 정상 동작 | 보안 | 예외처리 | 동시성 | 테스트 | 상태 |
|---|---|---|---|---|---|---|
| OTP·가입·로그인·재설정 | 로컬 통과 | H-01 수정, M-03/M-05 | upstream 오류 매핑, 소비 후 재인증 필요 | 조건부 소비 추가, DB IT 미실행 | unit/MVC 통과, 신규 IT 미실행 | 수정 후 실DB 검증 필요 |
| 프로필·주소·주거·약관 | 로컬 통과 | 사용자 ID principal 사용 | DTO 검증/404 | 주소 unique, 동시 최초 생성은 충돌 가능 | unit/MVC 통과 | 조건부 가능 |
| 계정 탈퇴 | 로컬 가드/익명화 통과 | M-04/M-06 | outbox 재시도 | 신규 예약과 경합 미해결 | unit 통과, DB 경쟁 미실행 | 수정 필요 |
| 예약·견적·정기회차·수리 | 로컬 통과 | 서버 가격 계산/소유권, M-05 | date/enum/options/404 | 멱등·booking lock, M-06 | unit/MVC 통과, IT 미실행 | 수정 필요 |
| 사진 업로드·조회 | 로컬 통과 | M-02/M-04 | 용량/MIME/Storage 오류 | UUID 경로 | unit/MVC 통과 | 수정 필요 |
| 콜 매칭·수락·거절 | 로컬 통과 | 소유권, M-07 | 만료/409 | manager→booking→offer, DB 제약 | unit/MVC 통과, IT 미실행 | 수정 필요 |
| 작업·중단·체크리스트·완료 | 로컬 통과 | 담당 매니저 검사 | 상태 전이/선행 영상 검사 | booking lock/멱등 | unit/MVC 통과 | H-02 연계 차단 |
| 라이브·바디캠·녹화 | 대역에서 통과 | **H-02**, 소유권/웹훅 서명 | mute 실패 pending/502 | 세션 lock, 사후 mute 공백 | unit/MVC 통과, 실영상 미검증 | **배포 차단** |
| 근무시간·휴무·일정 | 로컬 통과 | 소유권 검사 | 시간 순서/요일 검증 | 기존 업무 검사 28일 범위, 경합 IT 없음 | unit/MVC 통과 | 장기 일정·경합 추가 확인 |
| 패널티·예상 정산 원장 | 로컬 통과 | 본인 내역, M-07 | 상태/정책별 처리 | booking unique, 패널티 경쟁 추가 확인 | 로컬 테스트 통과 | 운영 정책 확인 |
| 문의·공지·FAQ | 로컬 통과 | 본인 문의, 관리자 답변, M-05 | 길이/공백/404 | 문의 멱등, 답변 동시 수정 추가 확인 | unit/MVC 통과 | 제한 추가 필요 |
| 푸시·outbox | 대역에서 통과 | 토큰 마스킹/사용자 분리 | 재시도·실패 상태 | M-08, 다중 인스턴스 미검증 | unit 통과, IT 미실행 | 실제 전달 검증 필요 |
| 관리자 API | 로컬 인증 테스트 통과 | 토큰 이중 검증 | 401/검증 오류 | 기기 회전 외부 효과 확인 필요 | unit/MVC 통과 | 운영 접근/키 관리 미검증 |

추가 경계 테스트가 필요한 경우: `null` enum/컬렉션 원소, 과거/먼 미래 날짜, 28일 이후 예약과 휴무 충돌, 중복 start/complete, 답변 동시 수정, 두 계정의 같은 FCM 토큰 등록, 계정 전환 중 네트워크 응답, 동일 key 다른 요청, 외부 API timeout 후 재시도. 기존 테스트가 모든 조합을 커버한다고 주장하지 않는다.

## 8. 보안 체크리스트

PASS는 점검한 코드/로컬 테스트 범위만 의미한다. 운영 확인이 필요한 경우 별도 표기한다.

| 검사 항목 | 결과 | 근거 |
|---|---|---|
| Authentication | NOT VERIFIED | 로컬 기본 검증 PASS, H-01 DB 경쟁 및 실제 Supabase 흐름 필요 |
| Authorization | FAIL | 경로·소유권 기본 방어는 양호, 정지 제재 M-07 및 영상 상태 H-02 |
| IDOR/BOLA | PASS | 조사한 주요 리소스의 사용자 소유권 조건·음성 테스트 확인; 운영 전체 공격 미실행 |
| JWT/Session | FAIL | JWT 서명/issuer/aud/만료 PASS, 폐기 M-03 |
| OAuth | NOT APPLICABLE | OAuth 로그인 서버 flow 미구현 |
| SQL Injection | PASS | 사용자 입력 바인딩; 동적 사용자 SQL 조합 미발견 |
| XSS | PASS | 조사한 렌더링에 실행 sink 미발견; HTML 위장 업로드는 M-02로 별도 |
| CSRF | NOT APPLICABLE | API는 Authorization Bearer·관리자 헤더, cookie 인증/서버 session 없음 |
| SSRF | NOT APPLICABLE | 사용자 URL을 서버에서 fetch하는 구현 미발견; 외부 base URL은 운영 설정 |
| Path Traversal | NOT VERIFIED | 서버 UUID 파일명/고객 prefix/`..` 방어 확인. encoded object 경로 end-to-end 미실행 |
| File Upload | FAIL | MIME 위장 검증 누락 M-02, 고아 업로드 M-04 |
| File Download | NOT VERIFIED | 소유권 확인·유효기간 있는 서명 URL 발급 확인, 실제 private ACL/폐기 미검증 |
| Secrets | PASS | 현재 tracked source·dist에서 실제 secret 일치 없음; Git 전체 history/배포 artifact는 제외 |
| Rate Limiting | FAIL | 인증·사진 일부 적용, M-05; edge와 trusted proxy 미검증 |
| Brute Force | PASS | 로그인 phone/IP, OTP 시도/TTL/발송 제한. 대규모 분산 공격 미검증 |
| CORS | NOT VERIFIED | 코드 명시 origins, credentials 사용 안 함; 운영 ALLOWED_ORIGINS 미확인 |
| CSP | PASS | 두 dist에 meta CSP 있음, script self. connect/img/media의 https 전체 허용은 좁히기 권고 |
| Security Headers | NOT VERIFIED | Spring 기본 headers와 meta만으로 정적 호스트 HSTS/frame-ancestors/Referrer/Permissions 정책 보장 불가 |
| Dependency Vulnerability | NOT VERIFIED | registry 감사 차단, Maven CVE 대조 미실행 |
| Sensitive Logging | FAIL | prod에서도 logging SMS 선택 가능해 OTP 원문 기록 M-10; 일반 요청은 body/header 미기록 |
| Admin Endpoint | PASS | AdminAuth + 필터, 빈 토큰 거부, constant-time 검사와 로컬 테스트 |
| Error Information Leakage | PASS | 클라이언트에는 일반 오류/요청 ID, SQL·stack 비노출 테스트 통과 |
| Mass Assignment | PASS | 명시 DTO/필드별 변경; userId/role/status/가격은 서버 결정, entity 직접 binding 없음 |
| Input Validation | FAIL | DTO/날짜/상태 검사 다수, 파일 내용 M-02와 mutation 시 정지 재검사 M-07 누락 |
| Payment Security | **NOT APPLICABLE** | **현재 미구현** |

## 9. 배포 준비 체크리스트

| 항목 | 결과 | 설명 |
|---|---|---|
| Production build 성공 | Yes | 웹 두 앱 + backend jar; APK/IPA release는 Not Verified |
| 테스트 통과 | Not Verified | 실행한 271+305 PASS, 전체 IT/E2E 미실행 |
| Critical 0개 | Yes | 점검 범위 내 |
| High 0개 또는 위험 수용 완료 | **No** | H-02 남음, H-01 실DB 재검증 필요, 위험 수용 없음 |
| secrets 코드에서 제거 | Yes | 현재 스캔 범위 내 실secret 미발견, 로컬 ignored env는 유지 |
| production 환경변수 분리 | Not Verified | 파일 분리는 있으나 prod 가드 M-10/운영 주입 미검증 |
| DB migration 검증 | Not Verified | SQL 검토, V001–V022 실제 적용·rollback/restore 미실행 |
| DB backup 존재 | Not Verified | runbook에 언급, 실증 없음 |
| HTTPS 적용 | Not Verified | native mixed content 제한과 https API 빌드 설정만 확인 |
| CORS 제한 | Not Verified | allowlist 코드 있음, 운영 값/프록시 확인 필요 |
| CSP 설정 | Yes | HTML meta CSP; frame-ancestors는 HTTP 응답 header 필요 |
| security headers 설정 | Not Verified | 정적 호스팅·edge·TLS 종료점의 실제 응답 확인 필요 |
| rate limiting | No | 전체 위험 endpoint 적용 미완료 |
| 로그 설정 | Yes | ECS/request ID/error handler, M-10 운영 대역 차단 필요 |
| 모니터링 | Not Verified | 운영 dashboard/SLO/queue 지연 경보 미확인 |
| error tracking | Not Verified | 서버 로그는 있음, 외부 수집/알림·클라이언트 crash 추적 미확인 |
| health check | Yes | actuator health 구현/공개, 운영 readiness probe 연결 미확인 |
| rollback 전략 | Not Verified | runbook 이미지 rollback/추가 migration, 실제 복구 훈련 없음 |
| dependency vulnerability 검토 완료 | No | 취약점 DB scan 미완료 |
| 결제 보안 검증 | **Not Applicable** | 현재 미구현 |

### 의존성·성능·운영 보충

잠금 파일 기준 React/ReactDOM 19.3.0, React Router 8.4.0, Vite 7.3.6, Vitest 4.1.11, Capacitor 8.5.2, livekit-client 2.22.3, secure-storage-plugin 0.13.0이다. backend는 Boot 4.1.1, LiveKit server 0.16.0, Firebase Admin 9.7.0 등 POM/BOM으로 관리한다. [npm 설치 트리](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/npm-dependencies.json>), [Maven 트리](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/maven-dependencies.txt>). semver range가 있어도 npm lock+CI npm ci가 재현성을 제공한다. 최신이 아니거나 range라는 이유만으로 CVE를 주장하지 않았다. 의심스러운 패키지나 실제 침해는 확인하지 못했으나 전체 패키지 소스 감사도 수행하지 않았다.

두 앱의 lazy LiveKit chunk는 약 **561KB / gzip 148KB**다. 500KB 경고가 있으나 live 진입 시 dynamic import이므로 초기 번들 전체라고 해석하면 안 된다. 생성 dist 총량 고객 약 **6.12MB**, 매니저 약 **5.75MB**, `.map` 0개. 실제 cold start/LCP/메모리/네트워크 부하 측정은 미수행이다. 명확한 전역 무한 render loop는 발견하지 못했다. 대부분 폴링 훅은 cleanup/visibility 처리가 있으나 모든 화면의 장시간 실기기 soak test를 한 것은 아니다.

복구 문서는 존재하지만 backup 일자·성공 restore 증거·RPO/RTO가 없다. 동일 DB에 이전 이미지만 돌리는 rollback은 신규 migration과 호환돼야 한다. 운영 승인 전 staging 복구 훈련과 DB/storage 삭제·미디어 보존 정책을 확인해야 한다. 개인정보 보존의 법적 적정성은 이번 기술 감사에서 판단하지 않았다.

## 10. 우선 수정 순서

1. **H-02 미디어 차단 설계와 검증.** 주거 영상은 노출 뒤 되돌릴 수 없고 현재 서비스의 핵심 보호 약속에 직접 영향이 있다. reconnect/mute 실패/기존 토큰/녹화를 모두 확인해야 한다.
2. **H-01 실제 Postgres 재검증 및 M-03 폐기 정책.** 동일 토큰 소비 수정은 적용했으므로 경쟁 테스트와 외부 장애 복구를 먼저 통과시키고, 비밀번호 변경 뒤 과거 세션 접근을 검증한다.
3. **M-06 탈퇴 경합·M-04 미연결 파일 정리.** 사용자 삭제 요청과 새 데이터 생성이 충돌하지 않아야 하며 미제출 사진까지 정리해야 한다.
4. **M-05 남용 방어·M-07 제재 재검사·M-10 prod 가드.** 실제 공개 직후 악용/오설정으로 서비스가 무너지는 경로를 줄인다.
5. **M-02 파일 검증, M-08 트랜잭션/외부 호출, M-09 pagination·batch 조회.** 입력 무결성과 장기 운영 부하를 개선하고 취약점 DB scan·운영 복구/headers/모니터링 증거를 확보한다.

M-01은 수정했으므로 실기기 Keychain/Keystore 이전·오프라인 로그아웃 확인만 남는다. 결제 미구현은 수정 우선순위에 넣지 않았다.

## 11. 배포 차단 항목

**현재 구현 기능의 배포를 승인하지 않는다.**

- **확인된 차단 결함:** H-02. 일시정지/재접속 동안 미디어 접근·녹화 차단을 보장하지 못한다.
- **수정 검증 게이트:** H-01 조건부 UPDATE의 실제 Postgres 동시 소비, 가입/재설정 실패 복구 테스트. 현재 로컬 mock 회귀 통과만으로 완결로 간주하지 않는다.
- **운영 승인에 필요한 미확인 증거:** 전체 IT/migration, 실제 provider·private storage·TLS/header·trusted proxy 설정, 취약점 DB scan, backup restore, 알림/장애 시연. 이들을 확정 취약점으로 중복 집계하지 않았다.

H-02에 대한 단순 화면 가림/상태값 변경은 승인 조건이 아니다. 라이브를 배포 범위에서 제외하려면 서버/라우트/화면까지 명시적으로 차단한 별도 범위를 다시 평가해야 하며, 이번에는 사용자의 현재 기능 범위를 임의로 축소하지 않았다.

## 12. 향후 구현 시 별도 재검사가 필요한 기능

- PG 결제, 포인트 충전, 결제 승인·환불·billing key·webhook·결제 멱등성. 현재 interface/noop/TODO뿐이며 **이번 점수·배포 차단 원인에 포함하지 않음**. 구현 후 반드시 별도 보안 감사를 수행한다.
- 실제 송금/정산 자동화. 현재 작업 기반 원장·조회와 구분하고 외부 송금 권한·재처리 설계를 별도 감사한다.
- 소셜 OAuth, 휴대폰 번호 변경, 실주소 검색·지도/거리 API 등 현재 미래 항목으로 남은 외부 연동. 구현 시 state/redirect/계정 연결·URL 검증·비용 상한을 확인한다.
- 관리자 UI, 다중 인스턴스 운영. SQL 운영을 UI로 바꿀 때 관리자 역할/감사 로그를, 수평 확장 전에는 분산 스케줄링·중복 전달·공유 rate limiter를 재검사한다.

### 다음 검증의 구체적 테스트 케이스

| 테스트 | 입력/경합 | 기대 결과 | 현재 상태 |
|---|---|---|---|
| OTP 동시 소비 | 같은 token 두 스레드 | 정확히 한 번 성공, 외부 변경도 한 번 | IT 추가·컴파일, 미실행 |
| OTP stale peek | peek 성공 후 소비 경쟁 패배 | 외부 create/update/delete 호출 0회 | 수정 전 실패 → 수정 후 통과 |
| native 저장/로그아웃 | secure delete 실패·앱 재시작 | 평문 토큰 없음, 옛 세션 복원 없음 | 두 앱 6개 통과 |
| 미디어 일시정지 | 새 track 재연결 + mute 502/timeout | paused 동안 viewer/recording에 프레임 0개 | **미충족, 실영상 미검증** |
| 계정 삭제 vs 예약 생성 | 가드 후 동시 create | 신규 예약 또는 탈퇴 중 한쪽 거부 | 미실행 |
| BOLA 전체 경로 | 사용자 A 토큰으로 B의 ID | 조회/수정/삭제 404, 외부 URL 발급 없음 | 기존 주요 테스트 있음, 전체 운영 미실행 |
| 관리자 직접 호출 | 무토큰·오토큰·일반 JWT | 모두 거부, 유효 admin만 허용 | 로컬 필터/인증 테스트 통과 |
| 업로드 위장 | HTML/SVG/임의 bytes + image/jpeg | 내용 검증에서 거부, Storage 호출 0회 | **현행 미충족** |
| 고아 파일 탈퇴 | upload 후 예약 없이 탈퇴 | 파일 원장/Storage 모두 삭제 또는 삭제 큐 | **현행 누락** |
| 제재 직후 수락 | pending offer + 미래 suspendedUntil | 수락 거부, 배정·outbox 없음 | **현행 재검사 누락** |
| outbox 재처리 | 일부 전송 성공 뒤 DB/handler 실패 | 업무 1회, 재전송 수신자 중복 제어 | 단위 일부, 실환경 미검증 |
| 운영 복구 | staging 백업→빈 DB/storage 복원 | migration·예약·서명 URL 정상 | 미실행 |

## 13. 최종 평가

현재 수정된 코드와 남은 구현 결함을 기준으로 한 정성적 위험도다. 결제/포인트 미구현 및 결제 테스트 부재는 제외했다. 미검증 항목이 많으므로 정밀한 확률 점수나 CVSS로 해석하지 않는다.

| 영역 | 위험도 |
|---|---:|
| 보안 위험도 | **7/10** |
| 데이터 무결성 위험도 | **6/10** |
| 장애 위험도 | **6/10** |
| 성능 위험도 | **5/10** |
| 유지보수 위험도 | **4/10** |
| 전체 위험도 | **7/10** |

**최종 상태: NOT READY**

라이브 일시정지 후 재접속에서 개인정보 보호를 보장하지 못한다.  
인증 토큰 경쟁과 네이티브 저장 문제는 수정하고 로컬 회귀를 통과했다.  
예약/탈퇴 경합, 파일 정리·검증, 남용 및 제재 재검사 문제는 남아 있다.  
실제 DB 통합·영상·배포 환경·의존성 취약점 대조는 추가 증거가 필요하다.  
결제 기능 미구현은 이 판단의 사유가 아니다.
