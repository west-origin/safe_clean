# 매니저 앱 데이터 어댑터

화면은 `api/*.js` 의 함수만 호출한다. 함수는 서버(`backend/docs/api.md`)를 부르고, 응답↔화면 계약이 다르면 함수 안에서 변환한다. `mock.js` 는 서버가 주지 않는 로컬 문구·옵션(반복 옵션·인증 문구 등)만.
스키마는 Figma 화면에 보이는 값에서 뽑았다(코드에서 정한 항목 — 서버 설계 시 확인).

## 공통
- 모든 함수는 `Promise` 를 돌려준다 (실서버 교체 대비).
- 개발 중 빈 상태를 보려면 URL 에 `?scenario=empty`, 오류 화면(ScreenError)을 보려면 `?scenario=error` 를 붙인다 (DEV 에서만, 홈 함수에만 걸려 있다).

## `getHome()` → Manager/Home · Manager/Home/Empty
| 필드 | 타입 | Figma 예시 |
|---|---|---|
| manager.name | string | 김매니저 |
| manager.approved | boolean | false 면 홈 상단 '승인 대기 중이에요' 카드 (APPROVAL_PENDING_COPY). 정지와 별개 |
| manager.suspension | string \| null | 콜 배정 정지 중이면 '9/29 까지 콜을 받을 수 없어요' → '콜 배정 정지 중이에요' 카드 (SUSPENDED_COPY, 승인 대기 카드 대신) |
| today.dateLabel | string | 9월 15일 (화) |
| today.count | number | 3 (Empty: 0) |
| inProgress | null \| { title, elapsed, since, jobId, status } | 청소 진행 중 / 01:23:42 / 14:02부터 진행 · 강남구 역삼동 / status: inProgress·paused(배지·이동 화면) |
| newCalls | null \| { count, expiry, summary } | 2 / 4:32 후 만료 / 가장 가까운 콜 · 9월 18일 오후 2:00 · 85,000원 |
| remaining | ScheduleItem[] | 아래 |

ScheduleItem: `{ id, time, status, customer, service }` — status 는 StatusBadge 의 값 (`scheduled` 등)

## `getCalls()` → Manager/Call/List · Manager/Call/Empty
| 필드 | 타입 | Figma 예시 |
|---|---|---|
| calls[] | CallItem[] | 아래 |
| availability.status | string | 콜 수신 중 (미승인이면 '승인 대기 중') |
| approved | boolean | 미승인 매니저는 매칭 후보가 아니다 — 하단 카드 점이 빨강 |
| availability.detail | string | 오늘 09:00 - 18:00 근무 설정됨 |
| tabBadge | number | 2 (콜 탭 배지) |

CallItem: `{ id, expiry, title, location, pay, eta, note }`

## `getCall(id)` → Manager/Call/Detail
CallItem + `customerRequest: string` (고객 요청사항)

## `getRejectReasons()` / `rejectCall(id, reason)` → Manager/Call/RejectSheet
사유 6개 문자열 배열 (Figma 문구). `rejectCall` 은 `{ id, reason }` 을 돌려준다. 이미 닫힌 콜이면 오류 — `OFFER_TAKEN`·`OFFER_EXPIRED` 는 '이미 마감된 요청이에요' 시트, 내가 수락한 콜의 `INVALID_STATE` 는 서버 문구 (문제 목록 10번).

## `getJob(id)` → Manager/Job/Detail
| 필드 | 타입 | Figma 예시 |
|---|---|---|
| status | StatusBadge status | scheduled |
| startLabel | string | 10:00 시작 (오늘이 아니면 "9월 25일 (금) 14:00 시작") |
| today | boolean | true — 작업 날짜가 오늘. false 면 '현장 도착' 대신 안내 |
| address | string[] (줄) | 서울 강남구 역삼동 / ○○아파트 101동 1203호 |
| info[] | { label, value } | 고객 김○○ · 청소 유형 아파트 기본청소 · 예상 소요 3시간 |
| pay | string | 85,000원 (파란 강조) |
| scope[] | string | 거실 · 주방 · 욕실 2 · 침실 3 |
| requests[] | string | 반려묘 있음 … (수리 예약이면 '가전: 냉장고' '증상: …' '사진 N장 첨부' 줄이 앞에) |
| photos[] | string | 수리 첨부 사진 서명 URL(1시간). 청소는 빈 배열 |

## `getEquipmentStatus()` → Manager/Job/Preparing
`{ label, value, status: 'ok'|'issue' }[]` — 카메라 연결됨 · 배터리 82% · 네트워크 양호 · 실시간 전송 준비 완료

## `getEquipmentStatus()` → Manager/Job/Preparing · Preparing/Error
서버의 실제 바디캠 상태 4행. 연결 안 됨이면 status 'issue' 행이 온다(인자 없음 — Error 화면도 같은 값)

## `getActiveJob()` / `setChecklistItem(jobId, itemId, done)` → Manager/Job/InProgress
| 필드 | 타입 | Figma 예시 |
|---|---|---|
| title · elapsed · since | string | 청소 진행 중 · 01:23:42 · 14:02부터 진행 · 실시간 촬영 중 |
| equipment[] | { label, value, status } | 카메라 정상 · 배터리 74% · 네트워크 양호 |
| requests[] | string | 반려묘 있음 … |
| checklist[] | { group, items: { id, label, done }[] } | 거실/주방/욕실 각 3개, 9개 중 4개 완료 |

## `getPauseReasons()` / `pauseJob(jobId, reason)` → Manager/Job/PauseSheet
사유 6개 (Figma 문구). 이후 Job/Paused 로 이동.

## `getPausedJob()` / `resumeJob(jobId)` → Manager/Job/Paused
`{ since: '14:32부터 중단됨', note: '촬영이 중단된 동안에는 …' }`

## `completeJob(jobId)` / `getCompletedJob()` → Manager/Job/CompleteConfirm · Manager/Job/Complete
CompleteConfirm 의 "아직 완료하지 않은 항목" 은 InProgress 체크리스트 상태에서 계산한다.
`{ title, subtitle, summary: { label, value, tone }[], payout: { label, amount, note } }`

## `getSettlement({ before? })` → Manager/Settlement
목록은 한 페이지(50건). 응답 `nextBefore`(불투명 커서 문자열) 가 있으면 화면이 '더 보기' 로 `before` 를 넘겨 이어 붙인다.
| 필드 | 타입 | Figma 예시 |
|---|---|---|
| monthly | { label, amount } | 이번 달 예상 정산 · 1,240,000원 |
| stats[] | { label, value, tone } | 완료 업무 18건 · 정산 예정 320,000원 · 정산 완료 920,000원(success) |
| items[] | { id, date, status, title, amount } | 9월 14일 · complete · 강남구 기본청소 · 120,000원 |

## `getSettlementDetail(id)` → Manager/Settlement/Detail
`{ title, subtitle, breakdown: { label, value, tone }[], final: { label, value } }`

## `getSchedule()` → Manager/Schedule · Manager/Schedule/Empty
`{ items: ScheduleItem[], week: [{ date, items }] }` — items 는 오늘, week 는 오늘~+6일(7일 탭). 7일/달력 탭은 Figma 에 없음.

## `getAvailability()` → Manager/Schedule/Availability
| 필드 | 타입 | Figma 예시 |
|---|---|---|
| defaultHours[] | { day, hours, off? } | 월~금 09:00 - 18:00 · 토/일 휴무 |
| defaultHours[] | { day, hours, off, start, end } | 월 · 09:00 - 18:00 (start/end 는 수정 시트 초기값 — 문구를 쪼개지 않는다) |
| unavailable[] | { id, date, label, time, repeat } | 9월 18일 · 개인 일정 · 14:00 - 18:00 · weekly 면 '매주 반복' 표시, 카드에 삭제 |

## `getRepeatOptions()` / `addUnavailable({ date, allDay, start, end, repeat, reason })` → Manager/Schedule/AddUnavailable
반복 옵션 3개 (Figma 문구). 저장 결과는 unavailable 항목 형태.

## `getProfile()` → Manager/Profile
`{ name, stats: { label, value }[], menu: { key, label, to? }[] }` — 메뉴 10개, `to` 가 있는 항목만 이동(근무 일정 관리 · 촬영 장비)

## `getEquipmentDevice()` → Manager/Profile/Equipment
`{ name: '안심캠 #AC-00231', connection: '정상 연결', status: { label, value, status }[] }`

## 매니저 데이터 (B4 부터 실서버 — 경로·응답은 `backend/docs/api.md` §5.3)
| 함수 | 서버 | 화면 계약 |
|---|---|---|
| `getHome()` | `GET /manager/home` | 아래 스키마 그대로. `?scenario=empty` 면 카드 없음 |
| `getCalls()` / `getCall(id)` | `GET /manager/offers` / `/offers/{id}` | CallItem 에 `expiresAt`(ISO) 추가, `note` 없으면 생략. 닫힌 콜 상세는 `ApiError(OFFER_EXPIRED)` |
| `acceptCall(id)` **(B4 추가)** | `POST /manager/offers/{id}/accept` + `Idempotency-Key` | `{ jobId }` → `/job/{jobId}`. `OFFER_TAKEN`·`OFFER_EXPIRED` 면 화면이 "이미 마감된 요청이에요" 시트 |
| `rejectCall(id, reason)` / `getRejectReasons()` / `getPauseReasons()` | `POST …/reject` / `GET /catalog/reasons?kind=reject|pause` | `{ id, reason }` / `string[]` |
| `getJob(id)` / `getJobContact(id)` | `GET /manager/jobs/{id}` / `/contact` | 남의 작업 404 / 당일 ±1일 아니면 `null` |
| `getEquipmentStatus()` | `GET /manager/device/status` | 4행. `error` 인자는 사라짐(Preparing/Error 도 실제 상태) |
| `startJob(id)` **(B4 추가)** | `POST /manager/jobs/{id}/start` + `Idempotency-Key` | `{ jobId, startedAt }`. 화면 순서 `startJob` → `live.start`. 당일 아님·다른 작업 진행 중이면 `ApiError(INVALID_STATE)` 문구 |
| `getActiveJob()` / `setChecklistItem()` / `pauseJob()` / `getPausedJob()` / `resumeJob()` / `completeJob()` / `getCompletedJob()` | `GET /manager/jobs/active` / `PUT …/checklist/{itemId}` / `POST …/pause` / `GET /jobs/active/pause` / `POST …/resume` / `POST …/complete`(+키) / `GET /jobs/last-completed` | 아래 스키마 그대로(+`startedAt`·`pausedSeconds`). `pauseJob` 은 영상이 나가는 중이면 `INVALID_STATE` — 화면은 `live.pause()` 를 먼저 부른다 |
| `getSettlement()` / `getSettlementDetail(id)` | `GET /manager/settlements` / `/{id}` | 그대로 (`status` 는 `settlementPending|complete`) |
| `getSchedule()` | `GET /manager/schedule` | `{ items, week }` (오늘~+6일) |
| `getAvailability()` / `updateDefaultHours(rows)` / `addUnavailable(form)` | `GET /manager/availability` / `PUT …/hours` / `POST …/unavailable` | 앱 행 `{ day, hours, off }` ↔ 서버 `{ weekday, start, end, off }`, 반복 `'반복 없음'|'매주'|'매월'` ↔ `none|weekly|monthly`, 날짜 `2026.09.18` → `2026-09-18` 을 어댑터가 바꾼다 |
| `getProfile()` / `getProfileDetail(key)` / `getEquipmentDevice()` | `GET /manager/profile` / `/profile/{key}` / `GET /manager/device` | 그대로. 기기 미등록이면 404 |
| `getManagerNotifications()` / `updateManagerNotification()` | `GET` / `PUT /manager/notifications/{key}` | 그대로 |
| `startLiveSession` / `getLiveSessionStatus` / `pauseLiveSession` / `resumeLiveSession` / `stopLiveSession` **(B5)** | `POST /live/sessions/{id}/start` / `GET …` / `POST …/pause` / `…/resume` / `…/stop` | `live/liveClient.js` 만 부른다. `pause` 는 서버가 mute 를 확인 못 하면 `LIVE_PAUSE_UNCONFIRMED`(502) |
| `getEquipmentDevice()` **(B5)** | `GET /manager/device` | `{ name, connection, status[4], stream: { url, keyMasked }, lastOnline }` — 촬영 장비 화면이 바디캠 설정 정보를 보여준다 |
| 로컬 유지 | — | `getRepeatOptions` `getAuthCopy` `getSupportCopy` · 공지·문의는 B6 |

쓰기(수락·시작·완료)의 멱등 키는 어댑터가 대상별로 만들어 성공할 때까지 재시도에 같은 키를 보낸다. 서버 없이 화면을 보려면 `VITE_API_MOCK=1`(`npm run dev:manager:mock`, 5176).

## Figma 에 없는 동작·화면 (코드에서 정함)
`getJobContact()`(지도 검색어·고객 전화, mock 번호) · `updateDefaultHours(rows)` · `getSchedule()` 에 `week[]` 추가(이번 주·달력 탭) · `getProfileDetail(key)`(매니저 정보·활동 지역·신원 인증·교육 이수·정산 계좌, 읽기 전용) · `getManagerNotifications()`/`updateManagerNotification()` · `getManagerNotices()`/`getManagerNotice(id)` · `getSupportCopy()`/`submitSupportInquiry({ content })`.

## 로그인·회원가입 (Figma 에 화면 없음 → 코드에서 정함. B2 부터 실서버 — `backend/docs/api.md` §5.1)
서버 호출은 `http.js`, 세션은 `session.js`(localStorage `ansimclean.manager.auth`). 화면 테스트는 `src/test/fakeHttp.js`.

| 함수 | 서버 | 화면 계약 |
|---|---|---|
| `isSignedIn()` / `signupStep()` / `SIGNUP_STEP_PATHS` | — | 가입까지 끝난 로그인 상태 / 가입 중이면 `'complete'` → `/auth/signup/complete` |
| `getAuthCopy(screen)` | — (`mock.js` AUTH_COPY) | 문구 |
| `loginWithPhone({ phone, password })` | `POST /auth/login` (role manager) | `{ ok: true, user }` · 틀리면 `{ ok: false }` |
| `logout()` | `POST /auth/logout` | 세션 삭제 |
| `getManagerTerms()` / `agreeManagerTerms(ids)` / `getManagerTerm(id)` | `GET /terms?audience=manager` / 초안 / `GET /terms/{id}?audience=manager` | `{ terms, note }` / — / `{ id, title, body }` |
| `checkPassword(pw)` | — | 8자 이상 |
| `requestPhoneVerification(phone)` / `verifyPhone(phone, code)` | `POST /auth/phone/otp` / `/auth/phone/verify` | `{ sent }` 또는 `{ sent:false, reason:'taken' }` / `{ ok }` |
| `saveManagerSignUp({ name, password })` | `POST /auth/signup` | 계정 생성 → 세션(가입 중) |
| `completeManagerSignUp()` | `POST /auth/signup/complete` | `{ ok, user }` → 로그인 상태 |
| `refreshMe()` / `deleteAccount(password)` | `GET /auth/me` / `DELETE /auth/account` | 앱 시작 1회 / 진행 중 작업이면 `INVALID_STATE` |

## 지원·푸시 (B6, 실서버)
| 함수 | 경로 | 응답 |
|---|---|---|
| `getManagerNotices()` / `getManagerNotice(id)` | `GET /support/notices?audience=manager` / `GET /support/notices/{id}?audience=manager` | `[{ id, title, date }]` / `{ id, title, date, body }` |
| `submitSupportInquiry({ content, submitKey })` | `POST /support/inquiries` (+ `Idempotency-Key`) | `{ id }` — 키는 `newInquiryKey()`, 내용을 고치면 화면이 새 키. 문구(`getSupportCopy`)는 mock 그대로 |
| `registerPushToken({ platform, token })` | `PUT /push/token` | `{ ok }` — `push/pushClient.js` 가 부른다. `logout()` 은 먼저 `DELETE /push/token` |
