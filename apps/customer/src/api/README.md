# 고객용 앱 데이터 어댑터

화면은 `api/*.js` 의 함수만 호출한다. 지금은 `mock.js` 의 값을 돌려주고, 실서버가 생기면 함수 안만 바꾼다.
스키마는 Figma 화면에 보이는 값에서 뽑았다(코드에서 정한 항목 — 서버 설계 시 확인).

## 공통
- 모든 함수는 `Promise` 를 돌려준다.
- 개발 중 빈 상태를 보려면 URL 에 `?scenario=empty`, 오류 화면(ScreenError)을 보려면 `?scenario=error` 를 붙인다 (DEV 에서만, 홈·목록 함수에만 걸려 있다).

## 인증 (B2 부터 실서버 — 경로·응답은 `backend/docs/api.md` §5.1)
서버 호출은 `http.js`(Bearer 토큰·401 시 refresh·`ApiError`)를 거치고, 세션은 `session.js`(localStorage `ansimclean.customer.auth`)에 둔다. 화면은 둘 다 모른다.

| 함수 | 서버 | 화면 계약 |
|---|---|---|
| `isSignedIn()` | — | 토큰이 있고 `user.profileComplete` 일 때만 true. 가입 중이면 false |
| `signupStep()` / `SIGNUP_STEP_PATHS` | — | 가입이 덜 끝난 계정의 다음 화면 `'address' \| 'housing' \| 'complete'` → 경로 |
| `signUpWith(method)` | — | 소셜은 아직 없어(결정 §13-1) 항상 `{ method: 'phone' }` |
| `getTerms()` | `GET /terms?audience=customer` | `[{ id, label, required }]` |
| `agreeTerms(ids)` | — (초안 `ansimclean.signup`) | 서버에는 `completeSignUp` 때 보낸다 |
| `getTerm(id)` | `GET /terms/{id}?audience=customer` | `{ id, title, body }` |
| `loginWithPhone({ phone, password })` | `POST /auth/login` (role customer) | 성공 `{ ok: true, user }` + 세션 저장 · 틀리면 `{ ok: false }` (던지지 않음) · 그 외 오류는 던짐 |
| `requestPhoneVerification(phone)` | `POST /auth/phone/otp` | `{ sent: true, expiresIn }` · 이미 가입한 번호 `{ sent: false, reason: 'taken', message }` |
| `verifyPhone(phone, code)` **(B2 추가)** | `POST /auth/phone/verify` | `{ ok: true }` + 검증 토큰을 초안에. 틀리면 `ApiError(OTP_INVALID \| OTP_EXPIRED \| OTP_RATE_LIMITED)` |
| `checkPassword(pw)` | — | 8자 이상 `{ ok, message }` (서버도 다시 검사) |
| `saveProfileInfo({ name, nickname, password })` | `POST /auth/signup` (초안의 검증 토큰) | 계정 생성 → 세션 저장(가입 중). `user` 반환 |
| `saveProfileAddress({ roadAddress, detailAddress })` | `PUT /customer/profile/address` | `{ id, label, roadAddress, detailAddress }` |
| `getHousingOptions()` | — | 아이콘이 앱 에셋이라 로컬 |
| `saveProfileHousing({ housingType, size, sizeUnknown, rooms, bathrooms, pet })` | `PUT /customer/profile/housing` | 같은 모양 |
| `completeSignUp()` | `POST /auth/signup/complete` (초안의 약관 id) | `{ ok, user }` · `profileComplete=true` → `isSignedIn()` true |
| `refreshMe()` | `GET /auth/me` | 앱 시작 시 1회 (`App.jsx`). 토큰 없으면 null |
| `logout()` | `POST /auth/logout` | 실패해도 세션은 지운다 |
| `deleteAccount(password)` **(B2 추가)** | `DELETE /auth/account` | 진행 중 예약이 있으면 `ApiError(INVALID_STATE)` |

화면 테스트는 `src/test/fakeHttp.js` 로 `http.js` 를 바꿔 돌린다 (`vi.mock('../../api/http.js', …)`). 서버 없이 화면을 보려면 `VITE_API_MOCK=1`(DEV 전용, 같은 가짜 서버).

## 고객 데이터 (B3 부터 실서버 — 경로·응답은 `backend/docs/api.md` §5.2)
| 함수 | 서버 | 화면 계약 |
|---|---|---|
| `getHome()` / `getRepair()` | `GET /customer/home` / `/repair` | 아래 스키마 그대로 (문구는 서버 app_settings). 홈은 `upcoming`(다가오는 방문 카드 또는 `null`)·`changeDeadlineHours`(잠긴 카드 안내의 기한 시간)도 준다 |
| `getReservations()` | `GET /customer/reservations` | `tab`·`status`·`rows`·`series`·`changeable`(시작 전 ∧ 기한 전) 는 서버가 만든다. `changeDeadlineHours` = 기한 시간(서버 `cancel_deadline_hours`) — 잠긴 카드·정기 회차 취소 시트 안내가 쓴다(없으면 mock 기본 24). 예정 탭은 화면이 가까운 순으로 정렬. `?scenario=empty` 면 빈 목록 |
| `changeReservation(id, { date, time })` | `PATCH /customer/reservations/{id}/schedule` | 카드 1장. 기한이 지났으면 `ApiError(BOOKING_NOT_CHANGEABLE)` "방문 N시간 전까지만 변경할 수 있어요" — 화면이 `error.message` 를 보여준다 |
| `cancelReservation(id, { wholeSeries })` | `POST /customer/reservations/{id}/cancel` | 카드 1장. `series: true` 카드의 시트에 "이번 회차만 / 정기청소 전체". 기한이 지났으면 `ApiError(BOOKING_NOT_CANCELLABLE)` "방문 N시간 전까지만 취소할 수 있어요" |
| `getSettings()` / `updateNotificationSetting(key, enabled)` | `GET /customer/settings` / `PUT /customer/notifications/{key}` | 아래 스키마 그대로 |
| `getBookingCategory(c)` | `GET /catalog/categories/{c}` | 404 → `null` |
| `getTimeSlots()` | `GET /catalog/time-slots` | `{ am, pm, unavailable, bookableDays, changeDeadlineHours }` |
| `searchAddress(q)` | `GET /catalog/address?q=` (2자 이상일 때만) | 주소 API 연동 전에는 서버가 `results: []` → 화면은 검색어를 그대로 주소로 쓴다 |
| `getBookingOptions(c)` / `getNoticeChips(c)` / `getConfirmCopy(c)` | `GET /catalog/categories/{c}/options` / `/notice-chips` / `/confirm-copy` | 아래 스키마 그대로. confirm-copy 의 `seriesTermMonths` = 정기청소 회차 수(서버 `series_term_months`) — 주기 화면 안내 "N개월 동안 N회" 가 쓴다(없으면 mock 기본 6) |
| `getSavedAddress()` | `GET /customer/addresses/default` | 404 → `null` (확인 화면은 "주소를 입력해주세요") |
| `getBookingQuote(c, draft)` **(B3 추가)** | `POST /customer/bookings/quote` | `{ perVisit: { base, options, total, label }, visits: [{ date, label }], visitsPlanned, totalAmount, totalLabel, paymentNote }`. 일정이 없으면 서버를 부르지 않고 `null` |
| `createBooking(c, draft)` | `POST /customer/bookings` + `Idempotency-Key: draft.submitKey` | `{ id, category, frequency, date, time, roadAddress, detailAddress, serviceLabel, cleaner, managerAssigned, matchingFailed, cancelled, seriesId, visits: [{ id, date, label }], perVisitLabel, totalAmount, totalLabel }`. `matchingFailed` = 후보가 없는 등으로 매칭에 실패해 멈춘 예약(수리 완료 화면이 '배정 중' 대신 `cleaner` 의 실패 안내를 보인다). `cancelled` = 취소된 예약(생성 응답에서는 false — 다시 연 수리 완료 화면이 `cleaner` 의 '배정 전 취소' 를 보인다). `saveAddress` = 새 주소를 입력했을 때(`reuseAddress === 'no'`). 빠진 일정·주소는 어댑터가 먼저 막는다 |
| `newSubmitKey()` **(B3 추가)** | — | 확인 화면이 처음 "완료" 를 누를 때 만들어 초안 `submitKey` 에 둔다. 초안 내용이 바뀌면 `useBookingDraft` 가 지운다 |
| `getBooking(id)` | `GET /customer/bookings/{id}` | 위와 같은 모양. 완료 화면 5번째 행 "결제 금액" = `totalLabel`(정기는 `"5회 · 400,000원"`) |
| `getProfileForm()` / `updateProfile({ name, nickname })` | `GET` / `PATCH /customer/profile` | `{ name, nickname, phone }` (휴대폰 변경은 §13-7 전까지 서버가 받지 않는다) |
| `getAddresses()` / `addAddress()` / `removeAddress(id)` / `setDefaultAddress(id)` | `GET` / `POST /customer/addresses` / `DELETE /customer/addresses/{id}` / `PUT /customer/addresses/{id}/default` | `[{ id, label, roadAddress, detailAddress, isDefault }]` / 1건 / `{ id }` / 바뀐 목록. 첫 주소가 기본, 기본을 지우면 다음 주소가 기본, '기본으로' 로 바꾼다 |
| `getPaymentMethods()` | `GET /customer/payment-methods` | PG 연동 전에는 항상 `[]` |
| `getLiveSession(bookingId)` **(B5)** | `GET /customer/bookings/{id}/live` | `{ manager, startedAt, viewers, status, reassurance, playback, live: { state, available, recordingReady, recordingPending, recording: none|pending|ready|failed } }`. id 없으면 DEV 에서만 mock(프로덕션은 404 → 화면이 /reservations 로). ended 인데 recordingReady·recordingPending 둘 다 false 면 녹화본이 없다(폴링 중단) |
| `getLiveViewerToken(bookingId)` / `getRecording(bookingId)` **(B5)** | `POST /live/sessions/{id}/token` / `GET /live/sessions/{id}/recording` | `{ url, token, roomName }` (진행 중 아니면 `LIVE_NOT_AVAILABLE`) / `{ url, durationSeconds }` (준비 전 `null`). `live/liveClient.js` 만 부른다 |
| 로컬 유지 | — | `getFrequencyOptions` `getAddressReuseOptions` `saveBookingStep`(초안은 sessionStorage) `getCompleteRows` `getHousingOptions` |

## `getHome()` → Home/Realtime · Home/Standard-A
`{ slides: [{ id, eyebrow, headline, title, description, image, peek }], upcoming, changeDeadlineHours }`. `image`/`peek` 는 ui `ASSETS` 의 키. 슬라이드 순서 = Figma 카운터(01 실시간, 02 일반). 히어로 배경·이미지 위치는 화면 CSS 가 `id` 별로 정한다.

## `getRepair()` → Repair
`{ hero: { eyebrow, headline, title, description, image } }`. 수리 카테고리 8종은 ui `REPAIR_CATEGORIES` (Figma 순서). 각 항목의 Figma 프로토타입은 예약 Frequency 화면(1·5 → Booking, 2·6 → MoveIn, 3·7 → Appliance, 4·8 → Office)이라 그대로 따랐다 — 수리 전용 플로우는 Figma 에 없음.

## `getReservations()` / `changeReservation(id)` / `cancelReservation(id)` → Reservations
| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string | |
| `tab` | 'upcoming' \| 'done' \| 'cancelled' | 탭 (예정 / 완료 / 취소). 탭 문구 "예정 2" 의 숫자는 건수 |
| `title` | string | 서비스 이름 |
| `status` | 'scheduled' \| 'assigning' \| 'inProgress' \| 'done' \| 'cancelled' | 서버 BookingMapper 가 §6.1 규칙으로 만든다 (카드 배지) |
| `rows` | `{ label, value }[]` | 일정 / 주소 / 매니저 |

완료·취소 탭의 목록과 빈 상태 디자인은 Figma 에 없음 → 데이터가 없으면 비어 있다. `?scenario=empty` 로 확인.

## `getSettings()` / `updateNotificationSetting(key, enabled)` / `logout()` → Settings
`{ header, profile: { name, phone, avatarSrc }, groups: [{ title, rows: [{ key, label, toggle?, value? }] }], notifications: { push, marketing } }`.
메뉴 행의 연결 화면(내 정보 수정, 주소 관리 …)은 Figma 에 없음 → TODO. 로그아웃 후 `/signup` 으로 (코드에서 정함).

## 예약 플로우 (C5~C6) — `getBookingCategory` `getFrequencyOptions` `getTimeSlots` `getAddressReuseOptions` `searchAddress` `saveBookingStep`
경로 `/booking/:category/<step>` — category: 'home' | 'office' | 'appliance' | 'move-in'. 4개 카테고리의 Figma 프레임을 diff 한 결과 DateTime·Step2·AddressSearch·AddressConfirm 은 완전히 같고, Frequency 만 ServiceLabel 문구와 프로모션 배너(home 은 정기청소 배너, 나머지는 자리표시)가 다르다.
진행 중인 입력은 `screens/booking/bookingDraft.js` 가 sessionStorage 에 카테고리별로 보관한다(새로고침해도 유지).

| 단계 | 저장 필드 | 설명 |
|---|---|---|
| 1 frequency | `frequency` | 'recurring' \| 'oneTime' (Figma 기본 선택 정기청소) |
| 2 datetime | `date`, `time` | 'YYYY-MM-DD', 'HH:mm'. 주말은 선택 불가(Figma 회색) |
| 3 step2 | `reuseAddress` | 'yes' \| 'no'. 네 → 옵션(6)으로, 아니오 → 주소 검색(4)으로 (코드에서 정함) |
| 4 address-search | `addressQuery` | 검색어. 결과 목록은 주소 API 연동 시 |
| 5 address-confirm | `roadAddress`, `detailAddress` | |

| 6 options | `freeOptions[]`, `paidOptions[]`, `optionsSummary` | key 배열. 무료는 최대 2개(택2). optionsSummary 는 Step2 의 옵션 필드에 보이는 문구 |
| 7 notes | `notices[]`, `request` | 주의사항 칩 key 배열 + 요청사항 텍스트 |
| 8 confirm | — | "수정하기" 는 해당 단계로 이동. "완료" = createBooking → /complete |
| complete | — | 완료 이미지 + 예약 카드(예약일·시간·주소·담당 클리너). 화면 탭 → /home (코드에서 정함) |

`getBookingOptions(category)` `getNoticeChips(category)` `getConfirmCopy(category)` `createBooking(category, draft)` `getCompleteRows()` — 4 카테고리 diff: Options 는 옵션 문구·가격·아이콘만, NotesRequests 는 칩 문구·아이콘만, Confirm 은 서비스 종류 라벨·주기 문구·칩 내용만 다르고 Complete 는 동일.

## `getLiveSession()` → Service/LivePortrait · LiveLandscape
`{ manager: { name, description, avatar }, startedAt, viewers, status: { title, description }, reassurance: { title, description }, playback: { currentLabel, totalLabel, ratio } }`. Figma 에 보이는 값 그대로. 영상 스트림·시청자 수 갱신·녹화본 탐색은 단계 5(LiveKit) 의 `live/useLiveSession.js` 가 맡는다.

## Settings 아래 화면 · 약관 본문 (Figma 에 없음 — 코드에서 정함)
`getTerm(id)` · `getProfileForm()`/`updateProfile(form)` · `getAddresses()`/`addAddress()`/`removeAddress(id)` · `getPaymentMethods()`(결제 연동 전 빈 배열) · `getFaq()` · `submitInquiry({ content })` · `getNotices()`/`getNotice(id)`.
`changeReservation(id, { date, time })` · `cancelReservation(id, { reason, wholeSeries })` 는 서버(`PUT …/schedule`, `POST …/cancel`)를 부른다. `searchAddress(query)` 는 서버 `GET /catalog/address`(§13-4 주소 API 전에는 빈 결과 — 화면이 검색어를 주소로 쓴다).

## 지원·푸시 (B6, 실서버)
| 함수 | 경로 | 응답 |
|---|---|---|
| `getFaq()` | `GET /support/faq?audience=customer` | `[{ id, question, answer }]` |
| `getNotices()` / `getNotice(id)` | `GET /support/notices?audience=customer` / `GET /support/notices/{id}?audience=customer` | `[{ id, title, date: "2026.09.10" }]` / `{ id, title, date, body }` |
| `submitInquiry({ content, submitKey })` | `POST /support/inquiries` (+ `Idempotency-Key`) | `{ id }` — 공백만이면 VALIDATION. 키는 `newInquiryKey()`, 내용을 고치면 화면이 새 키 |
| `registerPushToken({ platform, token })` | `PUT /push/token` | `{ ok }` — `push/pushClient.js` 가 부른다. `logout()` 은 먼저 `DELETE /push/token` |
