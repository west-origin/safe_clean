# API 계약

## 결제 API 추가 (2026-10-17)

기존 결제 구조만 설명은 이번 사용자 구현 요청으로 대체한다. 아래는 `apps/customer/src/api/payments.js`와 동일한 계약이며 상세 운영 절차는 [payments.md](payments.md).

| 메서드 · 경로 | 요청 | 응답 |
|---|---|---|
| POST `/customer/bookings/{bookingId}/checkout` | 필수 `Idempotency-Key`, `{}` | CheckoutResponse |
| GET `/customer/payments/{id}` | — | CheckoutResponse |
| POST `/customer/payments/{id}/confirm` | 필수 `Idempotency-Key`, `{ paymentKey: string(1~200), amount: 양의 정수 }` | CheckoutResponse (`authorized`는 승인 확인 중) |
| POST `/payments/webhooks/toss` | `{ eventType: "PAYMENT_STATUS_CHANGED", data: { orderId } }` | HTTP 200. 공개 경로이나 본문 상태를 신뢰하지 않고 PG 조회 결과만 반영 |

공통 접두사 `/api/v1`. 고객 경로는 ROLE_CUSTOMER + 예약 소유권 검사, 타인 자원은 404.
CheckoutResponse: `{ id, bookingId, orderId, amount, currency: "KRW", status, refundedAmount, clientKey, orderName, payable, refundStatus }`.
`status`: pending/authorized/paid/failed/partially_refunded/refunded. `refundStatus`: null/pending/done.
금액은 `bookings.total_amount`로 고정한다. 카드 원문·시크릿 키·PG paymentKey는 응답하지 않는다. 같은 예약은 실패 확정 전까지 같은 주문번호를 재사용하며, 예약 취소 시 잔액 전체 환불한다. 정기청소도 회차별 결제이며 자동결제는 등록하지 않는다.

> **이 파일이 기준이다.** `BACKEND_PLAN.md` §5 에서 B1(2026-09-19)에 옮겨 왔다. 이후 변경은 여기서 하고, 계약을 바꾸면 백엔드·앱 `api/*.js` 를 같은 커밋에서 바꾼다 (CLAUDE.md §11-2).
> 구현 상태는 맨 아래 "구현 현황". 표시가 없으면 아직 미구현.

## API 계약 (`backend/docs/api.md` 의 초안. 앱 함수 96개 전부 여기 있다)

공통: base `https://<api-host>/api/v1`, 요청/응답 JSON(UTF-8), 인증은 `Authorization: Bearer <accessToken>`. 날짜 `YYYY-MM-DD`, 시각 `HH:mm`, 시각(절대) ISO-8601 UTC. 목록은 배열 그대로(페이지네이션은 정산 목록만 `?limit=&before=`, 상한 100 — 2026-09-22 구현. 공지는 전량).

### 5.0 오류 형식과 코드 (`common/error`)
```json
{ "error": { "code": "BOOKING_NOT_CANCELLABLE", "message": "방문 24시간 전까지만 취소할 수 있어요" } }
```
`message` 는 **화면에 그대로 보여도 되는 한국어**다. `INTERNAL`(500)에는 — 예상하지 못한 예외든 서비스가 직접 던진 `ApiException(INTERNAL)`(설정 누락 등)이든 — `requestId`(8자) 가 붙고 message 는 "잠시 후 다시 시도해주세요 (문의 번호 xxxxxxxx)" 다 — 모든 응답의 `X-Request-Id` 헤더·요청 로그의 `rid=` 와 같은 값이라 사용자가 캡처한 화면으로 서버 로그를 찾는다. `ErrorCode` enum (HTTP 상태 포함):

| code | HTTP | 언제 |
|---|---|---|
| `VALIDATION` | 400 | `@Valid` 실패. `message` 는 첫 필드 오류 |
| `UNAUTHENTICATED` | 401 | 토큰 없음·만료·서명 불일치 (앱은 refresh 1회 후 로그인으로) |
| `FORBIDDEN` | 403 | 역할 불일치 (고객 토큰으로 `/manager/**`) |
| `NOT_FOUND` | 404 | 없는 리소스 **또는 남의 리소스** (존재를 알리지 않는다) |
| `INVALID_STATE` | 409 | 허용되지 않는 상태 전이 (`bookings.status` 기준) |
| `OFFER_EXPIRED` · `OFFER_TAKEN` | 409 | 콜 만료 / 다른 매니저가 먼저 수락 (닫힌 콜의 수락·거절 — 거절은 고객 취소·일정 변경으로 닫힌 콜도 `OFFER_TAKEN`) |
| `BOOKING_NOT_CANCELLABLE` · `BOOKING_NOT_CHANGEABLE` | 409 | 취소·변경 기한 초과. `message` = "방문 N시간 전까지만 취소할 수 있어요" / "…변경할 수 있어요"(N = `app_settings.cancel_deadline_hours`, 시드 24 — 설정을 바꾸면 문구도 따라간다, 문제 목록 25번) |
| `SLOT_UNAVAILABLE` | 409 | 날짜·시간 선택 불가 (주말·과거·마감) |
| `PHONE_TAKEN` | 409 | 가입 시 이미 있는 번호 |
| `PHONE_NOT_FOUND` | 404 | 비밀번호 재설정 시 가입되지 않은 번호 |
| `LOGIN_FAILED` | 401 | 번호/비밀번호 불일치 (앱 문구 "휴대폰 번호 또는 비밀번호를 확인해주세요") |
| `UNAUTHENTICATED` (프로필 없음·탈퇴) | 401 | 유효한 토큰이어도 profiles 가 없거나 deleted_at 이 있으면 401 (§7.2) — 앱은 401 에서만 로그아웃한다 |
| `OTP_INVALID` · `OTP_EXPIRED` · `OTP_RATE_LIMITED` | 400/400/429 | 인증번호 |
| `PASSWORD_TOO_SHORT` | 400 | 8자 미만 |
| `PASSWORD_CHANGED` | 409 | 비밀번호 재설정은 됐는데 새 세션 발급만 실패 — "비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요". 앱은 로그인 화면으로 보내 이 문구를 보인다 (자체 점검 #22) |
| `LIVE_NOT_AVAILABLE` | 409 | 진행 중이 아닌 예약의 라이브 |
| `RECORDING_NOT_READY` | 404 | 녹화본 아직 없음 |
| `LIVE_PAUSE_UNCONFIRMED` | 502 | live/pause·resume 이 트랙 mute 를 확인하지 못함 (앱은 다시 시도, 재조정기가 잇는다) |
| `IN_PROGRESS` | 409 | 같은 `Idempotency-Key` 요청을 처리 중 (앱 http.js 가 1초 뒤 한 번 더) |
| `IDEMPOTENCY_MISMATCH` | 422 | 같은 키인데 본문이 다름 |
| `METHOD_NOT_ALLOWED` · `UNSUPPORTED_MEDIA_TYPE` | 405/415 | 잘못된 메서드·Content-Type (앱이 만들 일은 없다) |
| `CONFLICT` | 409 | DB 유니크 제약 위반 — 같은 요청이 동시에 두 번 들어옴 (앱은 목록을 다시 읽는다) |
| `UPSTREAM` | 502 | Supabase/LiveKit/FCM 호출 실패 (원인은 로그에만) |
| `INTERNAL` | 500 | 그 외(예상하지 못한 예외, 가격표·`app_settings` 값 누락 같은 설정 문제). `message` 는 항상 "잠시 후 다시 시도해주세요 (문의 번호 xxxxxxxx)" + `requestId` (위 설명, 문제 목록 30번) |

검증 오류(`VALIDATION`)의 `message` 는 애노테이션의 한국어 문구만 그대로 나간다(필드명 접두사 없음). 한국어 문구가 없는 검증 실패는 "입력값을 확인해주세요".

앱 `http.js` 는 `ApiError { status, code, message }` 를 던지고, 화면의 `useAsync` 가 이미 `ScreenError` 로 보여준다(추가 작업 없음). 폼 화면(로그인 등)은 `code` 로 분기한다.

### 5.1 인증 (`auth/`) — 두 앱 공용. `role` 은 `'customer' | 'manager'`
| 앱 함수 | 메서드 · 경로 | 요청 | 응답 | 비고 |
|---|---|---|---|---|
| `requestPhoneVerification(phone)` | `POST /auth/phone/otp` | `{ phone: "12345678", role }` | `{ sent: true, expiresIn: 300, resendAfter: 60 }` | 앱은 `resendAfter` 초 동안 재전송 버튼을 비활성(카운트다운)한다. 서버가 `+8210…` 로 정규화 → 이미 살아 있는(`deleted_at is null`) `profiles` 가 있으면 role 과 무관하게 `PHONE_TAKEN`(휴대폰 하나 = 계정 하나) → 6자리 난수 생성, `phone_verifications` 에 해시 저장 → `SmsSender.send(phone, "[안심클린] 인증번호 123456 (5분 안에 입력)")`. 같은 번호 60초 1회, 시간당 5회(`OTP_RATE_LIMITED`). local 프로필은 문자 대신 콘솔 출력 |
| `verifyPhone(phone, code)` **(새 함수)** | `POST /auth/phone/verify` | `{ phone, code, role }` | `{ verificationToken, expiresIn: 900 }` | 최신 미만료 레코드와 해시 비교. 틀리면 `attempts+1`, 5회면 `OTP_INVALID` 로 폐기, 만료면 `OTP_EXPIRED`. 번호당 검증 시도 시간당 10회, 같은 IP 시간당 60회(`OTP_RATE_LIMITED`, 감사 M-05). 성공 시 불투명 토큰(32바이트 base64url) 발급. 앱은 가입 초안(`ansimclean.signup`)에 둔다 |
| `saveProfileInfo(profile)` | `POST /auth/signup` (인증 없음) | 고객 `{ verificationToken, name, nickname, password }` / 매니저 `{ verificationToken, name, password }` | `{ accessToken, refreshToken, expiresIn, user: { id, role, name, profileComplete: false } }` | 토큰 유효·미사용 확인 → 비밀번호 8자 미만 `PASSWORD_TOO_SHORT` → 조건부 UPDATE로 토큰 소비를 별도 커밋 → Supabase admin `POST /auth/v1/admin/users { phone, password, phone_confirm: true }` → `profiles` insert(role) → Supabase `grant_type=password` 로 세션 발급. 순서: (a) Supabase 에 같은 phone 의 사용자가 이미 있으면(이전 시도의 잔재) admin 으로 찾아 비밀번호만 갱신해 재사용 (b) 없으면 생성 (c) `profiles` insert — 실패하면 (b)에서 만든 사용자 삭제 (d) 토큰 발급. 소비된 인증 토큰 재사용은 `OTP_EXPIRED`. 외부 장애/DB 실패 뒤에는 새 SMS 인증이 필요하며, 이미 가입이 완료된 경우 로그인으로 이어간다 |
| `saveProfileAddress(address)` | `PUT /customer/profile/address` | `{ roadAddress, detailAddress }` | `{ id, label: "집", roadAddress, detailAddress }` | `addresses` 에 `is_default=true` 로. 도로명주소에서 시·도를 못 읽으면("강남대로 396") `VALIDATION` "주소를 시·도부터 입력해주세요. 예: 서울 강남구 강남대로 396" — 매칭 후보가 0명이 된다(직접 사용 점검 U1) |
| `getSuggestedNotices()` | `GET /customer/profile/suggested-notices` | — | `{ notices: ["pet"] }` | 예약 주의사항 화면이 미리 켜 둘 칩. 지금은 가입 때 받은 반려동물 여부뿐 (사용성 검토 2026-09-22). 실패하면 앱은 빈 목록으로 본다 |
| `saveProfileHousing(housing)` | `PUT /customer/profile/housing` | `{ housingType, size, sizeUnknown, rooms, bathrooms, pet }` | 같은 모양 | |
| `agreeTerms(ids)` / `agreeManagerTerms(ids)` | (로컬 초안) → `completeSignUp` 때 전송 | — | — | 사용자가 아직 없을 때 체크하므로 앱이 `sessionStorage ansimclean.signup.terms` 에 두었다가 완료 때 보낸다 |
| `completeSignUp()` / `completeManagerSignUp()` | `POST /auth/signup/complete` (Bearer) | `{ agreedTermIds: [...] }` | `{ profileComplete: true, user: { id, role, name } }` | 필수 약관 누락 → `VALIDATION`. `term_agreements` 기록, `profiles.signup_completed_at=now`(이후 `profileComplete` 의 유일한 기준), 알림 설정 기본 행. 매니저는 `manager_profiles(approved=false)` + 기본 근무시간 월~금 09–18 생성 |
| `loginWithPhone({ phone, password })` | `POST /auth/login` | `{ phone, password, role }` | `{ accessToken, refreshToken, expiresIn, user: { id, role, name } }` | Supabase `POST /auth/v1/token?grant_type=password`. 실패·역할 불일치 모두 `LOGIN_FAILED`(구분하지 않는다). 시간당 10회 |
| `requestPasswordReset(phone)` | `POST /auth/password/otp` | `{ phone: "12345678", role }` | `{ sent: true, expiresIn: 300, resendAfter: 60 }` | 비밀번호 재설정 1단계. `/auth/phone/otp` 와 **반대로** 그 role 의 살아 있는 계정이 있어야 한다 — 없으면 `PHONE_NOT_FOUND`(404, "가입되지 않은 번호예요"). 확인은 `/auth/phone/verify` 를 그대로 쓴다 |
| `resetPassword({ verificationToken, password })` | `POST /auth/password/reset` | `{ verificationToken, password }` | 로그인과 같은 `{ accessToken, refreshToken, expiresIn, user }` | 비밀번호 재설정 2단계. 8자 미만 `PASSWORD_TOO_SHORT` → 조건부 UPDATE로 토큰 소비를 별도 커밋 → Supabase admin 비밀번호 변경 → 새 세션 발급(바로 로그인) → `auth.sessions` 에서 새 세션을 뺀 이 사용자의 세션을 읽어 `revoked_sessions` 에 적고 → `POST /auth/v1/logout?scope=others` 로 **다른 기기 세션 폐기**(upstream 이 실패해도 옛 토큰은 401, 감사 M-03). 로그인 실패 횟수 한도도 초기화. 이 사용자의 **푸시 토큰도 전부 지운다**(끊긴 기기로 알림이 가지 않게, 이 기기는 새 세션 뒤 앱이 다시 등록, 자체 점검 #29). 새 세션 발급만 실패하면 `PASSWORD_CHANGED` |
| (http.js 내부) | `POST /auth/refresh` | `{ refreshToken }` | 같은 모양 | Supabase `grant_type=refresh_token`. 새 토큰의 `session_id` 가 끊은 세션이면 `UNAUTHENTICATED`(감사 M-03). 실패 → 앱은 로그아웃 처리 |
| `logout()` | `POST /auth/logout` (Bearer) | — | `{ ok: true }` | 그 JWT 의 `session_id` 를 `revoked_sessions` 에 먼저 적고(90일) Supabase `POST /auth/v1/logout`(그 refresh 토큰 폐기). 이후 그 세션의 access 토큰은 만료 전이라도 401, refresh 로 받은 새 토큰(같은 session_id)도 401 — upstream 폐기가 실패해도 서버가 막는다(감사 M-03). 앱은 어차피 토큰을 지운다 |
| (http.js · RequireAuth) | `GET /auth/me` | — | `{ id, role, name, profileComplete }` | 앱 시작 시 1회. 401 이면 로그아웃 |
| `deleteAccount()` **(새 함수, 설정 › 회원 탈퇴)** | `DELETE /auth/account` (Bearer) | `{ password }` | `{ ok: true }` | 앱스토어 심사 필수(계정 생성이 있으면 앱 안에서 탈퇴 가능해야 함). 막히면 `INVALID_STATE` — 고객: 청소가 진행 중인 예약이 있으면 "청소가 진행 중인 예약이 있어 탈퇴할 수 없어요. 청소가 끝난 뒤 다시 시도해주세요"(자체 점검 #17), 끝나지 않은 예약·활성 정기청소가 있으면 "진행 중이거나 예정된 예약이 있어 탈퇴할 수 없어요. 예약을 취소한 뒤 다시 시도해주세요" / 매니저: "맡은 작업이 남아 있어 탈퇴할 수 없어요", 미지급 정산이 있으면 `SettlementDeletionGuard` 문구(`BookingDeletionGuard`·`SettlementDeletionGuard`). 처리: `profiles` 익명화(name '탈퇴한 사용자', phone `deleted-<id>`, `deleted_at`) → 주소·주거·알림·푸시 토큰 삭제, 매니저면 계좌·지역·신원 확인·교육·보험 기록을 비우고 근무시간·근무 불가·패널티 원장 삭제(동의 '보유 기간: 매니저 탈퇴 시까지' 대로, 자체 점검 후속 검토) → Supabase admin `DELETE /auth/v1/admin/users/{id}`. 예약·정산 이력은 남는다(법정 보존). 1:1 문의는 소비자 불만·분쟁처리 기록으로 **탈퇴 뒤 3년 보관 후 삭제**(`inquiries.purge_after`, 매일 04:30 `InquiryRetention`, 사용자 결정 2026-09-27 — 자체 점검 #31). 탈퇴 + 5년 뒤 남은 개인정보(예약 주소·요청 등)는 `DeletedAccountPurger` 가 비운다 — 예약·정산 행은 남는다 (BACKEND_PLAN §13-22) |
| `getTerms()` / `getManagerTerms()` | `GET /terms?audience=customer\|manager` | — | 고객 `[{ id, label, required }]` / 매니저 `{ terms: [...], note }` | 인증 불필요 |
| `getTerm(id)` / `getManagerTerm(id)` | `GET /terms/{id}?audience=` | — | `{ id, title, body }` | |
| `getAuthCopy(screen)` (매니저) | 로컬 유지 (`mock.js AUTH_COPY`) | — | — | 문구는 앱 리소스. 서버 호출 없음 |
| `checkPassword(pw)` | 로컬 유지 (8자 규칙) | — | — | 서버는 `saveProfileInfo` 에서 다시 검사 |
| `signUpWith(method)` | 로컬 유지 | — | — | **소셜은 결정 전까지 전부 `phone` 흐름** (§13-1). `SignUp.jsx` 의 `start()` 가 `method` 를 `'phone'` 으로 보낸다 |
| `getHousingOptions()` | 로컬 유지 (`HOUSING_TYPES`·`PET_OPTIONS`) | — | — | 아이콘 키가 앱 에셋 |

### 5.2 고객 (`/customer/**`, `ROLE_CUSTOMER`)
| 앱 함수 | 메서드 · 경로 | 요청 | 응답 (= mock 모양) |
|---|---|---|---|
| `getHome()` | `GET /customer/home` | — | `{ slides: HOME_SLIDES 모양, upcoming: 예약 카드 1장(아래 `getReservations` 의 한 칸) \| null, changeDeadlineHours: 24 }` (`app_settings.home_slides`). `upcoming` = 가장 가까운 예정 방문. `changeDeadlineHours` = `cancel_deadline_hours` — `upcoming.changeable=false` 일 때 앱 안내 "방문 N시간 전이 지나…" 의 N(문제 목록 25번) |
| `getRepair()` | `GET /customer/repair` | — | `{ hero: REPAIR_HERO 모양 }` (`app_settings.repair_hero`) |
| `getReservations()` | `GET /customer/reservations` | — | `{ header, tabs, reservations: [{ id, tab, category, title, status, date, time, series, recordingReady, changeable, rows: [{label,value}×3] }], changeDeadlineHours: 24 }`. `recordingReady` = 완료 회차의 녹화본 준비 여부("녹화 영상 보기"). `changeable` = 시작 전 ∧ 방문 `changeDeadlineHours` 시간 전(변경·취소 버튼 표시 여부). `changeDeadlineHours` = `cancel_deadline_hours` — 잠긴 카드 안내 "방문 N시간 전이 지나…" 와 정기 회차 취소 시트 "N시간 안에 시작하는 방문은…" 의 N(앱 고정 24 였다, 문제 목록 25번). 예정 탭은 앱이 가까운 순으로 정렬한다. 매칭 실패(`bookings.matching_failed_at`, V015)면 매니저 행 문구가 `cleaner_matching_failed_label`("매니저를 찾지 못했어요 · 가능한 매니저가 생기면 다시 찾아요", V041)로 바뀐다 — 자동 재매칭이 약속 전에 더는 볼 수 없으면(밤·약속 3시간 안, `MatchingRetryWindow`) `cleaner_matching_failed_final_label`("매니저를 찾지 못했어요 · 1:1 문의로 알려주세요", V044). `MatchingFailed` 이벤트(`retryable`·`changeable`)로 고객 푸시가 한 번 가며 가능한 길만 말한다: 둘 다 "가능한 매니저가 생기면 다시 찾아볼게요. 일정을 바꾸면 다시 찾아요"(밤에 바꾸면 아침에 찾으므로 '바로' 는 뺐다, 합친 38건 검토 R15) / 자동만 "…다시 찾아볼게요. 1:1 문의로 알려주셔도 돼요" / 일정 변경만 "다른 날짜·시간으로 바꾸면 다시 찾아요. 1:1 문의로 알려주셔도 돼요" / 둘 다 안 되면 "방문 시간까지 매니저를 찾지 못할 수 있어요. 1:1 문의로 알려주세요"(코드에서 정함, 자체 점검 후속 검토). 밤(서울 21시~08시)에 생긴 매칭은 아침 08:05 까지 미뤄지고(급한 예약 — 미루면 약속까지 3시간 안 — 은 바로, 문제 목록 2번) 그동안 매니저 행은 `cleaner_pending_label` 그대로다. 다시 매칭되는 길: ① **자동** — 매시 05분(08:05~20:05) `MatchingRetry` 가 지금 매칭 구간(마지막 일정 변경·방문 취소 이후)에 이 예약의 콜을 받지 않은 새 후보에게만 새 회차로 보낸다(약속 3시간 전까지, 새 후보가 없으면 아무것도 안 함, 사용자 결정 2026-09-27) ② 고객 일정 변경 ③ 운영자 `POST /admin/bookings/{id}/redispatch`(X-Admin-Token, `{ bookingId, offered }`, 매칭 중인 예약만, 후보 전원에게)(자체 점검 #5·#13). `tab`·`status`·`rows` 는 서버가 §6.1 규칙으로 만든다. `series` = 정기청소 회차(취소 시트 선택지). `title` = 카테고리 이름, 정기청소 회차면 "가정/원룸 청소 · 정기 2/6회"(방문일 순, 취소 회차 포함 — 전체 점검 G2). 목록은 `starts_at` 내림차순 |
| `changeReservation(id, { date, time })` | `PATCH /customer/reservations/{id}/schedule` | `{ date, time }` | 예약 1건 (위 모양). `label` 은 서버가 만든다(앱 인자 무시). 같은 고객 시간당 10회를 넘으면 `RATE_LIMITED`(같은 일정으로 다시 보낸 것은 세지 않는다, 자체 점검 #20) |
| `cancelReservation(id)` | `POST /customer/reservations/{id}/cancel` | `{ reason?, wholeSeries?: bool }` | 예약 1건 — 정기청소면 `wholeSeries=true` 로 시리즈까지 취소(앱의 취소 시트에 "이번 회차만 / 정기청소 전체" 선택을 추가, §9.2-5). 이미 고객이 취소한 예약에 다시 보내면 그 카드를 그대로 돌려준다(응답이 늦은 재시도, 자체 점검 #58 — 시스템 취소된 예약은 `INVALID_STATE`). `reason` 이 시스템 사유(`unassigned`·`not_started`·`series_cancelled`)면 `customer` 로 바꿔 기록한다(#57) |
| `getBookingCategory(c)` | `GET /catalog/categories/{c}` | — | `{ label, promo, promoText? }` · 모르면 404 |
| `getFrequencyOptions()` | 로컬 유지 (`FREQUENCY_OPTIONS`) | | |
| `getTimeSlots()` | `GET /catalog/time-slots?category=&date=` | — | `{ am: [...], pm: [...], unavailable: ["13:00"], bookableDays: 60, changeDeadlineHours: 24 }` (`date` 있으면 마감된 시각 표시 — B3 는 빈 배열, 매니저 가용성 기반은 §13-6). `bookableDays` = 오늘부터 며칠 뒤까지 예약할 수 있나(`app_settings.booking_horizon_days`, V026) — 앱 달력이 그 뒤 날짜를 막고, 예약 생성·변경도 넘으면 `SLOT_UNAVAILABLE` (전체 재점검 B1). 일정 변경은 오늘과 지금 방문일 중 늦은 날부터 센다 — 60일 뒤의 정기 회차도 옮길 수 있게(전체 점검 G3). `changeDeadlineHours` = `cancel_deadline_hours` — 그 안의 시간을 고르면 앱이 "예약한 뒤에는 바꾸거나 취소할 수 없어요" 를 미리 알린다(직접 사용 점검 U2) |
| `getAddressReuseOptions()` | 로컬 유지 | | |
| `searchAddress(q)` | `GET /catalog/address?q=` | — | `{ query, results: [{ id, main, sub }] }` — **주소 API(§13-4) 연동 전에는 서버가 빈 배열** |
| `saveBookingStep()` | 로컬 유지 (초안은 sessionStorage) | | 서버 저장 없음 |
| `getBookingOptions(c)` | `GET /catalog/categories/{c}/options` | — | `{ free: [{key,label,icon}], paid: [{key,label,price:"+3,000원",icon}], freeLimit }` |
| `getNoticeChips(c)` | `GET /catalog/categories/{c}/notice-chips` | — | `{ chips: [{key,label,icon}], copy: NOTES_COPY }` (copy 는 `app_settings.notes_copy`) |
| `getConfirmCopy(c)` | `GET /catalog/categories/{c}/confirm-copy` | — | `{ copy, frequencyDescriptions: { recurring, oneTime }, seriesTermMonths: 6 }` — `seriesTermMonths` = 정기청소 개월 수 = 회차 수(`app_settings.series_term_months`). 주기 화면 안내 "N개월 동안 N회 방문해요" 의 N(앱 고정 6 이었다, 문제 목록 25번) |
| `getSavedAddress()` | `GET /customer/addresses/default` | — | `{ roadAddress, detailAddress }` · 없으면 404 |
| `getBookingQuote(c, draft)` **(새 함수, 확인 화면 진입 시)** | `POST /customer/bookings/quote` | `{ category, frequency, date, time, freeOptions[], paidOptions[] }` | `{ perVisit: { base, options, total, label: "85,000원" }, visits: [{ date, label: "2026년 11월 1일 (일)" }] (정기 4~5개 / 1회 1개), visitsPlanned, totalAmount, totalLabel: "340,000원", paymentNote: "결제는 청소 완료 후 진행돼요" }` — 저장 없음. 확인 화면 "요금" 행(§9.2-7) |
| `createBooking(c, draft)` | `POST /customer/bookings` (+ `Idempotency-Key`) | `{ category, frequency, date, time, roadAddress, detailAddress, freeOptions[], paidOptions[], notices[], request, saveAddress? }` — 청소 예약은 `repairType` 을 보내지 않는다(서버 본문의 `repairType` 은 수리 예약 `createRepairBooking` 용) | `{ id, category, frequency, date, time, roadAddress, detailAddress, serviceLabel, cleaner: "배정 후 알려드려요" /* 매칭 실패면 예약 목록과 같은 안내(cleaner_matching_failed_label·_final_label) */, managerAssigned: bool /* 앱은 문구 비교 대신 이 값으로 배지 */, matchingFailed: bool /* 매칭에 실패해 멈춘 예약(bookings.matching_failed_at, 배정되면 지워진다) — 후보가 없으면 생성 응답부터 true. 밤(서울 21시~08시)에 아침으로 미룬 예약은 아침 매칭(08:05) 전까지는 후보가 없어도 false 이고, 그때 후보가 없으면 true 가 된다(문제 목록 2번, 합친 38건 검토 R9). 수리 완료 화면은 managerAssigned·이 값·cancelled(아래 getBooking) 중 하나가 참일 때만 cleaner 를 보이고 아니면 Figma '배정 중'(문제 목록 8번·합친 38건 검토 R16) */, cancelled: bool /* 취소된 예약 — 생성 응답에서는 늘 false(아래 getBooking) */, seriesId?, visits: [{ id, date, label }], perVisitLabel, totalAmount, totalLabel }` — 서버가 다시 견적을 계산해 저장하므로 앱이 본 견적과 다를 수 없다. 무료 옵션 3개 이상·모르는 key·주말·과거 → `VALIDATION`/`SLOT_UNAVAILABLE`. 같은 고객 시간당 10건 넘으면 `RATE_LIMITED`(429, 같은 키 재시도는 세지 않음). 탈퇴가 먼저 커밋된 계정이면 `UNAUTHENTICATED`(감사 M-05·M-06). 도로명주소에서 시·도를 못 읽으면("강남대로 396") `VALIDATION` "주소를 시·도부터 입력해주세요. 예: 서울 강남구 강남대로 396" — 매칭 후보가 0명이 된다(직접 사용 점검 U1)(한도 검사 전이라 횟수를 쓰지 않는다) |
| `uploadRepairPhoto(file)` **(수리 예약, 2026-09-21)** | `POST /customer/repair-photos` (multipart `file`, 이미지·5MB) | — | `{ path: "<customerId>/<uuid>.<ext>", url }` (ext: jpg·png·webp·heic·heif) — Storage 비공개 버킷 `repair-photos`, url 은 미리보기 서명 URL(24시간). 앱은 이 URL 을 다시 받지 않고, 사진마다 올린 시각을 초안에 두어 24시간이 지나면 초안에서 빼고 "첨부한 사진이 만료됐어요. 사진을 다시 올려주세요" 로 다시 올리게 한다(`repairFlow.js` `PHOTO_TTL_MS`). 예약 본문 `repair.photos[]` 에 path 를 넣는다. 남의 path → `VALIDATION`. 사용자당 시간당 60장 넘으면 `RATE_LIMITED`(429). 형식은 선언한 Content-Type 이 아니라 **파일 내용**(서명 바이트)으로 판정하고 그 형식으로 저장한다 — 이미지가 아니거나 헤더가 깨졌거나 1억 화소를 넘으면 `VALIDATION`(감사 M-02). 탈퇴한 계정이면 `UNAUTHENTICATED`, 올리는 사이 탈퇴 정리가 끝났으면 올라간 파일을 지우고 `UNAUTHENTICATED`(재감사 M-04). Storage 업로드나 미리보기 주소 만들기가 실패하면 `UPSTREAM`(502) "사진을 올리지 못했어요. 잠시 후 다시 시도해주세요" — 녹화 영상 문구가 나가지 않는다(문제 목록 21번) |
| `createRepairBooking(category, draft)` **(수리 예약)** | `POST /customer/bookings` (+ `Idempotency-Key`) | `{ category: "repair", frequency: "oneTime", date, time, roadAddress, detailAddress, notices: [현장 상황 키 parking·elevator·pet·intercom], request, repairType: 수리 종류 키(appliance·plumbing…), repair: { appliance, symptom, applianceKey, symptomKey, detail, photos: [path] } }` — appliance·symptom 은 앱 카탈로그의 표시 문구 그대로(기사 화면), applianceKey/symptomKey 는 통계·매칭용 키(V017) | 위와 같은 `BookingResponse` (금액 0 — 수리비는 현장 진단 후). `frequency` 가 `oneTime` 이 아니면 `VALIDATION`. 첨부 사진이 24시간 정리로 이미 지워졌으면 `VALIDATION` "첨부한 사진이 만료됐어요. 사진을 다시 올려주세요"(재감사 M-04) — 앱은 이 문구로 알아보고(`isPhotoExpiredError`, 문구를 바꾸면 앱 `mock.js` `REPAIR_PHOTO_EXPIRED` 도) 초안 사진을 비운 뒤 증상 상세로 보낸다. 매니저 콜 상세·작업 상세의 요청사항에 "가전: … / 증상: … / 상세 / 사진 N장 첨부" 줄이 앞에 붙는다(`Booking.requestLines`) |
| `getBooking(id)` | `GET /customer/bookings/{id}` | — | 위와 같은 모양 (완료 화면 새로고침용). `cleaner` 는 예약 목록 카드의 매니저 행과 같은 규칙(`BookingMapper.managerLabel`) — 매니저 없이 취소된 예약이면 목록과 같은 "배정 전 취소"(문제 목록 9번). `cancelled: bool` = 취소된 예약인가(status = cancelled, 생성 응답(위)에서는 늘 false) — 수리 완료 화면은 `managerAssigned`·`matchingFailed`·이 값 중 하나가 참이면 Figma '배정 중' 대신 `cleaner` 를 보이고, `matchingFailed`·이 값이면 🔔 배정 안내와 머리글 뒷문장을 숨긴다(합친 38건 검토 R16) |
| `getCompleteRows()` | 로컬 유지 (`COMPLETE_ROWS`) | | |
| `getLiveSession(bookingId)` | `GET /customer/bookings/{id}/live` | — | `{ manager: { name, description, avatar }, startedAt: "2025. 09. 11. 14:32", viewers, status: { title, description }, reassurance, playback: { currentLabel, totalLabel, ratio }, live: { state: "waiting\|live\|paused\|ended", available: bool /* state==live */, recordingReady: bool, recordingPending: bool /* 녹화 진행·업로드 중 — ended 인데 둘 다 false 면 녹화본은 없다, 앱은 폴링을 멈춘다 */, recording: "none\|pending\|ready\|failed" } }` — `status.title` 은 상태별 고정 문구(`waiting` "카메라 연결을 기다리고 있어요" · `live` Figma 문구 · `paused` "촬영이 잠시 중단됐어요" · `ended` "청소가 끝났어요" — 끝난 뒤엔 `ended_ready`/`ended_failed`/`ended_none` 으로 녹화본 상태별 설명, V014). 녹화 실패는 `RecordingFailed` 이벤트로 고객 푸시, `startedAt = bookings.started_at`. **영상만 송출**(결정 §13-23)이라 소리는 없다 — `avatar` 는 서명 URL(없으면 `"managerAvatar"` 키) |
| `getSettings()` | `GET /customer/settings` | — | `{ header, profile: { name: "김하늘 님", phone: "010-1234-5678", avatarSrc }, groups, notifications: { push, marketing } }` |
| `updateNotificationSetting(key, enabled)` | `PUT /customer/notifications/{key}` | `{ enabled }` | `{ key, enabled }` |
| `getProfileForm()` / `updateProfile(form)` | `GET` / `PATCH /customer/profile` | `{ name, nickname }` (`phone` 변경은 OTP 재인증 필요 → §13-7, 지금은 무시) | `{ name, nickname, phone: "12345678" }` |
| `getAddresses()` / `addAddress()` / `removeAddress(id)` / `setDefaultAddress(id)` | `GET` / `POST /customer/addresses` / `DELETE /customer/addresses/{id}` / `PUT /customer/addresses/{id}/default` | `{ label, roadAddress, detailAddress }` | `[{ id, label, roadAddress, detailAddress, isDefault }]` / 1건 / `{ id }` / 바뀐 목록. `isDefault` = 예약 "기존 주소" 로 쓰이는 주소(직접 사용 점검 U15, 남의 주소 id 는 `NOT_FOUND`) (기본 주소 삭제 시 다음 주소가 기본). 20개까지 — 넘으면 `VALIDATION`(예약의 '새 주소 저장' 은 조용히 건너뛴다, 전체 재점검 P4). 도로명주소에서 시·도를 못 읽으면("강남대로 396") `VALIDATION` "주소를 시·도부터 입력해주세요. 예: 서울 강남구 강남대로 396" — 매칭 후보가 0명이 된다(직접 사용 점검 U1) |
| `getPaymentMethods()` | `GET /customer/payment-methods` | — | `[{ id, label, isDefault }]` — `payment_methods` 테이블에서. PG 연동 전에는 행이 없어 `[]` (구조만, §6.4) |
| `getFaq()` · `getNotices()` · `getNotice(id)` · `submitInquiry()` | §5.5 | | |

### 5.3 매니저 (`/manager/**`, `ROLE_MANAGER`)
| 앱 함수 | 메서드 · 경로 | 요청 | 응답 (= mock 모양) |
|---|---|---|---|
| `getHome()` | `GET /manager/home` | — | `{ manager: { name, approved /* 승인만(정지와 별개) */, suspension: null \| "9/29 까지 콜을 받을 수 없어요" /* 콜 배정 정지 중(자체 점검 #11) */ }, today: { dateLabel: "9월 15일 (화)", count }, inProgress: null \| { title, elapsed, since, jobId, status: "inProgress\|paused", startedAt(ISO), pausedSeconds }, newCalls: null \| { count, expiry: "4:32 후 만료", summary }, remaining: [ScheduleItem] }` — §6.6 |
| `cancelJob(id, { reason, detail })` | `POST /manager/jobs/{id}/cancel` (+`Idempotency-Key`) | `{ reason, detail }` | 예약 카드 | 매니저 방문 취소 (docs/manager-policy.md). `detail` 은 선택이고 500자까지(넘으면 `VALIDATION`, 앱 입력란도 `INPUT_LIMITS.jobCancelDetail` = 500). 같은 키 재요청은 저장된 응답(응답이 늦어 다시 눌러도 `NOT_FOUND` 가 아니다, 자체 점검 #44). 예약은 취소되지 않고 `assigning` 으로 돌아가 다시 매칭된다(취소한 매니저는 제외). 고객 푸시는 "매니저 배정이 취소됐어요 · 다른 매니저를 찾고 있어요" — 대신할 후보가 없어 곧바로 매칭 실패면 그 푸시 없이 매칭 실패 안내 하나만(문제 목록 5번). 방문 4시간 전부터는 `INVALID_STATE` + "고객센터로 전화해주세요". 패널티는 구간별(24시간 전 월 2회 무료 → 1점 / 24~4시간 1점), 미방문 3점은 `StaleBookingCleaner` 가 매긴다 |
| `getCancelReasons()` | `GET /manager/cancel-reasons` | — | `[{ key, label }]` | `app_settings.manager_cancel_reasons` |
| `getPenalties()` | `GET /manager/penalties` | — | `{ points, nextThreshold, suspension, items[] }` | MY › 패널티 내역. `suspension` 은 정지 중일 때만 |
| (운영자) | `POST /admin/penalties/{id}/waive` (X-Admin-Token) | `{ note }` (면제 근거, 필수·500자까지 — 원장 `waived_note`, 앱에는 안 보인다) | `{ penaltyId, managerId, points, suspendedUntil }` — `points` = 면제 뒤 누적 점수 글자(`getPenalties` 의 `points` 와 같은 값), `suspendedUntil` = 지금 걸린 콜 배정 정지의 마지막 날 `YYYY-MM-DD`(정지가 없거나 풀렸으면 `null`) | 증빙이 인정된 패널티 면제(docs/manager-policy.md). 원장 행을 `waived` 로 바꾸고, 면제 뒤 누적 점수가 가장 낮은 정지 기준(5점) 아래면 정지도 같은 트랜잭션에서 푼다 — 기준 이상이면 정지 기간은 그대로(줄이지 않는다). 이미 면제된 행은 바꾸지 않고 같은 모양으로 돌려준다. 토큰 틀림 401, 메모 없음 400, 없는 id 404, 회복 기록 409 "무사고 회복 기록은 면제할 수 없어요", 기간이 끝난 기록 409 "기간이 끝난 기록은 이미 점수에 들어가지 않아요" (문제 목록 3번, docs/runbook.md) |
| `getCalls()` | `GET /manager/offers` | — | `{ tabBadge, calls: [{ id, expiry, expiresAt, title, location, pay, eta, note, details[] }], availability: { status, detail }, approved }` — pending 만, 만료 임박순. `details` 는 수락 전에 알아야 할 것(`예상 소요 3시간` · 주거 정보 · `청소 범위: …`, 사용성 검토 2026-09-22). `eta` 는 거리 계산 전까지 `null`(앱이 그 줄을 안 그린다). 미승인(`approved=false`)이면 availability 는 '승인 대기 중'(정지면 '콜 배정 정지 중' + 정지 안내). 정지·미승인·탈퇴 매니저에게는 `calls` 가 비고 `tabBadge` 0 — 정지 전에 받은 콜도 수락할 수 없으니 싣지 않고 서버가 30초 안에 닫는다(문제 목록 6번). `expiresAt`(ISO) 은 앱이 카운트다운을 그리고 싶을 때 쓴다(지금 화면은 `expiry` 만 씀) |
| `getCall(id)` | `GET /manager/offers/{id}` | — | CallItem + `customerRequest` |
| `getRejectReasons()` / `getPauseReasons()` | `GET /catalog/reasons?kind=reject\|pause` | — | `string[]` |
| `rejectCall(id, reason)` | `POST /manager/offers/{id}/reject` | `{ reason }` | `{ id, reason }` — 이미 내가 거절한 콜은 200 + 처음 저장한 사유(멱등). 닫힌 콜은 오류(문제 목록 10번, 사용자 결정 B 2026-09-28): 내가 수락한 콜 `INVALID_STATE` "이미 수락한 콜이에요. 일정에서 확인해주세요" · 다른 매니저 수락·고객 취소·일정 변경·방문 취소로 닫힌 콜 `OFFER_TAKEN` · 만료됐거나 만료 시각이 지난 콜 `OFFER_EXPIRED` → 앱은 뒤 둘에 '이미 마감된 요청이에요' 시트 |
| (CallCard 수락 — 지금은 `navigate` 만) **`acceptCall(id)` 새 함수** | `POST /manager/offers/{id}/accept` | — | `{ jobId }` — 앱은 `/job/{jobId}` 로. `OFFER_EXPIRED`/`OFFER_TAKEN` 이면 목록으로. 콜을 받은 뒤 그 시간에 휴무·근무 불가를 넣었거나 근무시간을 바꿨으면 `INVALID_STATE` "근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요"(콜은 남는다, 문제 목록 7번) |
| `getJob(id)` | `GET /manager/jobs/{id}` | — | `{ id, status, startLabel: "10:00 시작" | "9월 25일 (금) 14:00 시작"(오늘이 아니면 날짜), today: bool /* 작업 날짜가 오늘 — 시작은 당일에만, 아니면 앱이 '현장 도착' 대신 안내 (직접 사용 점검 U9·U11) */, address: [2줄], info: [{label,value}×3], pay, scope[], requests[], photos: [서명 URL(1시간)] }` — `photos` 는 수리 예약의 사진(청소 예약은 `[]`). URL 을 만들지 못한 장은 빠지고 requests 의 "사진 N장" 도 그 수에 맞춘다 |
| `getJobContact(id)` | `GET /manager/jobs/{id}/contact` | — | `{ phone: "010-1234-5678", mapQuery }` — 배정된 매니저에게만, 작업 당일 ±1일에만(그 외 404) |
| `getEquipmentStatus()` | `GET /manager/device/status` | — | `[{ label: "카메라", value: "연결됨"\|"연결 안 됨", status }, { "배터리", "82%"\|"확인 필요" }, { "네트워크", "양호"\|"확인 필요" }, { "실시간 전송", "준비 완료"\|"대기 중" }]` — 카메라·전송은 `manager_devices.stream_status`(Ingress 웹훅), 배터리·네트워크는 `last_report`(기기 API 없으면 "확인 필요", status `issue` 아님 `unknown`→앱은 `issue` 로 표시) |
| `getActiveJob()` | `GET /manager/jobs/active` | — | `{ id, status: "inProgress"|"paused" /* 작업 상태 — 앱 경과 시간은 이걸로 멈춘다(자체 점검 #43) */, title, elapsed, startedAt, pausedSeconds, since, sinceLabel: "14:02부터 진행", streaming: bool, equipment[], requests[], checklist: [{ group, items: [{id,label,done}] }] }` · 없으면 404 |
| `setChecklistItem(jobId, itemId, done)` | `PUT /manager/jobs/{jobId}/checklist/{itemId}` | `{ done }` | `{ jobId, itemId, done }` |
| `startJob(id)` **새 함수** (JobPreparing "청소 시작") | `POST /manager/jobs/{id}/start` | — | `{ jobId, startedAt }` — `scheduled` 이고 작업 날짜가 오늘일 때만. 서버 상태에 `preparing` 은 없다(준비 화면은 앱 화면일 뿐). 라이브는 별도(§5.4) |
| `pauseJob(jobId, reason)` | `POST /manager/jobs/{id}/pause` | `{ reason }` | `{ jobId, reason, since: "14:32부터 중단됨" }` — **선행 조건: `live_sessions.muted=true` 또는 카메라 미연결**. 영상이 나가는 중이면 `INVALID_STATE`("영상을 먼저 중단해주세요") → 순서를 서버가 강제한다. 멱등 |
| `getPausedJob()` | `GET /manager/jobs/active/pause` | — | `{ since, note }` |
| `resumeJob(jobId)` | `POST /manager/jobs/{id}/resume` | — | `{ jobId }` — 멱등. 앱 순서: `resumeJob` → `live.resume()`(영상이 나가는데 상태가 일시정지인 순간이 없게). `live.resume` 이 실패하면 상태는 진행 중, 영상은 막힌 채 → 앱이 문구로 재시도, `LiveReconciler` 도 `in_progress ∧ muted ∧ mute_pending=false` 를 1분마다 unmute 재시도 |
| `completeJob(jobId)` | `POST /manager/jobs/{id}/complete` | — | `{ jobId }` (정산 생성, §6.5) |
| (운영자) | `POST /admin/jobs/{id}/complete` (X-Admin-Token) | — | `{ jobId }` — 매니저가 '청소 완료' 를 누르지 않고 떠난 작업을 닫는다. 매니저 완료와 같은 처리, 이미 끝났으면 그대로. 이력 reason `admin` (자체 점검 #17, docs/runbook.md) |
| `getCompletedJob()` | `GET /manager/jobs/last-completed` | — | `{ title, subtitle, summary: [4], payout: { label, amount, note } }` |
| `getSettlement({ before? })` | `GET /manager/settlements?before=<이전 응답의 nextBefore>&limit=<1..100>` | — | `{ monthly: { label, amount }, stats: [3], items: [{ id, date, status, title, amount }], nextBefore: string\|null }` — `nextBefore` 는 불투명 커서(`<ISO>_<id>`, 생성 시각+id 키셋 — 같은 시각의 정산이 빠지지 않게. 앱은 그대로 돌려보낸다) — 합계는 DB 집계('이번 달' = 정산 생성=작업 완료 시각 기준), 목록은 한 페이지(기본 50). `nextBefore` 가 있으면 앱이 '더 보기' 로 다음 페이지 |
| `getSettlementDetail(id)` | `GET /manager/settlements/{id}` | — | `{ id, title, subtitle, breakdown: [3], final: { label, value } }` |
| `getSchedule()` | `GET /manager/schedule?from=&to=` | — | `{ items: [오늘], week: [{ dateKey, label, items }] }` — 기본 오늘~+6일 |
| `getAvailability()` | `GET /manager/availability` | — | `{ defaultHours: [{ day: "월", hours: "09:00 - 18:00", off? }×7], unavailable: [{ id, date: "9월 18일", label, time }] }` |
| `updateDefaultHours(rows)` | `PUT /manager/availability/hours` | `[{ weekday: 0..6, start: "09:00", end: "18:00", off }]` | `defaultHours` 모양 |
| `getRepeatOptions()` | 로컬 유지 (`REPEAT_OPTIONS`) | | 앱은 `['반복 없음','매주','매월']` ↔ `none\|weekly\|monthly` 로 바꿔 보낸다 |
| `addUnavailable({...})` | `POST /manager/availability/unavailable` | `{ date, allDay, start, end, repeat, reason }` | `{ id, date, label, time, repeat }` |
| `getProfile()` | `GET /manager/profile` | — | `{ name: "김○○ 매니저", stats: [{평점},{완료 청소}], menu: PROFILE.menu 그대로 }`. 평점은 매겨진 값(>0)이 있을 때만, 없으면 "-" — 고객 평가 화면이 아직 없다(고객이 보는 매니저 소개도 같은 규칙, 직접 사용 점검 U14) |
| `getProfileDetail(key)` | `GET /manager/profile/{key}` (info·area·identity·education·account) | — | `{ title, rows? , chips?, note? }` |
| `getEquipmentDevice()` | `GET /manager/device` | — | `{ name, connection: "정상 연결"\|"연결 안 됨", status: [4], stream: { url, keyMasked: "****-**-1234" } }` — 기기 설정용. 기기 미등록이면 404(운영자가 등록, §13-8) |
| `getManagerNotifications()` / `updateManagerNotification()` | `GET /manager/notifications` / `PUT /manager/notifications/{key}` | `{ enabled }` | `{ rows, values }` / `{ key, enabled }` |
| `getSupportCopy()` | 로컬 유지 | | |
| `submitSupportInquiry()` · `getManagerNotices()` · `getManagerNotice()` | §5.5 | | |

### 5.4 라이브 (`live/`) — 앱의 `liveClient.js` 만 부른다. 영상은 **매니저 휴대폰 카메라 → LiveKit**(`LIVE_SOURCE=app`, 기본, 계획 §8.0). 바디캠 → Ingress(`ingress`)는 남아 있지만 쓰지 않는다
| 호출 | 메서드 · 경로 | 요청 | 응답 |
|---|---|---|---|
| 매니저 `start(jobId)` | `POST /live/sessions/{bookingId}/start` | — | `{ roomName, status: "waiting_camera\|live\|paused", cameraOnline: bool, cameraBusy: bool, preview: { url, token } \| null, publish: { url, token } \| null }` — **app 모드(기본): `publish` = 송출 토큰(identity `camera-<managerId>`, 카메라 영상만 publish·구독 불가, 6시간), `preview` null, 기기 행이 없으면 "휴대폰 카메라" 행을 만들고 Ingress 는 건드리지 않는다, cameraBusy 는 늘 false.** ingress 모드: `preview` = 시청 토큰, `publish` null. (앱 liveClient 는 `status` 로 화면 상태를 정한다) — 오늘의 `scheduled\|in_progress` 인 자기 작업만 (Job/Preparing 화면에서 미리 불러 "카메라 연결됨" 을 확인하고, 청소 시작 후에도 같은 세션). `cameraBusy=true` 면 바디캠이 다른 방(대기 방)에 송출 중이라 앱이 "바디캠을 껐다가 다시 켜주세요" 를 보여준다(§8.1). 서버가 그 매니저의 Ingress 를 이 방에 묶고(§8) Egress 녹화를 시작한다. `preview` 는 매니저가 자기 바디캠 화면을 보는 viewer 토큰(TTL 6시간) |
| 매니저 (화면이 3초마다) | `GET /live/sessions/{bookingId}` | — | `{ status: created\|waiting_camera\|live\|paused\|ended, cameraOnline, muted, cameraBusy, viewers, startedAt, held, source: "app"\|"ingress" }` — `source` 로 매니저 앱이 청소 준비 화면에서 휴대폰 카메라 미리보기를 켤지 정한다. 카메라가 붙으면 `live` — app 모드는 웹훅 `track_published`/`track_unpublished`(참가자 `camera-<managerId>`, 영상), ingress 모드는 `ingress_started`/`ended`. `held` = 매니저가 영상을 멈추고 아직 재개하지 않음(`live_sessions.video_held`, V045) — 작업이 진행 중인데 `held` 면 매니저 앱이 '영상은 중단됐지만 작업 상태를 바꾸지 못했어요' 시트를 다시 띄운다(다시 시도 또는 닫기 = 재개) |
| 매니저 `pause()` / `resume()` | `POST /live/sessions/{bookingId}/pause` · `/resume` | — | `{ status, muted: true }` — **영상만 책임진다**(작업 상태는 `job/*/pause`). 서버가 `mutePublishedTrack` 을 부른 뒤 `getParticipant` 로 트랙 `muted` 를 최대 3초 폴링해 **확인된 뒤에만 200**. 확인 못 하면 `mute_pending=true` 로 남기고 `LIVE_PAUSE_UNCONFIRMED`(502) → 앱은 일시정지 화면으로 가지 않는다. 카메라가 안 붙어 있으면(`waiting_camera`) 트랙이 없으므로 `mute_pending=true` 로 즉시 200(막을 영상이 없다). 둘 다 멱등(이미 그 상태면 200). 일시정지는 mute 전에 방의 고객 참가자 구독을 막고(`canSubscribe=false`), 재개는 영상이 다시 나간 뒤 연다(재감사 H-02). **pause 가 200 이면 서버는 매니저가 재개할 때까지(`resume` 또는 `jobs/*/resume`) 영상·고객 시청을 막아 둔다** — `jobs/*/pause` 가 실패하고 바디캠이 끊겼다 다시 붙어도(`video_held`, 2026-09-28 사용자 결정). 502 는 '계속 전송' 안내라 그 표시를 지운다. `resume` 은 예약 → 세션 순서로 잠그고 읽는다 |
| 매니저 `stop()` | `POST /live/sessions/{bookingId}/stop` | — | `{ ok }` — Egress 정지, Ingress 를 방에서 분리(다음 작업에서 재사용), 방 종료 |
| 고객 `join(bookingId)` | `POST /live/sessions/{bookingId}/token` | `{ role: "viewer" }` | `{ url, token, roomName }` — 자기 예약이 `in_progress`이고 세션이 열려 있으며 `paused`·`mute_pending`·`unmute_pending`·매니저가 멈춘 채(`video_held`)가 아닐 때만(`LIVE_NOT_AVAILABLE`). 기존 시청자/발급된 토큰의 권한 철회를 뜻하지 않는다. TTL 2시간, `canPublish=false` |
| 고객 녹화본 | `GET /live/sessions/{bookingId}/recording` | — | `{ url, durationSeconds }` (서명 URL 6시간, `RECORDING_URL_TTL_SECONDS`) · 준비 전 `RECORDING_NOT_READY` |
| 바디캠 기기 | (LiveKit Ingress RTMP 주소 — API 아님) | 기기에 `stream_url` + `stream_key` 한 번 설정 | — |
| LiveKit → 서버 | `POST /live/webhooks/livekit` | LiveKit 웹훅(서명 검증) | 204 — (app 모드) `track_published/unpublished`(참가자 `camera-<managerId>`, 영상) → 카메라 붙음·끊김(아래 ingress_* 와 같은 처리, 기기 행은 '휴대폰 카메라') · (ingress 모드) `ingress_started/ended` → `manager_devices.stream_status`·`live_sessions.camera_online_at`·`status=live`, `egress_ended` → `recording_status=ready`, `participant_joined/left`(identity `customer-`) → `viewer_count`, `room_finished` → `ended` |

### 5.5 지원 (`support/`, 두 앱)
| 앱 함수 | 메서드 · 경로 | 응답 |
|---|---|---|
| `getFaq()` | `GET /support/faq?audience=` | `[{ id, question, answer }]` |
| `getNotices()` / `getManagerNotices()` | `GET /support/notices?audience=` | `[{ id, title, date: "2026.09.10" }]` (audience + all, 최신순) |
| `getNotice(id)` / `getManagerNotice(id)` | `GET /support/notices/{id}?audience=` | `{ id, title, date, body }` — 다른 대상의 공지는 `NOT_FOUND` |
| `submitInquiry({ content })` / `submitSupportInquiry()` | `POST /support/inquiries` (+ `Idempotency-Key`) | `{ id }` — 공백만이면 `VALIDATION`, 2,000자 상한. 같은 사용자 시간당 10건 넘으면 `RATE_LIMITED`(429, 감사 M-05) |
| `getMyInquiries()` | `GET /support/inquiries` | `[{ id, content, status: open\|answered, answer, createdLabel, answeredLabel }]` — 1:1 문의 화면 위쪽 '내 문의'. 답변은 운영자가 `POST /admin/inquiries/{id}/answer` (X-Admin-Token, `{ answer }`) 로 → `InquiryAnswered` 푸시(type `inquiry`, 토글 무관) |
| `registerPushToken({ platform, token })` | `PUT /push/token` `{ platform: ios\|android\|web, token }` | `{ ok }` — 앱 시작·로그인 후. 같은 토큰이 다른 사용자에게 남아 있으면 그쪽에서 지운다(기기 공유) |
| `unregisterPushToken({ token })` | `DELETE /push/token` `{ platform, token }` | `{ ok }` — 로그아웃 직전 (이 기기로 앞 사람의 푸시가 오지 않게). 401 강제 로그아웃(refresh 도 거부)은 로그인이 필요해 이 요청을 보낼 수 없다 — 두 경우 모두 세션이 지워지면 앱(`push/pushClient.js`)이 기기의 푸시 등록을 풀고(unregister) 다음 로그인 때 새 토큰을 받는다. 남은 행은 다음 발송 때 FCM 이 무효 토큰으로 답하면 `PushService.send` 가 지운다 |

푸시 문구·받는 사람(`push/PushCopy`, `PushHandler`): 새 콜(후보 매니저, 설정 `call`) · 매니저 배정(고객) · 청소 시작 = 바디캠 첫 연결(고객) · 완료(고객 + 매니저 `settlement`) · 취소·일정 변경(배정됐던 매니저) · 미배정 취소(고객) · 정기청소 종료 7일 전(고객) · 바디캠 끊김/5분 대기(매니저) · 녹화본 준비(고객). 알림 설정 `push` 가 꺼져 있으면 보내지 않는다. `data: { type, bookingId }` 로 화면 이동.


## 구현 현황
- ✅ B5: §5.4 전부 — `POST /live/sessions/{id}/start`(같은 세션에 다시 불러도 방은 한 번, `cameraBusy` 면 앱이 3초마다 다시 부른다) · `GET /live/sessions/{id}`(고객·매니저 둘 다, `{ status, cameraOnline, muted, cameraBusy, viewers, startedAt }`) · `POST …/pause|resume`(트랙 muted 를 최대 6×500ms 확인한 뒤에만 200, 못 하면 `LIVE_PAUSE_UNCONFIRMED` 502 + `mute_pending`/`unmute_pending`; 카메라가 안 붙었으면 표시만 두고 200) · `POST …/stop` · `POST …/token`(`in_progress` + 열린 세션 + 영상 중단 요청 없음, 아니면 `LIVE_NOT_AVAILABLE`) · `GET …/recording`(ready 아니면 `RECORDING_NOT_READY` 404) · `POST /live/webhooks/livekit`(서명 실패 401, 처리·중복 모두 204) · `GET /customer/bookings/{id}/live`(§5.2, `manager.description` 은 "경력 N년 · 완료 청소 M건") · `GET /manager/device` 에 `lastOnline` 추가 · **`POST /admin/devices`**(헤더 `X-Admin-Token`, `{ managerId, name, serial }` → Ingress 생성 + 전체 스트림 키 1회 응답, §13-20).
- ✅ B4: §5.3 매니저 전부 — `GET /manager/home` · `GET /manager/offers` · `/offers/{id}` · `POST /offers/{id}/accept`(+`Idempotency-Key`, `{ jobId }`) · `/offers/{id}/reject` · `GET /manager/jobs/{id}` · `/jobs/{id}/contact`(당일 ±1일 아니면 404) · `GET /manager/device/status` · `GET /manager/device`(미등록 404, `online: bool` 원시값) · `GET /manager/jobs/active`(없으면 404) · `/jobs/active/pause` · `/jobs/last-completed` · `PUT /jobs/{id}/checklist/{itemId}` · `POST /jobs/{id}/start|pause|resume|complete`(start·complete 는 `Idempotency-Key`, pause 는 `live` 가 `live` 상태면 `INVALID_STATE` "영상을 먼저 중단해주세요") · `GET /manager/settlements` · `/settlements/{id}`(JSON 키 `final`) · `GET /manager/schedule?from=&to=` · `GET /manager/availability`(`defaultHours[].start/end` = "HH:mm"\|null 원시값) · `PUT /availability/hours`(본문 `[{ weekday 0..6, start, end, off }]`) · `POST /availability/unavailable`(`repeat` none|weekly|monthly) · `DELETE /availability/unavailable/{id}`(204, 내 것만) — 휴무·근무 불가가 앞으로 366일(`LOOKAHEAD_DAYS`) 안의 맡은 작업과 겹치거나, 새 근무시간이 맡은 작업의 **시작 시각**을 벗어나면(`[시작, 종료]` 밖 — 매칭과 같은 기준, 끝나는 시각은 넘어도 된다, 자체 점검 #16 후속) `INVALID_STATE` "그 시간에 맡은 작업이 있어요…" · `GET /manager/profile` · `/profile/{key}` · `GET|PUT /manager/notifications`. 콜 카드 `note` 는 주의사항이 없으면 `null`. 오퍼가 pending 이 아니면 `GET /offers/{id}` 는 `OFFER_EXPIRED`. `getCompletedJob` 의 촬영 기록 tone 은 `default|success|issue`(`issue` 는 앱 SummaryRow 에 추가). `POST /live/**` 는 B5.
- ✅ B3: `GET /customer/home` · `/repair` · `/settings` · `GET|PATCH /customer/profile` · `GET|POST /customer/addresses` · `GET /customer/addresses/default`(없으면 404) · `DELETE /customer/addresses/{id}`(기본 주소 삭제 시 가장 오래된 주소가 기본) · `PUT /customer/notifications/{key}` · `GET /customer/payment-methods`(항상 `[]`) · `POST /customer/bookings/quote` · `POST /customer/bookings`(+`Idempotency-Key`) · `GET /customer/bookings/{id}` · `GET /customer/reservations` · `PATCH /customer/reservations/{id}/schedule` · `POST /customer/reservations/{id}/cancel`(`wholeSeries`). `GET /customer/bookings/{id}/live` 는 B5. 검증 오류 문구: `SLOT_UNAVAILABLE` 은 "내일부터 예약할 수 있어요" / "주말은 예약할 수 없어요" / "선택할 수 없는 시간이에요", `VALIDATION` 은 "무료 옵션은 N개까지 고를 수 있어요" / "알 수 없는 옵션이에요: key" / "알 수 없는 주의사항이에요: key".
- ✅ B1: `GET /catalog/**` 7개, `GET /terms`, `GET /terms/{id}`, `GET /actuator/health`. 오류 형식 §5.0 전체.
- ✅ B2: `POST /auth/phone/otp` · `/auth/phone/verify` · `/auth/signup` · `/auth/signup/complete` · `/auth/login` · `/auth/refresh` · `/auth/logout` · `GET /auth/me` · `DELETE /auth/account` · `PUT /customer/profile/address` · `PUT /customer/profile/housing`. 로그인 식별자는 §7.1.1 A(Supabase phone+password). 가입 중 계정은 `PENDING_*` 권한으로 가입 경로만 통과.
