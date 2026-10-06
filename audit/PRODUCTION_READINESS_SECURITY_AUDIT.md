# Production Readiness & Security Audit — 재점검

검사일: 2026-09-24 (Asia/Seoul)
기준: `9b131e4` + 이번 작업 트리 수정
판정: **NOT READY** — 라이브 일시정지의 재접속 시 미디어 차단이 아직 완결되지 않았다.

이 문서가 현재 판정이다. [1차 보고서](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/PRODUCTION_READINESS_SECURITY_AUDIT_INITIAL.md>)는 `488c9eb` 시점의 이력이며, 그때의 발견 건수·테스트 수를 현재 상태로 해석하면 안 된다.

## 1. Executive Summary

**결제 기능은 현재 미구현 상태이며 이번 배포 평가 범위에서 제외됨.** 포인트 충전·PG·환불·송금 미구현 및 관련 테스트 부재는 위험도에 반영하지 않았다. 구현된 예상 정산 원장·조회만 업무 데이터로 검사했다.

현재 기능: 고객·매니저 휴대폰 인증/가입/로그인/재설정/탈퇴, 프로필·주소·주거·알림, 청소·정기청소·수리 예약/견적/변경/취소/사진, 매칭·오퍼 수락/거절, 작업 상태·체크리스트·근무시간·패널티·예상 정산, 라이브/녹화, 문의/공지/FAQ, 푸시 및 관리자 기기/문의 API. React/Vite/Capacitor 두 앱, 공통 UI, Java 21/Spring Boot/JPA/Flyway/PostgreSQL, Supabase·LiveKit·Solapi·FCM 연동이다.

[API 목록](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/API_INVENTORY.md>)을 현재 Controller **92개 메서드 매핑**(결제 stub 1개 포함)으로 다시 생성했다. Actuator health는 별도다. DB migration은 V029까지 존재한다. 1차 구조검사 위에서 이후 수정 커밋과 인증·미디어·파일·프로필·API 재시도 흐름을 집중 재검토했다. 모든 화면을 이번에 수동 E2E로 다시 조작한 것은 아니다.

### 이번에 재현하고 수정한 내용

- **M-11 신규:** A 계정 요청의 늦은 401 또는 멱등성 재시도가 B 계정 토큰으로 전송된다. 늦은 성공 응답도 B 화면/프로필로 전달될 수 있었다. 두 앱에 로그인 세션 세대 검사를 적용했다.
- **M-01 재개방 후 수정:** 평문→보안 저장소 이전이 로그아웃 삭제와 다른 큐를 써서, 늦은 이전 저장이 이전 토큰을 복원했다. 이전도 같은 큐에서 실행한다.
- **H-02 추가 완화:** 재조정기가 mute 미확인 상태에서 새 녹화를 시작하는 경로를 차단하고, PAUSED/중단 요청 중 새 고객 시청 토큰을 거부한다. 기존 참가자/녹화의 재접속 보호는 **미해결**이다.
- **M-02 추가 수정:** PNG의 unsigned 32-bit 가로·세로 곱이 signed long에서 overflow해 픽셀 상한을 우회했다. 곱셈 대신 나눗셈으로 상한을 비교한다.
- **L-02:** 진행 문서의 실제 개발 테스트 계정 비밀번호 1개를 삭제했다. 과거 Git 이력에 남은 값의 폐기는 아직 필요하다.

### 발견 건수와 TOP 5

1차의 모든 이슈를 다시 신규로 세지 않았다. 아래는 이번에 신규/재개방 또는 잔존으로 다룬 고유 원인이다. 운영 미검증 항목은 취약점 건수에 넣지 않았다.

| 심각도 | 이번에 다룬 건수 | 현재 잔존 |
|---|---:|---:|
| Critical | 0 | 0 |
| High | 1 (H-02) | 1, 일부 완화 |
| Medium | 5 (M-01, M-02, M-04, M-09, M-11) | 2 (M-04, M-09) |
| Low | 2 (L-02, L-03) | 2, L-02는 문서만 정리 |

남은 우선순위: ① 일시정지 영상 재노출 ② 사진 정리/첨부 경합으로 데이터 유실 ③ 무제한 목록과 후보별 조회의 성장 위험 ④ 기록된 개발 비밀번호 폐기 ⑤ 주소 동시 변경/상한 경합.

### 실제 실행 결과

| 검증 | 결과 | 증거·한계 |
|---|---|---|
| 프론트 테스트 | **133 files / 301 tests PASS** | [실행 로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-frontend.log>) |
| 백엔드 verify | **63 suites / 357 tests PASS**, package/JaCoCo 포함 | [실행 로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-backend-final.log>), [XML 집계](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-test-summary.json>) |
| ESLint | PASS | [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-lint.log>) |
| 디자인 token 검사 | PASS, 455 files | [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-tokens.log>) |
| 두 앱 production build | PASS | [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-build.log>). LiveKit chunk 561 kB 경고는 남음 |
| mock 계약 snapshot | PASS, 생성 후 변경 없음 | [로그](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-shapes.log>) |
| 실제 PostgreSQL IT | **NOT RUN** | Docker 없음. `-DskipITs`; IT 소스 컴파일과 실행을 구분함 |
| Type check | NOT APPLICABLE | JS/JSX 프로젝트 |
| Secret 검사 | 로컬 환경 secret 3개와 추적 파일 1,515개·dist 580개 비교: 일치 0 | 별도 문서 검사에서 개발 비밀번호 1개 발견/삭제. [기록](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-secret-scan.json>); Git 전체 이력은 미검사 |
| Dependency CVE | **NOT VERIFIED** | 이번 실행에서 npm/Maven 온라인 취약점 DB 대조 안 함 |

첫 백엔드 실행은 코드 테스트 1개가 `127.0.0.1` 모의 HTTP 서버를 열 때 sandbox의 `Operation not permitted`로 실패했다. 해당 테스트가 외부 LiveKit을 부르지 않는 것을 확인하고 자동 승인된 권한 확대로 offline verify를 다시 실행해 통과했다. 테스트용 `.env` import 경로를 따로 지정했고 실제 DB·SMS·메일·푸시·과금·운영 파일 삭제는 실행하지 않았다.

기존 PROGRESS의 실서버 E2E·npm audit 결과는 다른 실행의 기록이며 이번 재검증 결과로 전용하지 않았다. 1차 npm audit 네트워크 확대는 **의존성 메타데이터의 외부 전송을 이유로 자동 승인 검토가 거부**했다. 일반 재점검 요청을 그 전송 승인으로 해석하지 않았다.

## 2. Critical — 즉시 수정

확인된 Critical **0개**. 운영 환경을 포함한 보안 인증을 의미하지 않는다.

## 3. High

### [H-02] 일시정지 후 재접속이 Ingress 잠금보다 빠르면 기존 시청자·녹화에 영상이 노출될 수 있음

**상태:** CONFIRMED — 코드의 허용 상태 및 테스트 재현. 실제 미디어 패킷·노출 길이는 NEEDS VERIFICATION. **일부 완화, 미해결**.
**위험도:** High.
**위치/API:** [LiveService.pause:142](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveService.java:142>), [cameraAttached:329](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveService.java:329>), [lockIngress:352](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveService.java:352>), [LiveReconciler:179](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveReconciler.java:179>). `/live/sessions/{id}/pause`, ingress webhook, 재조정기.

**문제/조건:** 추가된 Ingress 잠금은 유효한 개선이지만 카메라가 송출 중일 때는 적용하지 않고, 끊김 webhook 또는 다음 재조정 때 시도한다. 카메라가 먼저 재접속하면 새 트랙은 LIVE로 들어온 뒤 사후 mute된다. mute 실패 시 `mute_pending`만 남는다. 잠금 실패 때 카메라 없는 pause는 여전히 성공할 수 있다. 이미 연결된 고객과 발급된 시청 토큰, 실행 중 Egress는 새 토큰 API의 제한만으로 폐기되지 않는다.

**영향:** 중단 시간의 주거 공간/매니저 영상이 기존 고객 시청·녹화 경로로 나갈 수 있다. 임의의 제3자에게 공개되는 IDOR라고 주장하지 않는다. 시청자가 해당 집 고객이라도 '촬영 중단'의 개인정보 보호 보장이 깨지므로 차단 사유를 유지한다.

**재현/이번 수정:** 기존 `카메라_재접속은_mute_가_확인된_뒤에만_paused_트랙이_없으면_live_가_아니다`는 PAUSED 예약의 새 트랙이 mute 미확인 시 LIVE + pending임을 계속 확인한다. 추가한 `재조정기도_mute_미확인_세션의_녹화를_시작할_수_없다`와 `일시정지_및_mute_미확인_동안_새_시청_토큰을_주지_않는다`는 수정 전 **2개 실패**, 공통 `ensureRecording` 및 `viewerToken` 수정 후 통과했다. [수정 전](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-live-before.log>), [현재 테스트](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/test/java/kr/ansimclean/api/live/LiveServiceTest.java:332>).

**필요한 최종 수정/검증:** pause 응답 전에 미디어 계층에서 새 트랙 발행·기존 구독·녹화까지 차단을 확인하고, 명시적 resume 뒤에만 복원하는 상태 전이가 필요하다. 격리된 방/합성 영상으로 기존 시청자 연결 유지, RTMP 빠른 재접속, 잠금 API 실패·webhook 지연, 이미 실행 중 Egress를 함께 검사해야 한다. 이번 변경을 이 보장 전체의 해결로 표시하지 않는다. 방/Ingress 강제 삭제로 임의 대체하면 고정 바디캠 키 및 재개·녹화 계약을 깨뜨리므로 적용하지 않았다.

공식 API는 `enabled=false`가 새 연결을 거절하는 속성이라고 설명한다. 이것이 애플리케이션의 잠금 호출 **전** 시간 구간까지 보호한다는 의미는 아니다. [LiveKit Ingress API](https://docs.livekit.io/reference/other/ingress/api/), [공식 protocol](https://github.com/livekit/protocol/blob/main/protobufs/livekit_ingress.proto).

H-01(OTP 소비 전 외부 변경)은 현재 코드에서 토큰 조건부 UPDATE/선소비 순서를 유지한다. 관련 단위 테스트는 통과했고 `OtpConsumptionIT`의 실제 DB 경쟁 검증은 아직 미실행이다. 이미 수정된 H-01을 새 High로 중복 계산하지 않았다.

## 4. Medium

### [M-11] 계정 전환 뒤 이전 요청이 새 계정으로 재시도됨 — 수정 완료

**상태:** CONFIRMED, 동일 시나리오 재검증 PASS. **위험도:** Medium.
**위치:** 고객 [http.request:84](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/customer/src/api/http.js:84>), 매니저 [http.request:84](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/manager/src/api/http.js:84>), [회귀 테스트](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/customer/src/api/http.account-race.test.js:1>). 모든 공통 HTTP 요청에 적용.

**문제/공격·장애 조건:** A로 POST 문의/예약 → 첫 응답이 늦음 → 같은 앱에서 B 로그인 → A의 401 처리 시 현재 B의 refresh token을 읽어 갱신하고 A의 본문을 B로 재전송. 409 IN_PROGRESS의 1초 재시도도 현재 토큰을 읽었다. 성공 응답에는 계정 경계 검사 자체가 없어 A의 `/auth/me` 응답을 B의 프로필로 적용할 수 있었다.

**영향:** 공유 기기/계정 전환에서 본문과 계정이 섞여 B 명의 쓰기 또는 A 데이터 표시. 서버의 소유권 검사가 임의 userId에 우회되는 문제와는 구분한다.

**재현:** 두 앱 각각 늦은 401, 늦은 200, 409 재시도 중 계정 변경의 3개 테스트 — 수정 전 **6개 실패**. [수정 전](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-http-before.log>).

**수정:** `session.set/clear` 때 로그인 세대를 증가시키고, 요청/응답/갱신/재시도가 시작한 세대를 유지한다. refresh는 `renew`를 통해 같은 세대의 토큰만 교체한다. 계정마다 refresh 진행 상태를 구분한다. 이전 요청은 401로 끝내며 현재 세션은 지우지 않는다. 정상 동시 요청 2개는 여전히 한 번만 refresh하는 회귀 테스트도 두 앱에 추가했다. 추가 추적에서 로그아웃 어댑터도 push 삭제/로그아웃 응답 뒤 새 계정을 다시 로그아웃시킬 수 있음을 확인했다. 두 앱에서 총 4개 재현 테스트가 수정 전 실패했고, 어댑터에도 같은 세대 검사를 적용했다. [로그아웃 수정 전](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-logout-before.log>) 전체 301개 PASS.

### [M-01] 보안 저장소 이전과 로그아웃 삭제의 순서 역전 — 수정 완료

**상태:** CONFIRMED, 재검증 PASS. **위험도:** Medium.
**위치:** 고객 [persistNative:35](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/customer/src/api/session.js:35>), [hydrate:86](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/customer/src/api/session.js:86>), [매니저 대응 파일](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/manager/src/api/session.js:35>), [네이티브 테스트](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/apps/customer/src/api/session.native.test.js:48>).

**문제/조건:** 1차 수정은 로그인/refresh/삭제를 직렬화했으나 평문 이전의 `secure.set`은 큐 밖이었다. 이전 set 대기 → clear/remove 완료(pending 제거) → 늦은 set 완료 → 재실행 순서에서 이전 access/refresh가 복원된다. 실제 앱은 초기 hydrate 동안 라우터를 기다리므로 일반 화면 버튼으로 쉽게 일어나는 것으로 과장하지 않는다. 저장 계층의 경합을 모의 저장소에서 재현했다.

**영향:** 로그아웃 뒤 로컬 이전 세션 복원. 서버가 이미 폐기한 JWT를 다시 유효하게 만드는 우회라고 주장하지 않는다.

**수정/재검증:** 이전 저장도 동일 큐에 넣고, 오래된 이전 실패가 새 로그인 메모리를 지우지 않도록 revision을 확인한다. 두 앱 신규 2개 테스트 수정 전 실패/후 통과. [전](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-native-before.log>), [후](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-auth-after.log>). 실기기 Keychain/Keystore 잠금·강제종료는 미검증이다.

### [M-02] 이미지 픽셀 수 검사 overflow — 수정 완료, 구조 검증 한계 명시

**상태:** CONFIRMED, 재검증 PASS. **위험도:** Medium.
**위치:** [RepairPhotoService:58](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/RepairPhotoService.java:58>), [ImageBytes](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/ImageBytes.java:1>), [회귀 테스트](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/test/java/kr/ansimclean/api/booking/RepairPhotoServiceTest.java:94>). `POST /customer/repair-photos`.

**문제/재현:** 1차 이후 서명/치수 검사는 추가됐지만 PNG 헤더의 너비·높이 `0xffffffff` 각각을 곱하면 signed long이 음수가 돼 1억 화소 제한을 통과한다. 위조 헤더로 `png(-1,-1)` 업로드 대역 호출을 테스트했으며 수정 전 '예외가 발생하지 않음'으로 실패했다. [증거](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/audit/evidence/recheck-image-before.log>).

**영향:** 잘못된 해상도 데이터가 저장소로 전달되는 검증 우회. 실제 브라우저 OOM/RCE/XSS는 재현하지 않았으며, 해당 잘못된 PNG를 클라이언트 디코더가 거부할 수도 있다.

**수정:** 양수 검사 뒤 `width > MAX_PIXELS / height`로 비교한다. 기존 위장 HTML/임의 바이트 거부와 크기 제한을 유지했다. 백엔드 전체 테스트 PASS.

**한계:** 현재 형식 검사는 magic bytes와 헤더 검사다. 완전한 이미지 디코딩/재인코딩 또는 악성 파일 스캔이 아니다. HEIC는 서명만 검사하고 해상도 상한은 검증하지 않는다. 이미지 처리 파이프라인을 추가할 때 별도 검증이 필요하며, MIME-only였다는 1차 설명을 현재 상태에 그대로 적용하지 않는다.

### [M-04] 사진 정리와 예약 첨부/탈퇴의 경합 — 잔존

**상태:** CONFIRMED(코드상 순서), 실제 DB/Storage 동시 실행은 NEEDS VERIFICATION. **위험도:** Medium.
**위치:** [deleteUnattached:76](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/RepairPhotoService.java:76>), [deleteAllOf:90](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/RepairPhotoService.java:90>), [예약 첨부:180](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingService.java:180>), [attach 쿼리](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/RepairPhotoUploadRepository.java:16>).

**문제/재현 순서:** 24시간 지난 미첨부 사진 목록 조회 → 그 사진 경로를 포함한 예약의 `attach` 커밋 → 정리 작업이 처음 읽은 객체로 Storage/원장 삭제. 후보 재검증·공유 잠금·상태 전이 없이 삭제하므로 정상 예약의 사진도 삭제될 수 있다. 정리가 먼저 끝나도 예약은 attach 영향 행 수를 확인하지 않아 사라진 경로를 저장할 수 있다.

탈퇴와 업로드에도 공통 직렬화가 없다. 원장 저장 → 업로드 지연 → 탈퇴 정리가 Storage/원장 삭제 → 늦은 Storage 업로드 완료 순서에서는 원장 없는 파일이 다시 생길 수 있다. 원장/폴더 전부 삭제하는 기존 개선만으로 이 경합을 없애지는 못한다.

**영향:** 첨부 사진 유실·깨진 링크, 탈퇴 후 사진 잔존. 실제 파일 삭제 실험은 하지 않았다.

**수정 권고:** 업로드/첨부/삭제를 명시적 상태와 원자적 조건으로 조율하고, cleanup이 삭제 후보를 확정한 뒤 첨부는 실패/재업로드를 안내하도록 한다. 업로드 완료 시 탈퇴/tombstone도 재확인한다. Storage 삭제 재시도까지 포함한 IT 필요. 하루 200개 한도 및 첫 실패에 배치 중단이므로 backlog/보존기간 알림도 필요하다. 기존 원장 이전 파일의 호환성을 포함해야 하므로 이번에 즉흥적 데이터 마이그레이션은 적용하지 않았다.

### [M-09] 이력 목록의 무제한 조회·매칭 후보별 조회 — 잔존

**상태:** CONFIRMED(쿼리 구조), 실제 지연/OOM은 NEEDS VERIFICATION. **위험도:** Medium.
**위치:** [BookingService.reservations:209](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingService.java:209>), [SupportService:61](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/SupportService.java:61>). [OfferService.findCandidates:158](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/offer/OfferService.java:158>). 예약·문의 이력과 매칭 후보의 근무시간 조회.

**조건/영향:** 계정/매니저 데이터 증가 시 모든 이력을 메모리와 JSON으로 만들고 후보별 추가 조회를 수행한다. 홈에서 열린 예약만 가져오고 매니저 소개를 한 번에 가져오는 개선은 확인했다. 전체 이력 pagination은 아직 없다.

**재현/권고:** 격리 DB에 사용자 이력 1만/10만 건과 매니저 후보를 생성해 SQL 수·응답 크기·P95·heap을 측정할 것. 현재 실행하지 않았다. cursor pagination과 서버 상한, 후보 일괄 조회를 API/UI와 함께 도입한다. LiveKit 청크 경고는 이것과 별개이며 이미 동적 로딩이므로 초기 전체 번들로 오해하지 않는다.

## 5. Low

### [L-02] 실제 개발 테스트 계정 비밀번호가 작업 문서/Git 이력에 남음

**상태:** CONFIRMED — 본문에서 1개 제거, 기존 값 폐기 NOT VERIFIED. **위험도:** Low(개발 테스트 계정임을 문서가 명시).
**위치:** [PROGRESS:595](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/PROGRESS.md:595>).

이전 실행의 실서버 비밀번호 재설정 결과가 평문으로 기록됐다. 저장소/문서 접근자가 이 계정으로 로그인할 수 있는지는 현재 비밀번호의 유효성에 달렸다. 실제 로그인을 시도하지 않았으며 운영 관리자 credential로 단정하지 않는다. 이번에 본문에서 제거했지만 과거 커밋은 바꾸지 않았다. 해당 개발 계정의 기존 비밀번호를 폐기하고 문서에는 계정 식별자와 검증 결과만 남겨야 한다. 비밀번호 값은 이 보고서와 검사 결과에 복사하지 않았다.

### [L-03] 주소 개수/기본 주소의 동시 쓰기 경합

**상태:** CONFIRMED(공유 잠금·count-then-write 구조), 실DB 동시 재현 NEEDS VERIFICATION. **위험도:** Low.
**위치:** [addAddress:226](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ProfileService.java:226>), [makeDefaultAddress:252](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ProfileService.java:252>), [ProfileRepository](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ProfileRepository.java:19>).

`holdLive`는 공유 잠금이어서 주소 쓰기끼리는 직렬화하지 않는다. 19개에서 주소 2개를 동시에 추가하면 둘 다 20개 상한 검사를 통과할 수 있다. 서로 다른 기본 주소 설정도 경합한다. 기본 주소 유일성은 DB index가 보호하며 제약 위반은 `GlobalExceptionHandler`에서 **409**로 처리한다. 과거 PROGRESS의 '500' 표현을 확인된 현재 동작으로 인용하지 않는다. 사용자 단위 주소 변경을 배타적으로 직렬화하되 예약 생성의 기존 공유 잠금과 lock upgrade/deadlock이 생기지 않도록 전체 호출 순서를 함께 조정하고 IT를 추가해야 한다.

## 6. 잘 구현된 부분 및 이전 이슈 상태

| 기존 항목 | 현재 평가 |
|---|---|
| H-01 OTP 재사용 | 외부 변경 전 원자적 소비. 관련 단위 테스트 PASS, Postgres IT 미실행 |
| M-03 서버 세션 폐기 | `revoked_sessions`, JWT converter/refresh 검사, 비밀번호 변경 직후 폐기 순서 반영. 단위 테스트 PASS |
| M-05 남용 제한 | 예약·문의·OTP 검증 제한 및 메모리 키 상한 추가. 다중 인스턴스에는 공유 제한 필요 |
| M-06 탈퇴/예약 생성 | profile의 공유/배타 잠금으로 같은 행을 사용. 코드 반영 확인, DB 경쟁 IT 미실행 |
| M-07 정지 매니저 오퍼 수락 | 잠근 매니저의 배정 정지를 재검사. 테스트 PASS |
| M-08 outbox 배치 | 이벤트마다 별도 transaction/실패 기록 분리. 한 배치 전체 rollback 문제 개선. 외부 호출 중 한 DB 연결 점유는 계속 용량 검증 필요 |
| M-10 운영 대역 | `ProductionGuard`가 local/test 외 로그 대역·누락 설정을 거부. 테스트 PASS |
| L-01 Actions | 커밋 SHA 고정 및 read-only contents 권한 확인 |

DB role/소유권 기반 인가, DTO 기반 입력, 바인딩 쿼리, OTP 해시·만료/시도 제한, UUID 파일명·사용자별 경로, private bucket 전제의 signed URL, 관리자 필터+서비스 방어, outbox 재시도, ECS 로그/요청 ID, actuator health 제한 등은 유지된다. CI는 Docker IT 실행을 선언하지만 실제 CI 통과 기록을 이번에 확인한 것은 아니다.

세션 폐기 운영 한계도 남긴다: 다른 인스턴스의 폐기는 1분 cache reload까지 지연될 수 있고 폐기 기록은 90일 후 삭제된다. Supabase refresh/세션 최대 생존 설정이 이에 맞는지 확인해야 한다. 비밀번호 재설정의 세션 목록 조회와 외부 비밀번호 변경 사이에 새 로그인이 생기는 경우, 또는 외부 변경 성공 후 로컬 폐기 DB가 실패하는 경우의 전체 계정 무효화는 **NEEDS VERIFICATION**이며 단순 정상 재설정 테스트만으로 보장하지 않는다.

## 7. 기능별 검사 결과

PASS는 실행한 단위·슬라이스와 코드 범위다. 실제 외부 E2E와 구분한다.

| 기능 | 정상 동작 | 보안 | 예외처리 | 동시성 | 테스트 | 상태 |
|---|---|---|---|---|---|---|
| OTP/가입/재설정 | PASS(대역) | 소비 순서 개선 | PASS | IT 미실행 | Auth/Otp | 조건부 |
| 로그인/로그아웃/refresh | PASS | 계정 전환 경합 수정 | PASS | 단일 앱 경합 PASS, 서버 IT 필요 | 신규 12개 포함 | 조건부 |
| 프로필/주소/주거 | PASS | 소유권 검사 | 409 처리 확인 | L-03 남음 | 기존 suite | 개선 필요 |
| 예약/정기회차/변경/취소 | PASS(대역) | 소유권·가격 서버 계산 | PASS | 삭제 가드 IT 필요 | Booking suite | 조건부 |
| 수리 사진 | PASS(헤더 검사) | 상한 overflow 수정 | PASS | M-04 남음 | 사진 suite | 개선 필요 |
| 매칭·오퍼·작업·패널티 | PASS(대역) | 정지 매니저/상태 검사 | PASS | 실제 DB 경쟁 미검증 | 기존 suite | 조건부 |
| 예상 정산 원장/조회 | PASS(대역) | 본인 매니저만 | PASS | DB IT 미실행 | 기존 suite | 조건부 |
| 라이브·일시정지·녹화 | 대역 PASS | H-02 남음 | 재시도 존재 | 실미디어 미검증 | 신규 2개 포함 | **배포 차단** |
| 문의·공지·FAQ·푸시 | PASS(대역) | 소유권·rate limit | outbox | 실제 전송 미실행 | 기존 suite | 조건부 |
| 관리자 기기·문의 | PASS(대역) | admin token 검사 | PASS | 기기 IT 미검증 | 보안/gateway suite | 조건부 |
| 네이티브 세션 | mock PASS | 큐 경합 수정 | fail closed | mock 경합 PASS | 신규 2개 포함 | 실기기 필요 |

## 8. 보안 체크리스트

| 항목 | 결과 | 근거/한계 |
|---|---|---|
| Authentication | PASS | 코드/단위 범위, 운영 IdP 별도 |
| Authorization | PASS | role + 소유권 + 관리자 token, 미디어는 H-02 별도 |
| IDOR/BOLA | PASS | 주요 서비스 경로에서 타인 자원 차단; 모든 ID 조합의 외부 침투시험 아님 |
| JWT/Session | NOT VERIFIED | 앱 경합 수정; 다중 서버/운영 TTL/재설정 장애 시나리오 남음 |
| OAuth | NOT APPLICABLE | 구현 대상 아님 |
| SQL Injection | PASS | 검토한 쿼리 parameter binding |
| XSS | PASS | 검토 범위 React text 렌더링; 운영 웹 CSP 별도 |
| CSRF | NOT APPLICABLE | 세션 cookie 대신 Authorization Bearer |
| SSRF | PASS | 검토 범위 외부 URL은 설정/서버 생성, 임의 URL fetch 경로 없음 |
| Path Traversal | PASS | UUID 경로/소유 prefix 검사 |
| File Upload | NOT VERIFIED | 서명/크기·픽셀 검사 있음, HEIC 전체 구조·decoder/ACL 미검증 |
| File Download | PASS | 소유권·기한 있는 signed URL 코드; 실제 bucket ACL 별도 |
| Secrets | FAIL | 개발 credential 과거 이력의 폐기 확인 필요 |
| Rate Limiting | PASS | 단일 인스턴스 전제 |
| Brute Force | PASS | 로그인/OTP/IP 제한 코드 |
| CORS | NOT VERIFIED | 설정 제한/운영 guard 있음, 운영 주입값 미확인 |
| CSP | NOT VERIFIED | 실제 배포 응답 미확인 |
| Security Headers | NOT VERIFIED | HTTPS/프록시 실제 응답 미확인 |
| Dependency Vulnerability | NOT VERIFIED | 이번 npm/Maven CVE 대조 미실행 |
| Sensitive Logging | PASS | 운영 logging 대역 거부, 요청 본문/Authorization 미기록 범위 |
| Admin Endpoint | PASS | 필터+서비스 token 검사 |
| Error Information Leakage | PASS | 검토 응답은 공통 오류, 제약 세부값 미출력 |
| Mass Assignment | PASS | DTO로 서버 소유 role/가격/소유자/상태 제한 |
| Input Validation | PASS | 검토 범위 입력/파일 상한 포함; 실제 DB 경합 별도 |
| Payment Security | **NOT APPLICABLE** | 현재 미구현 |

추가 미디어 검증: 현재 정책은 '영상만'이며 Egress는 videoOnly다. 그러나 `joinToken`의 canSubscribe는 전체 구독이고, 서버의 트랙 mute/조회는 주로 videoTrack만 사용한다. 오디오를 보내는 실제 기기를 허용하면 라이브 오디오가 차단되는지 **NEEDS VERIFICATION**이다. 클라이언트 소리 버튼 비활성화나 videoOnly 녹화를 서버 오디오 접근 차단 증거로 취급하지 않는다. [gateway:69/206](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveKitCloudGateway.java:206>), [정책](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/docs/domain.md:162>).

## 9. 배포 준비 체크리스트

| 항목 | 결과 | 설명 |
|---|---|---|
| Production build 성공 | Yes | 고객/매니저 |
| 테스트 통과 | Yes | 301 + 357, PostgreSQL IT 제외 |
| Critical 0개 | Yes | 확인된 범위 |
| High 0개 또는 위험 수용 완료 | **No** | H-02; 사용자 위험 수용 없음 |
| secrets 코드에서 제거 | No | 현재 문서는 정리, 과거 개발 credential 폐기 확인 필요 |
| production 환경변수 분리 | Yes | config/profile/운영 guard 코드 |
| DB migration 검증 | Not Verified | V029까지 존재, 이번 실DB 적용/IT 안 함 |
| DB backup 존재 | Not Verified | 복구 훈련 포함 |
| HTTPS 적용 | Not Verified | 실제 edge/네이티브 API 주소 |
| CORS 제한 | Not Verified | 코드 있음, 운영 값 확인 필요 |
| CSP 설정 | Not Verified | 실제 배포 |
| security headers 설정 | Not Verified | 실제 배포 |
| rate limiting | Yes | 단일 서버; 여러 서버는 공유 저장소 필요 |
| 로그 설정 | Yes | ECS·요청 ID·민감 본문 미기록 |
| 모니터링 | Not Verified | 실제 수집/임계값 알림 |
| error tracking | Not Verified | 실제 수신/연동 |
| health check | Yes | endpoint 코드; 외부 의존성 장애 감지 범위 별도 |
| rollback 전략 | Not Verified | 문서와 실제 되돌리기/복구 훈련 구분 |
| dependency vulnerability 검토 완료 | No | 이번 온라인 CVE 대조 안 함 |
| 결제 보안 검증 | **Not Applicable** | 현재 미구현 |

## 10. 우선 수정 순서

1. **H-02:** 미디어 차단을 먼저 완결하고 격리 RTMP/시청자/Egress 재접속 시험. 개인정보 보호 경계이며 앱 표시만으로 검증할 수 없다.
2. **M-04:** 사진 삭제/첨부/탈퇴의 원자적 상태 전이와 경쟁 IT. 조용한 파일 유실·잔존 방지.
3. **L-02:** 기록된 개발 비밀번호 폐기. 현재 문서 삭제만으로 과거 값의 접근을 차단하지 못한다.
4. Docker 환경에서 기존 IT와 추가 경쟁 시나리오, 운영 Supabase 세션 설정/Storage ACL/오디오 비활성 정책 확인.
5. **M-09/L-03:** 이력 pagination·후보 batch 조회 및 주소 동시 쓰기 직렬화. 부하 기준과 데이터량을 정해 확인.

## 11. 배포 차단 항목

**현재 라이브를 포함한 배포는 H-02가 미해결이라 승인하지 않는다.** Ingress 잠금, 새 토큰 제한, 녹화 시작 제한은 부분 완화다. 기존 시청자·기존 녹화에 새 프레임이 전달되지 않는다는 보장/검증은 남아 있다.

운영 DB/Storage/IdP/복구/headers/CVE 미검증을 '취약점 확정'으로 세지 않지만, 프로덕션 승인 증거로도 대신하지 않는다. 결제 미구현은 배포 차단 사유가 아니다. 기능 제외 배포는 서버에서 실제 노출/호출이 차단되는 별도 배포 구성이 확인될 때만 다시 평가할 수 있다.

## 12. 향후 구현 시 별도 재검사가 필요한 기능

결제·포인트 충전·환불·PG webhook·billing key·실제 정산 송금은 현재 미구현/stub 범위로, 현 점수에서 제외했다. 구현 시 금액 검증·서명·멱등성·원장 일관성·중복 처리·복구를 별도 감사해야 한다. 소셜 OAuth/계정 연결, 새 이미지 변환 파이프라인, 공개 웹 운영이 추가되면 해당 인증/미디어/CSP 경계도 재검사한다.

## 13. 최종 평가

점수는 수학적 안전 확률이 아닌 잔존 문제와 검증 공백을 반영한 정성적 위험도(높을수록 위험)다.

| 영역 | 위험도 |
|---|---:|
| 보안 | 6/10 |
| 데이터 무결성 | 5/10 |
| 장애 | 5/10 |
| 성능 | 5/10 |
| 유지보수 | 4/10 |
| 전체 | **6/10** |

**NOT READY**

인증·업로드·운영 가드·outbox는 1차 이후 개선됐고, 이번에 계정/저장소 경합과 이미지 상한 결함도 수정했다.
프론트 301개와 백엔드 357개 테스트, lint/build는 실제 통과했다.
라이브 중단의 재접속 시 기존 시청/녹화 보호는 여전히 배포 차단 수준이다.
사진 수명 경합, 실DB/실기기/운영 설정 검증도 남아 있다.
결제 미구현은 이 판단에 반영하지 않았다.
