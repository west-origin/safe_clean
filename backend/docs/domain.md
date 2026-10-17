# 도메인 규칙

## 고객 체크리스트·이력·영상 구간 (2026-10-17)

- V053은 녹화 시작 시각과 실제 파일 시작 확인 여부, V054는 체크·해제 이력이다. 기존 V051(잠금)·V052(결제)는 보존한다.
- 체크는 예약 행을 먼저 잠근다. 동시 재요청·체크 해제·작업 완료도 직렬화된다. 같은 상태는 무시하며, 실제 변경만 서버 시각·매니저·당시 문구와 함께 추가한다.
- `job_checklist_events`는 앱에서 추가·조회만 한다. 항목 FK가 없어 재배정 시 원래 항목을 삭제해도 남는다. 예약 삭제는 cascade. RLS와 anon/authenticated 권한 회수로 클라이언트 접근을 막는다.
- 같은 시각의 이력은 identity `event_order`로 마지막 상태를 정한다. 폴링은 항목마다 최신 이력만 조회하고, 같은 DB 스냅샷의 현재 완료 상태와 대조한다.
- `recording_started_at`의 요청 시각은 임시값이다. LiveKit의 `FileInfo.started_at`을 받아 `recording_start_confirmed=true`가 되어야 구간 계산에 쓴다. Egress 프로세스 시작 시각과 파일 시작 시각은 구분한다.
- 실제 시작은 시작/업데이트·종료 웹훅과 기존 재조정기 조회에서 복구한다. 다른 Egress의 값은 무시하고 녹화 삭제 시 기준도 비운다.
- 기존 녹화·항목은 추정 시각으로 보정하지 않는다. 완료 개수·요청사항·기본 재생은 유지하되 근거가 없는 구간은 표시하지 않는다.
- 구간은 이전 완료부터 이번 완료까지, 경계 정각부터 다음 구간이다(5:19 싱크대, 5:20부터 다음 구간). 마지막 체크 뒤는 무제목이다. 1% 미만 구간과 동시 완료는 이름 배열로 합쳐 `첫 이름 외 N`으로 표시한다.


> **이 파일이 기준이다.** `BACKEND_PLAN.md` §6·§8 에서 B1(2026-09-19)에 옮겨 왔다. 이후 변경은 여기서 하고, 계약을 바꾸면 백엔드·앱 `api/*.js` 를 같은 커밋에서 바꾼다 (CLAUDE.md §11-2).
> 구현 상태는 맨 아래 "구현 현황". 표시가 없으면 아직 미구현.

## 도메인 규칙 (`backend/docs/domain.md`. 구현자는 이 절을 그대로 코드로 옮긴다)

### 6.1 예약 상태기계 (`bookings.status`, `Booking` 엔티티의 메서드만 바꾼다)
```
requested ──(매칭 시작, 생성 직후)──▶ assigning ──(매니저 수락)──▶ scheduled ──(매니저 "청소 시작", 당일)──▶ in_progress ◀──resume── paused
   │                                     │                            │                                       │ pause ▲
   └──────────── cancel ─────────────────┴──────── cancel ────────────┘                                       └───────┘
                                                                                                              │ complete
                                                                                                              ▼
                                                                                                            done
```
`preparing` 은 **서버 상태가 아니다**. Job/Preparing 은 앱 화면이고, 서버는 `scheduled` 인 채로 라이브 세션(§8)만 미리 연다. (이전 초안의 `preparing` 상태는 화면에서 뒤로 가면 되돌릴 방법이 없어 삭제했다.)

| 전이 | 메서드 | 누가 | 조건 | 부수 효과 |
|---|---|---|---|---|
| (생성) → `requested` → `assigning` | `BookingService.create` | 고객 | 날짜 ≥ 내일, 평일, `time` 이 `time_slots` 안, 무료 옵션 ≤ `free_option_limit`, 모든 option/notice key 가 카탈로그에 있음 | 금액 계산(§6.4) · 정기청소면 `booking_series` 생성(§6.8) · `OfferService.dispatch(booking)` · 이력 기록 |
| `assigning` → `scheduled` | `booking.assign(managerId)` | 매니저(offer accept) | 수락한 offer 가 `pending` 이고 만료 전 | 같은 round 의 다른 pending offer 전부 `cancelled` · 체크리스트 템플릿 복사 → `job_checklist_items` · `live_sessions` **upsert**(없으면 생성, 있으면 `created` 로 리셋) · 고객 푸시 "매니저가 배정됐어요" |
| `scheduled` → `in_progress` | `booking.start()` | 매니저 | 작업 날짜가 오늘(Asia/Seoul) · 그 매니저에게 다른 `in_progress/paused` 가 없음 (부분 유니크 인덱스가 최종 방어) | `started_at=now` · 라이브 세션은 `live/start` 가 별도로 (`waiting_camera` → 바디캠이 붙으면 `live` + 고객 푸시, §8.1) |
| `in_progress` → `paused` | `booking.pause(reason)` | 매니저 | reason 이 `catalog_reasons(kind=pause)` 안 | `job_pauses` insert (라이브 mute 는 `live/pause`, §8.1) |
| `paused` → `in_progress` | `booking.resume()` | 매니저 | — | 마지막 `job_pauses.resumed_at=now` |
| `in_progress`/`paused` → `done` | `booking.complete()` | 매니저 | — | `ended_at=now` · 열린 pause 닫기 · `LiveService.stop`(Egress 정지·방 삭제, §8.1) · 정산 생성(§6.5) · `manager_profiles.completed_count+1` · 고객 푸시 |
| `requested/assigning/scheduled` → `cancelled` | `booking.cancel(reason, actor)` | 고객 | **`starts_at − now ≥ cancel_deadline_hours(24)`** (하나의 규칙. 내일 14시 예약은 오늘 14시까지) 아니면 `BOOKING_NOT_CANCELLABLE`(문구 "방문 N시간 전까지만 취소할 수 있어요", N = 설정 값 — 앱 안내도 서버가 내려 준 이 값을 쓴다) | pending offer `cancelled` · 배정된 매니저 푸시(밤에 일정을 바꿔 '일정 변경' 안내를 아침으로 미뤄 둔 원래 매니저도 — §6.2 밤 규칙) · `wholeSeries` 면 시리즈와 미래 예약 전부 취소 |
| 일정 변경 (`schedule`) | `booking.reschedule(date,time)` | 고객 | `requested/assigning/scheduled` 이고 같은 규칙(`starts_at − now ≥ 24h`), 새 일정도 규칙(내일 이후·평일·슬롯) 통과 | `starts_at/ends_at` 재계산 → `scheduled` 였으면 → `assigning` 으로 되돌리며 **기존 `accepted` 오퍼를 `superseded` 로**, 남은 `pending` 은 `cancelled`, `manager_id/assigned_at` 을 비우고 체크리스트를 지우고, `dispatch_round+1` → **기존 매니저에게 새 round 의 우선 오퍼 10분**(수락하면 다시 `assign`, 거절·만료면 일반 매칭). `live_sessions` 는 **PK 가 booking_id 이므로 재생성하지 않고 `status=created` 로 리셋**(`assign` 은 항상 upsert) · `BookingRescheduled` 이벤트(기존 매니저 푸시) · 이력 기록 |
| 그 밖의 전이 | — | — | `INVALID_STATE` | |

**지각 안내** (상태는 그대로, 사용자 결정 2026-09-27 — 자체 점검 #25): `scheduled` 인데 `starts_at + tardyMinutes(30)` 가 지나도 시작하지 않았으면 `LateStartNotifier`(1분마다)가 `late_notified_at` 을 채우고 `ManagerLate` → 고객 푸시 "매니저가 늦어지고 있어요 · 약속 시간이 30분 넘게 지났어요. 도착이 더 늦어지면 고객센터로 알려주세요"(거래성 — 설정 토글과 무관, 코드에서 정함. 앱이 앞에 떠 있어도 OS 배너로 보인다 — 고객 앱 `capacitor.config.json` presentationOptions). 예약당 한 번. 약속 시간이 12시간보다 오래 지난 예약은 보지 않는다(끝내 시작하지 않으면 방문일 이틀 뒤 03:10 `StaleBookingCleaner` 가 미방문 취소). 시작 시각이 `starts_at + tardyMinutes` 보다 뒤면 매니저 0.5점은 `JobService.start` 가 매긴다(docs/manager-policy.md §2) — 초 단위까지 비교해 30분 0초는 지각이 아니고 30분 30초는 지각이다. 이 안내와 같은 기준이라 안내를 받은 예약은 시작할 때 지각으로 기록된다(예전에는 분 아래를 버려 31분부터였다, 문제 목록 26번).

Reservations 화면 매핑(서버가 만든다): `tab` = `done`→`done`, `cancelled`→`cancelled`, 나머지 `upcoming`. `status` = `assigning/requested`→`assigning`(매니저 배정 중), `scheduled`→`scheduled`(방문 예정), `in_progress/paused`→`inProgress`, `done`, `cancelled`. `rows` = `[일정: KoreanFormat.dateTimeLong, 주소: road_address, 매니저: 매니저 이름+" 매니저" | "배정 후 알려드려요" | (취소이고 미배정) "배정 전 취소"]`. 오늘 진행 중이면 일정 값은 `"오늘 " + 오전/오후 h시`. 정기청소 예약은 `title` 뒤에 `" · 정기"` 를 붙이지 않는다(Figma 문구 유지) — 대신 `series: true` 필드를 추가로 내려 앱 취소 시트가 선택지를 보여준다.

### 6.2 매칭 (`offer/OfferService`) — "콜"
1. `dispatch(booking)`: 작업 구간 = `[service_time, service_time + duration_minutes)`. 후보 = `manager_profiles.approved=true` ∧ **`areas` 에 `bookings.sido || ' ' || bookings.sigungu` 포함**(sigungu 가 null 이면 `manager_profiles.sido = bookings.sido` — 주소 검색 API 연동 전 임시). sido 를 못 읽는 주소는 예약·주소 저장에서 `VALIDATION` 으로 받지 않는다(`AddressRegion.requireMatchable`, 직접 사용 점검 U1) ∧ 그 요일 `manager_hours.off=false` 이고 **시작 시각이 `[start,end]` 안**(종료 시각에 시작하는 마지막 슬롯도 포함. 끝나는 시각은 근무 종료를 넘어도 된다 — 매니저가 콜을 보고 수락할지 정한다, 사용자 결정 2026-09-27. 근무 불가·겹치는 작업은 작업 전체로 본다) ∧ `manager_unavailable`(repeat=weekly 는 같은 요일, monthly 는 같은 일자로 펼쳐서) 과 겹치지 않음 ∧ 그 매니저의 같은 날 `scheduled/in_progress/paused` 예약 구간과 겹치지 않음. 후보 0명이면 `assigning` 으로 두고 운영자 알림(로그 WARN + §13-8). **`exclude` 인자**로 재매칭 때 이미 거절·만료한 매니저를 뺀다.
2. 후보 **전원**에게 `offers(pending, expires_at = now + offer_ttl_minutes(기본 30))`, `pay_amount = bookings.total_amount − bookings.platform_fee`(스냅샷, §6.4). 저장 후 `OffersDispatched` 이벤트 → 커밋 뒤 푸시 "새 청소 요청". **우선 오퍼**(`dispatchPreferred(booking, managerId, ttl=10분)`): 일정 변경·정기 회차는 이 매니저 한 명에게만 먼저 보내고, 거절·만료 시 `dispatch(booking, exclude=[managerId])`. 우선 오퍼도 일반 후보와 같은 조건(지역·근무 가능·겹치는 작업 없음·정지 아님)을 통과할 때만 보내고, 아니면 바로 일반 매칭(자체 점검 #21). 일정 변경의 우선 오퍼는 그 회차를 맡았던 매니저라 정기청소 취소 이력을 보지 않는다(#18). 우선 오퍼를 못 보냈으면 이전 매니저 푸시는 '고객이 일정을 바꿔 이 예약은 다른 매니저에게 다시 배정돼요'(일정 탭, 코드에서 정함, #56). 일정 변경의 우선 오퍼는 콜 푸시(`OffersDispatched`)를 따로 적지 않는다 — 이전 매니저에게 가는 '예약 일정이 변경됐어요 · 고객이 일정을 바꿔 다시 매칭돼요. 새 콜을 확인해주세요' 가 콜 목록으로 안내한다(1회 예약에 '단골 고객의 정기청소 콜' 이 가던 문제, 문제 목록 15번 — 사용자 결정 2026-09-28). 정기청소 다음 회차의 우선 오퍼는 그대로 단골 고객 문구 푸시.
3. `accept(offerId, managerId)`: 한 트랜잭션에서 `select … for update` 로 **manager_profiles(그 매니저 행) → booking → offer** 순서로 잠근다(잠금 순서 고정 — 데드락 방지. 매니저 행을 먼저 잠그므로 같은 매니저의 동시 수락은 직렬화되고, 겹침 검사가 그 뒤에 돈다). 최종 방어는 `bookings_manager_no_overlap` 제약(§4) — 위반 시 `OFFER_TAKEN`. 그 다음 — offer 가 `pending` 이 아니면 `OFFER_TAKEN` / `expires_at < now` 면 `OFFER_EXPIRED` / booking 이 `assigning` 이 아니면(취소됨 포함) `OFFER_EXPIRED` / 그 매니저가 그 시간에 근무하지 않으면(콜을 받은 뒤 휴무·근무 불가를 넣었거나 근무시간을 바꿈 — 1번 후보와 같은 기준 `AvailabilityService.isAvailable`) `INVALID_STATE` "근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요"(문제 목록 7번, 사용자 결정 ① 2026-09-28) / 그 매니저의 겹치는 작업이 그 사이 생겼으면 `OFFER_TAKEN`. 통과하면 `booking.assign(managerId)`(체크리스트 복사·live_sessions 생성 포함), 나머지 pending → `cancelled`, `BookingAssigned` 이벤트.
4. `reject(offerId, reason)`: `rejected`. 모두 거절·만료되면 `redispatch`(지금 매칭 구간에서 이미 받은 매니저 제외, `max_dispatch_rounds`(누적 3회차)까지) → 그래도 없으면 매칭 실패(운영자 로그·고객 알림). 이미 닫힌 콜의 거절: 내가 거절한 콜은 200(저장된 사유, 멱등), 내가 수락한 콜은 `INVALID_STATE`, 다른 이유로 닫힌 콜(다른 매니저 수락·고객 취소·일정 변경·방문 취소)은 `OFFER_TAKEN`, 만료됐거나 만료 시각이 지난 콜은 `OFFER_EXPIRED` — 앱이 '이미 마감된 요청이에요' 를 보인다(문제 목록 10번, 사용자 결정 B 2026-09-28).
   매칭 실패 뒤 **자동 재매칭**(`offer/MatchingRetry`, 매시 05분 08:05~20:05, 사용자 결정 2026-09-27): `assigning ∧ matching_failed_at ∧ starts_at > now + 3시간` 인 예약에, **지금 매칭 구간**(마지막 일정 변경·매니저 방문 취소 이후, `bookings.matching_from_round` V043)에 이 예약의 콜을 받지 않은 후보(방문을 취소한 매니저는 늘 제외)가 생겼으면 새 회차(`restartMatching` — 표시를 지운다)로 그들에게만 보낸다. 새 후보가 없으면 아무것도 하지 않는다(고객에게 실패 알림을 반복하지 않게). 모두 거절·만료되면 평소처럼 다시 매칭 실패 → 고객 알림 한 번 더. 이미 받았던 매니저에게까지 다시 보내려면 운영자 `redispatchByAdmin`. 실패 안내(푸시·카드)는 실제로 가능한 길만 말한다 — `booking/MatchingRetryWindow`(실행 시각·3시간 조건, MatchingRetry cron 과 같아야 한다 — 테스트가 묶는다)로 자동 재매칭이 약속 전에 볼 수 있는지, 24시간 기한으로 일정 변경이 되는지 계산해 `MatchingFailed(retryable, changeable)` 에 싣는다.
   **밤에는 새 콜을 아침으로 미룬다**(문제 목록 2번, 사용자 결정 ③ 2026-09-28): 밤 = 서울 08시 전·21시부터(`MatchingRetryWindow.isNight` — 자동 재매칭 시간대 08:05~20:05 와 같은 상수). 밤에 생긴 새 콜 보내기(예약 생성·일정 변경·방문 취소 뒤 재매칭·거절·만료 뒤 다음 회차·정기 회차 우선 콜 — `dispatch`·`dispatchPreferred`·`onRescheduled` 로 가는 자동 경로 전부)는 콜을 만들지 않고 예약에 보류 표시(`bookings.dispatch_held_at`·`dispatch_held_manager_id`·`dispatch_held_rescheduled`, V046)만 남기고, 아침 첫 실행(`MatchingRetry` 08:05)의 `releaseHeldDispatches` 가 보낸다 — 우선 콜(정기 다음 회차·일정 변경의 원래 매니저)은 아침에도 그 매니저에게 먼저 우선 콜로(정기 직전 매니저는 밤사이 그 정기청소를 취소했는지 다시 본다), 아니면 지금 매칭 구간에서 이미 받은 매니저를 빼고 후보 전원에게. 일정 변경 전 매니저 안내('예약 일정이 변경됐어요')도 밤에는 적지 않고 아침에 우선 콜과 함께 적는다(밤사이 두 번 바꿔도 한 번). **급한 예약**(미루면 약속까지 3시간(`MIN_LEAD`)보다 적게 남음, 예: 밤 22시에 만든 다음 날 10시 예약)은 기다리지 않고 지금 보내고(`holdUntilMorning`), 미룬 뒤 일정 변경으로 급해지면 그때 보낸다. 운영자 재매칭(`redispatchByAdmin`)은 미루지 않는다(보류를 지운다) — 미뤄 둔 일정 변경 전 매니저도 일반 콜을 받지만 '새 콜' 푸시에서는 빠지고 '예약 일정이 변경됐어요'(콜을 받았으면 '새 콜을 확인해주세요') 하나만 받는다(낮의 일정 변경과 같게, 합친 38건 검토 R2). 예약이 취소되면 보류도 지운다 — 밤에 일정을 바꿔 원래 매니저 안내를 미뤄 둔 예약이면 그 매니저에게 '예약이 취소됐어요'(`BookingCancelled`)를 밤에도 바로 보낸다(새 콜이 아닌 안내라 미루지 않는다, 합친 38건 검토 R1). 보류 중 고객 카드는 배정 전 문구('배정 후 알려드려요') 그대로이고 매칭 실패 표시도 없다. 밤에 회차가 모두 닫혀 최대 회차에 닿았으면 매칭 실패 안내는 그대로 간다. 매니저 콜 카드의 '근무 시간 외' 문구는 바꾸지 않는다.
5. `@Scheduled(fixedDelay=30s)` `expireOffers()`: `pending ∧ expires_at < now` → `expired`, 그 예약에 pending 이 0 이면 4번과 같은 재시도. 이어서 정지·승인 취소·탈퇴로 수락할 수 없게 된 매니저의 `pending` → `cancelled`(`closeBlockedOffers`) 후 같은 재시도 — 그 콜이 만료될 때까지 '대기' 로 세어져 재매칭이 늦어지지 않게. 콜을 닫기 직전에 그사이 다시 받을 수 있게 됐는지(정지 면제 등)를 DB 의 지금 값으로 한 번 더 본다(`isApprovedManagerNow` — 먼저 올려 둔 매니저 객체의 옛 값이 아니라, 합친 38건 검토 R3). 정지·미승인 매니저의 콜 탭·홈에는 콜 카드를 싣지 않는다(`pendingFor` 가 빈 목록 — 콜 탭은 정지·승인 대기 안내 카드, 문제 목록 6번).
6. 표시: `expiry` = `KoreanFormat.untilExpiry(expires_at)` → `"4:32 후 만료"`(m:ss), 만료 후는 목록에서 빠진다. `title` = `"9월 18일 목요일 오후 2:00"`, `location` = `"서울 강남구 역삼동 · 아파트 기본청소"`(주소 앞 3어절 · 카테고리 라벨 — 건물번호 앞에서 멈춘다: 세종시처럼 시군구가 없으면 '세종특별자치시 도움6로', 자체 점검 #27), `pay` = `"85,000원"`, `eta` = 거리 계산 전까지 **고정 문구 "이동 시간은 배정 후 안내"** (§13-9), `note` = 첫 notice 라벨 (`"특이사항: 반려동물이 있어요"`), `customerRequest` = `request`.

### 6.3 작업 화면 값 (`job/JobMapper`)
- `getJob`: `startLabel = "HH:mm 시작"`, `address = [road_address, detail_address]`, `info = [고객: 성 + "○○"(마스킹), 청소 유형: 카테고리 라벨, 예상 소요: bookings.duration_minutes / 60 + "시간"(예약 때 고정한 값 — 콜 카드·일정 시간대와 같게, 자체 점검 #26)]`, `pay = offer.pay_amount`, `scope = free_options+paid_options 라벨`, `requests = notices 라벨 + request`.
- `getActiveJob`: 매니저의 `in_progress/paused` 1건. `elapsed = HH:mm:ss(now − started_at − 일시정지 합)`(앱이 1초마다 다시 부르지 않도록 `startedAt`·`pausedSeconds` 도 같이 내려 앱 타이머가 계산. 화면은 이미 `elapsed` 를 쓰므로 초기값으로 채워 준다), `since = "14:02부터 진행 · 실시간 촬영 중"`, `equipment` = 3행(카메라 = `stream_status` online→"정상"/offline→"연결 안 됨"(issue), 배터리·네트워크 = `last_report` 있으면 값, 없으면 "확인 필요").
- `getCompletedJob`: 마지막 `done` 1건. `summary = [청소 시간 "14:02 - 17:25", 총 소요 "3시간 23분"(시작~완료 벽시계 — 일시정지 구간을 빼지 않는다, 진행 화면 타이머는 뺀다. 2026-09-28 결정), 체크리스트 "9개 중 9개 완료"(전부면 success), 촬영 기록 (recording_status ready→"정상 업로드" success / recording→"업로드 중" default / failed→"업로드 실패" issue)]`, `payout = { "예상 정산", final_amount, "정산은 다음 정산일에 지급됩니다." }`.

### 6.4 가격 (`catalog/PricingService`, 숫자는 전부 `app_settings`/`catalog_prices`)
`base = catalog_prices[category][frequency]` · `options = Σ catalog_options.price (paid 만)` · `total = base + options` · `platform_fee = round(total × platform_fee_rate)`(`app_settings.platform_fee_rate`, 기본 0.10) · `manager_pay = total − platform_fee`. 예약 생성 시 `bookings.base_amount/options_amount/total_amount/platform_fee/duration_minutes` 에 **고정**(카탈로그·수수료율이 바뀌어도 과거 예약·오퍼·정산은 안 바뀐다). 오퍼의 `pay_amount` 와 정산의 `final_amount` 는 반드시 이 스냅샷에서 계산한다(같은 예약에서 다른 숫자가 나올 수 없게). **결제 연동(2026-10-17 사용자 요청)**: 토스페이먼츠 일회성 카드 결제. 예약은 unpaid로 생성하고 고객이 예약 완료·내 예약 화면에서 방문 회차별로 결제를 시작한다. CheckoutService가 서버 금액 검증·멱등 승인 의도를 저장하고 아웃박스가 PG를 호출한다. 검증한 PG 조회/승인 결과만 paid로 반영한다. 예약 취소 시 잔액 전체 환불, 부분 환불 상태 동기화, PaymentReconciler 복구를 지원한다. 자동결제·빌링키·카드 저장은 제외. 계약과 운영 활성화는 `backend/docs/payments.md`와 `backend/docs/api.md` 참조. NoopPaymentGateway는 새 결제 경로에서 호출하지 않는다.

### 6.5 정산 (`settlement/`)
`booking.complete()` 와 같은 트랜잭션에서 `SettlementService.createFor(booking)` → `settlements(pending, service_amount=bookings.total_amount, platform_fee=bookings.platform_fee, other_fee=0, final=total−platform_fee)`. `booking_id unique` 라 중복 생성이 DB 에서 막힌다. `status=complete` 는 운영자가 지급 후 바꾼다(관리 API 는 범위 밖 → SQL/대시보드, §13-8). 화면 값: `monthly.amount` = 이번 달(Seoul) `final_amount` 합, `stats` = [완료 업무 = 이번 달 done 수, 정산 예정 = pending 합, 정산 완료 = complete 합(success)], `items` = 최근 50건 `{ date: "9월 14일", status: settlement.status, title: 구 + " " + 카테고리 짧은 라벨, amount }`, detail `breakdown = [서비스 금액, 플랫폼 수수료 -x, 기타 -y]`, `final`.

### 6.6 매니저 홈 (`managerhome/ManagerHomeService` — 읽기 조합만)
`today.dateLabel = "9월 15일 (화)"`, `today.count` = 오늘 `scheduled+in_progress+paused+done` 수, `inProgress` = 활성 작업(§6.3, `title="청소 진행 중"`, `since="14:02부터 진행 · 강남구 역삼동"`), `newCalls` = pending offer 가 있으면 `{ count, expiry: 가장 이른 만료, summary: "가장 가까운 콜 · 9월 18일 오후 2:00 · 85,000원" }`, `remaining` = 오늘 남은 `scheduled` 를 시각순 `{ id, time: "16:30 - 18:30"(시작 + duration), status: "scheduled", customer: "박○○ 고객 · 해운대구 우동", service: 카테고리 라벨 }`. `availability.status` = 지금이 근무시간 안이면 "콜 수신 중" 아니면 "근무 시간 외", `detail = "오늘 09:00 - 18:00 근무 설정됨" | "오늘 휴무"`.

### 6.8 정기청소 시리즈 (`booking/BookingSeriesService`) — **매월 1회 × `series_term_months`회** (결정 2026-09-22, 그 전에는 '한 달 동안 매주')
- 정기청소 = **첫 방문일부터 매월 같은 날짜·같은 시각에 1회씩**, `app_settings.series_term_months` 회(기본 6 = 6개월 6회). 없는 날짜(31일 등)는 그 달 마지막 날로, **주말이면 다음 평일**로 민다(서버가 주말 예약을 막는다). `frequency=recurring` 예약 생성 시 `booking_series(active, term_start=첫 방문일, term_end=마지막 회차 날짜, weekday=첫 방문 요일 기록용, visits_planned)` 를 만들고, **그 기간의 회차 전부를 즉시 `bookings(requested, series_id)` 로 생성**한다(고객 "내 예약" 에 바로 보인다). 요금은 회차마다 개별(§6.4).
- 매칭 시점: 첫 회차는 즉시 `dispatch`. 나머지는 `SeriesDispatcher`(§2.5, **매일 09:00** — 2026-09-28 전에는 03:00 이라 새벽에 콜이 갔다, 문제 목록 2번)가 **방문 7일 전**에 매칭하되, 직전 회차를 수행한 매니저에게 **우선 오퍼 10분** → 거절·만료 시 일반 매칭. 밤에 생긴 회차 매칭(일정 변경으로 매칭 시점 안에 들어옴)은 §6.2 의 밤 규칙대로 아침에. 그 시리즈의 회차를 한 번이라도 방문 취소·미방문한 매니저는 우선 오퍼에서 빠지고 일반 콜로만 받는다 — 원장(`manager_penalties`)의 취소·미방문 행을 상태와 상관없이 센다(운영자가 면제해도 빠진다, 지각은 세지 않는다. docs/manager-policy.md §4 · 문제 목록 27번).
- 종료: `term_end` 가 지나면 시리즈 `ended`. 그 전에 남은 회차(취소·완료가 아닌 것)가 없어지면 바로 닫는다 — 마친 회차가 있으면 `ended`, 전부 취소면 `cancelled` (회차 취소·미방문 정리·매니저 완료 어느 길이든, 자체 점검 #8). 자동 연장 없음. `term_end − 7일` 에 `SeriesEnding` 이벤트 → 고객 푸시 "정기청소가 곧 끝나요. 다음 달도 예약해 보세요"(연장은 새 시리즈 생성 = 같은 예약 플로우).
- 취소: 회차 하나 = 그 예약만(기한 규칙 §6.1). `wholeSeries=true` = 시리즈 `cancelled` + 아직 시작 전인(`requested/assigning/scheduled`, 기한 안) 미래 회차 전부 취소. 기한(방문 N시간 전, N = `app_settings.cancel_deadline_hours`, 기본 24)이 지난 회차는 남고 앱 시트에 "N시간 안에 시작하는 방문은 취소할 수 없어요" 문구로 알린다(N 은 예약 목록 응답의 `changeDeadlineHours` — 고정 글자 '24시간' 이었다, 문제 목록 25번).
- 회원 탈퇴는 `active` 시리즈가 있으면 `INVALID_STATE`. 매니저는 맡은 작업(`BookingDeletionGuard`)·**미지급 정산**(`SettlementDeletionGuard`, 2026-09-22)이 남아 있으면 `INVALID_STATE` — 탈퇴가 계좌 정보를 바로 지우므로 정산이 끝난 뒤에만. 탈퇴 뒤 `AccountMediaDeleted` 핸들러가 고객의 녹화본(Storage)·수리 사진, 매니저의 LiveKit Ingress·기기 행을 지운다 — 예약·정산 행(법정 보존)은 남지만 집 안 영상·사진 미디어는 보존 대상이 아니다(개인정보 약관 '보유 기간: 회원 탈퇴 시').
- 탈퇴 뒤 보관: 1:1 문의(자유 입력이라 주소·번호가 들어 있을 수 있다)는 전자상거래법의 소비자 불만·분쟁처리 기록으로 **탈퇴 시각 + 3년** 뒤 지운다 — 탈퇴 트랜잭션에서 `purge_after` 를 적고(V038, 이미 탈퇴한 사용자도 같은 규칙으로 채움) 매일 04:30 지난 것을 삭제. 문의 등록은 탈퇴와 줄을 선다(탈퇴가 먼저면 401). 사용자 결정 2026-09-27 (자체 점검 #31). 고객·매니저 모두 같다 — 매니저 개인정보 동의의 보유 기간은 V039 로 '매니저 탈퇴 시까지 (1:1 문의 기록은 분쟁 처리를 위해 탈퇴 후 3년 보관)'(version 2)
- 탈퇴 + 5년 파기(`auth/DeletedAccountPurger`, 매일 04:20, BACKEND_PLAN §13-22 — 사용자 결정 2026-09-27): 5년은 전자상거래법 계약·대금결제 기록 보존 기간. 탈퇴 때 지우지 않은 개인정보를 비운다 — 고객: 예약·정기청소 주소(→ '시도 시군구', 없으면 '(주소 파기)')·상세 주소·요청·주의사항·수리 상세·수리 사진 경로·자유 입력 취소 사유(→ `customer`)·상태 이력의 고객 사유, 결제 수단(결제 행은 참조만 끊음). 매니저: 근무시간·근무 불가·패널티 원장·신원 확인/교육/보험 기록 — 지금은 **탈퇴 때** 지운다(`AuthService.deleteAccount`·`ManagerProfile.withdraw`, 매니저 동의 '매니저 탈퇴 시까지' 대로), 파기는 그 전에 탈퇴한 계정에 남은 것까지 한 번 더. 공통: 약관 동의, 남은 1:1 문의. **예약·정산·결제 행은 지우지 않는다**(상대방 이력이 가리키고, 비운 뒤엔 누구인지 알 수 없다). 계정마다 트랜잭션 하나, 끝나면 `profiles.purged_at`(V040). 기능마다 `auth.AccountPurge` 구현(예약·결제) 또는 서비스 직접 호출(프로필·일정·지원) — 패키지 순환 없이
- 앱 변경: 예약 취소 시트에 "이번 회차만 / 정기청소 전체" 라디오(§9.2-5).

### 6.9 멱등 처리 알고리즘 (`common/web/Idempotent.run`, 예약 생성·콜 수락·작업 시작/완료·문의에 적용)
```
run(key, userId, requestHash, action):           -- 전체가 하나의 DB 트랜잭션 (REQUIRES_NEW 아님, 업무와 같은 tx)
  insert idempotency_keys(key, userId, requestHash, 'processing')
    on conflict (key, userId) do nothing returning *
  if inserted:                                   -- 처음 온 요청
    result = action()                            -- 업무 저장 (같은 tx)
    update idempotency_keys set status='done', response_status, response_body   -- 같은 tx
    commit                                       -- 업무 + 키가 원자적으로 저장된다
    return result
  else: row = select … for update              -- 두 번째 이후
    if row.request_hash != requestHash: 422 IDEMPOTENCY_MISMATCH
    if row.status == 'processing': 409 IN_PROGRESS   -- 첫 요청이 아직 커밋 전
    return row.response_status, row.response_body
```
- **커밋 전에 서버가 죽으면** 업무 저장과 키가 함께 롤백된다 → 재시도가 처음처럼 실행된다(중복 없음). **커밋 뒤에 죽으면** 키가 `done` 으로 남아 있다 → 재시도는 저장된 응답을 받는다(중복 없음). "저장 성공 → 응답 기록 전 종료" 는 둘이 한 트랜잭션이라 생기지 않는다.
- 동시에 같은 키가 두 번 오면 `insert … on conflict do nothing` 이 하나만 통과시키고 나머지는 `IN_PROGRESS`.
- 키 없이 온 요청은 멱등 처리 없이 그냥 실행한다(웹 개발 편의). 앱은 항상 보낸다(§9.4).

### 6.7 한국어 표시 (`common/format/KoreanFormat` — 전부 정적 메서드, 단위 테스트 필수)
| 메서드 | 입력 → 출력 | 쓰는 곳 |
|---|---|---|
| `dateLabel(LocalDate)` | `2026-09-15` → `"9월 15일 (화)"` | 매니저 홈·정산 |
| `dateLong(LocalDate)` | → `"2026년 9월 15일"` | |
| `dateTimeLong(LocalDate, LocalTime)` | → `"2026년 11월 1일 (일) 오전 9시"` | Reservations 일정 |
| `dateTimeCall(LocalDate, LocalTime)` | → `"9월 18일 목요일 오후 2:00"` | 콜 카드 |
| `timeKo(LocalTime)` | `14:00` → `"오후 2시"`, `14:30` → `"오후 2시 30분"` | (앱 `formatTimeKo` 와 같은 규칙) |
| `timeRange(LocalTime, LocalTime)` | → `"09:00 - 18:00"` | |
| `hms(Duration)` | → `"01:23:42"` | elapsed |
| `durationKo(Duration)` | → `"3시간 23분"` | |
| `won(long)` | `85000` → `"85,000원"`, 옵션 가격은 `"+3,000원"` (`wonSigned`) | |
| `untilExpiry(Instant)` | → `"4:32 후 만료"` (음수면 `"만료됨"`) | |
| `sinceLabel(Instant, String)` | → `"14:32부터 중단됨"` | |
| `phoneDisplay(String e164)` | `+821012345678` → `"010-1234-5678"`, `phoneLocal` → `"12345678"`, `toE164("12345678")` → `+821012345678`(8자리 숫자만 허용) | |
| `maskName(String)` | `"김하늘"` → `"김○○"` | 매니저에게 보이는 고객 이름 |
| `noticeDate(Instant)` | → `"2026.09.10"` | 공지 |
| `liveStartedAt(Instant)` | → `"2025. 09. 11. 14:32"` | 라이브 |
시간대는 항상 `AppClock.zone()` = `Asia/Seoul`. 요일 배열 `["일","월","화","수","목","금","토"]`(앱과 동일).

---

## 7. 인증 상세 (`auth/` + `common/security`)

### 7.1 Supabase Auth 를 쓰는 방식 (OTP 는 Spring)
- 앱 → Spring 만. Spring 이 Supabase Auth REST 를 **service_role 키**로 호출한다 (`common/supabase/SupabaseAuthClient`, Spring `RestClient`). 엔드포인트: `/auth/v1/admin/users`(생성·비밀번호 변경·삭제), `/auth/v1/token?grant_type=password|refresh_token`, `/auth/v1/logout`.
- **휴대폰 인증번호는 Spring 이 만든다** (`auth/OtpService`): 6자리 `SecureRandom`, `phone_verifications` 에 `SHA-256(code + OTP_SALT)` 저장, 5분 만료, 5회 오답 폐기, 검증 성공 시 15분짜리 `verificationToken`. 문자는 `common/sms/SmsSender` 로 보낸다 — 기본 구현 `SolapiSmsSender`(REST, `SMS_API_KEY/SECRET/SENDER`), 업체를 바꾸면 구현체 하나만 추가하고 `application.yml` 의 `sms.provider` 를 바꾼다. local 프로필은 `LoggingSmsSender`(콘솔 출력 — 실기기 테스트 때만 진짜 발송).
- Supabase 대시보드 설정(B0, `runbook.md` 에 기록): **Phone provider 끔, Email provider 끔**(가입은 admin API 로만), "Allow new users to sign up" 끔, JWT 만료 3600s, Refresh token rotation 켬, JWT Signing Keys 비대칭(ES256). **anon key 는 어디에도 배포하지 않는다**.
- 앱 토큰 보관: `session.js` — `localStorage`(`ansimclean.<app>.auth` = `{ accessToken, refreshToken, expiresAt, user }`). Capacitor 앱에서는 WebView 저장소가 앱 샌드박스 안이라 우선 이대로, 나중에 `@capacitor/preferences` 로 옮기는 항목을 §13-10 에.

### 7.2 Spring 의 JWT 검증 (`SupabaseJwtDecoderConfig`)
- Supabase 프로젝트의 **JWT Signing Keys** 를 비대칭(ES256, 기본값)으로 쓰고 `spring.security.oauth2.resourceserver.jwt.jwk-set-uri = ${SUPABASE_URL}/auth/v1/.well-known/jwks.json`, `issuer = ${SUPABASE_URL}/auth/v1`, `audience = authenticated`. (구형 프로젝트가 HS256 이면 `NimbusJwtDecoder.withSecretKey(SUPABASE_JWT_SECRET)` — `application.yml` 의 `auth.jwt.mode = jwks|secret` 로 고른다.)
- 검증 후 `CurrentUser { id(sub), role, profileComplete }`: role 은 토큰이 아니라 **`profiles.role` 을 DB 에서 읽는다**(요청당 1쿼리, 1분 캐시). `profiles` 가 없거나 `deleted_at` 이 있으면 401. `signup_completed_at` 이 null 이면 `/auth/signup/complete`·`/auth/me`·`/auth/logout`·`/customer/profile/{address,housing}` 만 허용(가입 이어가기), 나머지는 403.
- `SecurityConfig`: `permitAll` = `/auth/phone/**`, **`POST /auth/signup`(정확히 이 경로 — 아직 토큰이 없는 신규 사용자)**, `/auth/login`, `/auth/refresh`, `/terms/**`, `/catalog/**`, `/support/faq`, `/support/notices/**`, `/live/webhooks/**`, `/actuator/health`. `/auth/signup/complete`·`/auth/me`·`/auth/logout`·`/auth/account` = 인증만. `/customer/**` = `ROLE_CUSTOMER`, `/manager/**` = `ROLE_MANAGER`, `/live/**`·`/support/inquiries`·`/push/**` = 인증 + 서비스에서 소유권. CSRF 끔(토큰 인증), 세션 stateless, CORS = `ALLOWED_ORIGINS`(§12).
- 속도 제한: `auth/OtpRateLimiter`(메모리, 번호당 60초 1회·시간당 5회) · `LoginRateLimiter`(번호당 시간당 10회). 단일 인스턴스 전제(§13-11).

### 7.3 가입 흐름 (앱 화면 순서 그대로)
```
고객: SignUp(전화) → TermsConsent(체크 → sessionStorage 초안) → ProfileInfo(휴대폰 "인증하기" → POST /auth/phone/otp → [새] 인증번호 필드 → POST /auth/phone/verify → verificationToken 을 초안에)
      → "다음" = POST /auth/signup(verificationToken, name, nickname, password) → 토큰 저장(profileComplete=false) → ProfileAddress = PUT /customer/profile/address → ProfileHousing = PUT /customer/profile/housing
      → SignUpComplete "지금 시작하기" = POST /auth/signup/complete(agreedTermIds) → profileComplete=true → /home
매니저: SignUpTerms(초안) → SignUpInfo(인증하기 → OTP → verify → "다음" = POST /auth/signup(verificationToken, name, password) → 토큰 저장) → SignUpComplete "시작하기" = POST /auth/signup/complete → /
```
- `RequireAuth` 의 `isSignedIn()` = `accessToken 있음 ∧ user.profileComplete`.
- **가입 재개(신규 가입과 분리)**: `GET /auth/me` 와 로그인 응답의 `user` 에 `signupStep` 을 넣는다 — 서버가 무엇이 있는지 보고 정한다: 고객: 기본 주소 없음 → `"address"`, 주거 정보 없음 → `"housing"`, `signup_completed_at` 없음 → `"complete"` / 매니저: `"complete"` / 완료면 `null`. 앱은 `signupStep` 에 맞는 화면(`/auth/profile-address` · `/auth/profile-housing` · `/auth/complete`, 매니저 `/auth/signup/complete`)으로 보내고 **OTP·기본정보 화면은 다시 거치지 않는다**(토큰이 있으므로).
- 이미 계정이 있는(가입 중이든 완료든) 번호로 `POST /auth/phone/otp` 를 부르면 `PHONE_TAKEN` + 문구 "이미 가입한 번호예요. 로그인해서 이어서 진행해주세요" → 앱은 로그인 화면으로. 로그인은 `profileComplete=false` 여도 성공하고 `signupStep` 으로 이어간다.
- 매니저 `approved=false` 여도 로그인 가능. 콜·일정은 비어 있고 홈 `today.count=0`. 승인 전 안내는 프로필 `신원 인증` 항목 값으로 보인다(`identity: 본인 인증 완료 / 범죄경력 조회 대기 …`).

---

## 8. 라이브 상세 (`live/`) — 바디캠 → LiveKit Ingress → 고객 시청 + Egress 녹화

### 8.1 구성
- **바디캠 1대 = 매니저 1명 = LiveKit Ingress 1개(영구).** 운영자가 기기를 등록하면(`manager_devices`, §13-20) Spring 이 `IngressServiceClient.createIngress(RTMP_INPUT, name="device-<serial>", roomName="parking-<managerId>", participantIdentity="camera-<managerId>", participantName="<매니저 이름> 바디캠", enableTranscoding=true, reusable=true)` 로 Ingress 를 만들고 `ingress_id`·`stream_url`(`rtmp://<project>.livekit.cloud/x`)·`stream_key` 를 저장한다. 기기에는 이 주소와 키를 **한 번만** 설정한다(매니저 앱 `촬영 장비` 화면에 주소 + 마스킹된 키 + "운영자에게 전체 키 요청" 안내).
- **대기 방 규칙**: 작업이 없을 때 Ingress 는 항상 `parking-<managerId>` 방을 가리킨다. 이 방의 토큰은 아무에게도 발급하지 않고 Egress 도 없다 → 매니저가 실수로 카메라를 켜 두어도 아무도 못 보고 저장되지 않는다.
- **작업 시작(`POST /live/sessions/{id}/start`)**: (1) 오늘의 `scheduled|in_progress` 확인 (2) `IngressServiceClient.updateIngress(ingressId, roomName="booking-<id>")` — 바디캠이 **이미 대기 방으로 송출 중**이면 LiveKit 이 활성 Ingress 의 변경을 거부한다 → `cameraBusy=true` 로 응답하고 앱이 "바디캠을 껐다가 다시 켜주세요" 를 보여준 뒤 3초 폴링으로 재시도(서버는 폴링 요청마다 update 를 다시 시도). LiveKit Cloud 의 `RoomService.moveParticipant`(방 간 이동)가 프로젝트에서 쓸 수 있으면 그것으로 대체해 재시작 없이 옮긴다 — B5 에서 확인 (3) `RoomServiceClient.createRoom(name, emptyTimeout=6h, departureTimeout=6h)`(비어 있어도 사라지지 않게) (4) `live_sessions.status=waiting_camera`, `ingress_id` 저장 (5) 매니저 미리보기 viewer 토큰 반환. **Egress 는 여기서 시작하지 않는다** — 카메라가 붙기 전의 빈 화면을 녹화하지 않도록 `ingress_started` 에서 시작한다(§8.2). 매니저는 바디캠 전원/송출 버튼을 켠다.
- **바디캠이 붙으면** 웹훅 `ingress_started`(room=`booking-<id>`) → `camera_track_sid` 저장, `mute_pending` 이면 즉시 mute, **Egress 가 없으면 시작**(§8.2), `live_sessions.status=live`(단, 작업이 `paused` 면 `paused`), `camera_online_at`, `manager_devices.stream_status=online` → `CameraOnline` 이벤트 → 첫 연결이면 고객 푸시 "청소가 시작됐어요 · 실시간 영상 보기". 모든 웹훅은 `livekit_events.id` 로 중복을 걸러내고, 상태 전이는 "현재 상태에서 허용될 때만" 적용한다(순서가 뒤바뀌어 와도 안전). 앱의 `GET /live/sessions/{id}` 폴링(3초)이 `live` 를 보면 Job/InProgress 의 "실시간 촬영 중" 표시.
- **바디캠이 끊기면**(`ingress_ended` 인데 작업이 아직 `in_progress`) → `status=waiting_camera`, `stream_status=offline`, 매니저 푸시 "바디캠 연결이 끊겼어요". 작업 상태는 그대로(청소는 계속). 카메라 대기가 5분을 넘으면(기준은 끊긴 시각 — 작업을 시작하고 한 번도 붙지 않았으면 세션 시작, 일시정지 뒤 재개했으면 재개 시각) 재조정기(`LiveReconciler`)가 매니저에게 푸시 "바디캠이 아직 연결되지 않았어요 · 5분 넘게 연결되지 않았어요. 바디캠 전원과 네트워크를 확인해주세요" 를 대기 구간마다 한 번(`camera_wait_notified_at`) 보내고 WARN 로그를 남긴다(문제 목록 16번). 작업이 일시정지 중이거나 매니저가 영상을 멈춘 채면 보내지 않는다.
- **일시정지/재개**: `RoomServiceClient.mutePublishedTrack(room, "camera-<managerId>", camera_track_sid, true/false)`. 카메라가 아직 안 붙었으면(`waiting_camera`) `mute_pending=true` 만 두고 붙는 순간 적용. 영상만 송출(결정 §13-23)이라 트랙은 하나다. Egress 는 계속 돌지만 mute 구간은 검은 화면(고객·녹화 모두). **B5 첫날에 ffmpeg 스트림으로 Ingress 참가자 트랙의 서버측 mute 가 실제로 유지되는지 확인**한다. 안 되면 대안: 일시정지 = Egress 정지 + 고객 viewer 참가자 `removeParticipant` + viewer 토큰 거부, 재개 = 새 Egress 시작(녹화본이 여러 파일 → `recording_parts` 로 저장하고 재생 화면은 순서대로 이어 재생). 어느 쪽이든 **일시정지 구간은 고객이 볼 수도, 녹화될 수도 없어야 한다**(사유가 '화장실 이용' 인 경우). 구현 보강(재감사 H-02, 2026-09-24): 일시정지는 mute **전에** 방에 있는 고객 참가자의 `canSubscribe` 를 끄고(`live_sessions.viewers_blocked`, V030), 확인된 재개 뒤에 연다. 막을지는 **작업(예약)의 일시정지**도 본다 — 일시정지 중 카메라가 끊겨 세션이 `waiting_camera` 가 돼도 다시 열지 않는다(재감사 2차). 옛 토큰으로 다시 들어온 고객은 `participant_joined` 에서 바로 막고, 재조정기가 1분마다 **참가자마다 실제 권한**(`ParticipantInfo.permission.canSubscribe`)을 뜻과 비교해 어긋난 사람을 다시 고친다(한 명 막기 실패도 재시도). 일시정지·mute 미확인 중에는 새 시청 토큰과 새 녹화를 주지 않는다. **남은 틈**: 재접속한 트랙이 mute 되기 전의 프레임이 실행 중인 녹화(room composite)에 들어갈 수 있다 — 실기기 격리 시험 필요 `job_pauses` 와 `status=paused` 는 `job/*/pause` 가 기록한다.
- **완료(`stop`)**: `EgressServiceClient.stopEgress(egressId)` → `RoomServiceClient.deleteRoom(room)` → Ingress 잠금 해제 → **바디캠이 이 방에 묶여 있을 때만**(`getIngress` 의 방이 이 방이거나 `pending_room` 이 이 방) `updateIngress(ingressId, roomName="parking-<managerId>")`(활성 상태면 실패해도 무시하고 다음 기기 재시작 때 적용되도록 `manager_devices.pending_room='parking-<managerId>'` 표시 → `ingress_ended` 웹훅에서 적용) → `status=ended`. 같은 매니저의 다른 작업이 진행 중이거나 바디캠이 다른 방에 묶여 있으면(조회 실패 포함) 바디캠은 건드리지 않는다 — 늦게 돈 정리가 다음 작업의 잠금을 풀거나 다음 작업 방의 바디캠을 대기 방으로 빼지 않게 (자체 점검 #1·#51, `LiveService.releaseDevice`).
- 매니저 미리보기: 매니저 앱 `liveClient.start()` 는 **송출하지 않고** `preview` 토큰으로 방에 viewer 로 들어가 `<VideoSurface>` 에 붙인다(Job/InProgress 의 작은 미리보기·Job/Preparing 의 "카메라 연결됨" 확인용). 휴대폰 카메라·마이크 권한은 더 이상 필요 없다 → `촬영 장비 › 카메라 테스트` 와 Info.plist/Manifest 의 CAMERA·RECORD_AUDIO 권한을 제거한다(B5 항목, PROGRESS 기록).
- **바디캠 없이 개발·검증하는 법**: `ffmpeg -re -stream_loop -1 -i sample.mp4 -c:v libx264 -preset veryfast -b:v 2M -c:a aac -f flv rtmp://<ingress-url>/<stream-key>` 또는 OBS 로 노트북이 바디캠 역할을 한다. B5 의 서버·앱·웹훅·녹화·일시정지 검증은 전부 이걸로 끝내고, 실제 기기는 '주소·키 설정 + §8.3 검증 5항목' 만 남는다. 기기 사양 답이 늦어도 B0~B4·B6~B8 은 영향이 없다.

### 8.2 녹화
`ingress_started` 처리 안에서(카메라가 실제로 붙었을 때) `startRoomCompositeEgress(room, EncodedFileOutput(MP4, S3Upload(endpoint=SUPABASE_STORAGE_S3_ENDPOINT, bucket=recordings, key=recordings/<bookingId>.mp4, accessKey/secret=Storage S3 access key, region=ap-northeast-2, forcePathStyle=true)), layout="single-speaker", videoOnly=true)`. Supabase Storage 는 S3 프로토콜을 지원한다(대시보드 › Storage › S3 access keys). `egress_id` 저장, `recording_status=recording`. 카메라가 끊겼다 다시 붙어도 Egress 는 하나(방 합성이라 계속 녹화, 끊긴 구간은 검은 화면). 웹훅 `egress_ended` → `ready`(+`duration_seconds`, `recording_path`) 또는 `failed`. 고객 재생: `GET /live/sessions/{id}/recording` → `SupabaseStorageClient.signedUrl("recordings", path, 21600)`(6시간 — 3~4시간 녹화본을 한 번에 끝까지 봐도 끊기지 않게, `LiveService.RECORDING_URL_TTL_SECONDS`) → 앱 `<video src>` + `currentTime` 으로 15초 탐색. HLS 는 필요해지면 `SegmentedFileOutput` 추가(§13-12). 보존 **완료 후 90일**(`app_settings.recording_retention_days`) 뒤 `@Scheduled` 일 1회 삭제 — 약관 `recording` 문안과 일치시킨다.

### 8.2.1 일시정지의 책임과 부분 실패 (한 곳에만 적는다)
| 단계 | 누가 | 실패하면 |
|---|---|---|
| ① `POST /live/…/pause` — 영상 차단 확인 | `live/LiveService.pause`(mute + 확인 폴링) | 앱은 시트에 "촬영을 중단하지 못했어요. 영상이 계속 전송되고 있으니 다시 시도" 를 보이고 **일시정지 화면으로 가지 않는다**. 서버는 `mute_pending=true` 로 두지만, 작업이 진행 중이면 `LiveReconciler` 는 그 요청을 버리고 영상을 되돌린다(매니저에게 '계속 전송' 으로 안내했으므로). 작업이 일시정지면 재시도해 막는다 |
| ② `POST /manager/jobs/…/pause` — 작업 상태 | `job/JobService.pause`(선행 조건 ①) | ①은 이미 됐으므로 영상은 막혀 있다(안전한 쪽). 서버는 **매니저가 재개할 때까지**(`live/resume` 또는 `jobs/resume`) 막아 둔다 — 바디캠이 끊겼다 다시 붙어도(`live_sessions.video_held`, V045, 2026-09-28 사용자 결정). 앱은 문구 후 재시도(멱등), 시트를 닫으면 `live/resume` 으로 영상을 다시 켠다 |
| ③ 화면 이동 | 앱 | — |
| 재개 ① `POST /manager/jobs/…/resume` | `JobService.resume` | 아무것도 안 바뀜, 재시도 |
| 재개 ② `POST /live/…/resume` | `LiveService.resume`(unmute + 확인) | 상태는 진행 중, 영상은 막힘 → 고객은 "잠시 중단" 화면 유지, 앱 문구 + `LiveReconciler` 재시도 |
원칙: **영상이 나가는데 상태가 '일시정지' 인 순간은 없다**(반대는 잠깐 허용). **매니저가 '멈췄다' 는 답을 받은 영상은 매니저가 재개하기 전에는 다시 나가지 않는다** — 재접속·재조정기·웹훅·Ingress 잠금·고객 상태 문구가 모두 `LiveService.holdsVideo`(작업 일시정지 ∨ `video_held`) 한 기준을 쓴다. `liveClient.pause()/resume()` 은 §5.4 의 live 엔드포인트만 부르고 작업 상태는 부르지 않는다 — 화면(`JobInProgress`·`JobPaused`)이 위 순서대로 둘을 부른다(2차 점검에서 그렇게 고쳤다).

### 8.3 바디캠 요구 사양 (§13-19 — 기기 확정 전 확인할 것)
| 필요 | 이유 | 없으면 |
|---|---|---|
| **RTMP(S) 푸시** (주소 + 스트림 키 설정 가능) | LiveKit Ingress 입력 | WHIP 지원 기기면 `WHIP_INPUT` 으로 동일 구조. **RTSP 만** 되는 기기면 중계 서버(ffmpeg 를 Spring 이 프로세스로 띄워 RTSP→RTMP)가 필요 — 별도 항목 |
| H.264 **영상만**(음성 트랙 없음 또는 끔), 720p 이상, 1.5–3 Mbps | 영상만 송출(결정 §13-23). 기기가 음성을 끄지 못하면 Ingress 는 받되 Egress `videoOnly` + 고객 토큰에서 오디오 구독 불가(`canSubscribe` 는 두고 서버가 오디오 트랙을 `mutePublishedTrack` 로 항상 mute — 카메라가 붙을 때와 재조정기 매 주기, 2026-09-24 구현) | 기기 설정에서 조정 |
| LTE/5G 또는 테더링 | 고객 집 Wi-Fi 에 의존하지 않음 | 매니저 휴대폰 핫스팟 |
| (선택) 배터리·네트워크 상태 API 또는 앱 SDK | 장비 화면의 배터리 %·네트워크 | "확인 필요" 표시 (이미 설계에 반영) |
| 연속 4시간 이상 송출 | 작업 길이 | 보조 배터리 |

실기기 검증 항목(B5 완료 기준): 바디캠 송출 → 매니저 미리보기 + 고객 시청 지연 3초 이내 · 일시정지(검은 화면)/재개 · 바디캠 전원 껐다 켜기(waiting_camera → live 복귀) · 완료 후 3분 안에 녹화본 재생 · 매니저 앱 백그라운드에서도 송출 유지(휴대폰과 무관하므로 당연히 유지 — 확인만).


## 구현 현황
- ✅ B5: §8 `live/` — LiveKit 은 `LiveKitGateway` 뒤(`cloud` = 서버 SDK, `logging` = 카메라가 붙지 않는 대역, `app.livekit.mode`). Ingress 는 RTMP 입력·transcoding, 방은 empty/departure timeout 6시간, Egress 는 방 합성 MP4 **영상만** → Supabase Storage S3(`recordings/<bookingId>.mp4`). 일시정지는 `mutePublishedTrack` 뒤 `getParticipant` 폴링으로 확인(§8.2.1) — 확인 실패는 `mute_pending`/`unmute_pending`(V008) 으로 남기고 `LiveReconciler`(1분) 가 재시도. 웹훅 6종은 `livekit_events` 로 중복 방어, 전이는 현재 상태에서 허용될 때만. `ingress_ended` 와 재조정기가 `pending_room` 을 적용(바디캠이 송출 중이라 못 옮긴 방). `RecordingRetention`(04:00) 이 완료 후 90일 지난 녹화본 삭제. 판단: LiveKit 제어 호출(start·pause·resume·stop·녹화 시작)은 확인이 필요해 **동기**로 부르고 실패는 재조정기가 메운다(아웃박스는 푸시만) / 재개 확인 실패는 별도 `unmute_pending` 열로 표시해 "live/pause 뒤 jobs/pause 전" 의 짧은 창에서 재조정기가 잘못 unmute 하지 않게 함 / 카메라 대기 5분 알림은 `camera_wait_notified_at` 으로 1회 / 고객 화면 매니저 소개 문구는 코드에서 정함.
- ✅ B4: §6.2 `offer/OfferService`(= `BookingDispatcher` 구현, `@Primary`) — 후보 = approved ∧ `areas` 에 '시도 시군구'(시군구 없으면 시도) ∧ `AvailabilityService.isAvailable`(요일 근무시간·근무불가 반복 펼침) ∧ `BookingService.hasOverlappingWork`; 수락은 **매니저 행 → 예약 → 오퍼** 순서로 `select … for update`, 제약 위반은 flush 에서 `OFFER_TAKEN`; 우선 오퍼는 `offers.preferred`(V007) 로 표시하고 거절·만료 시 같은 회차에서 그 매니저만 빼고 일반 매칭; 일반 오퍼가 모두 닫히면 다음 회차 재매칭(이미 받은 매니저 전부 제외)을 `app_settings.max_dispatch_rounds`(3) 회차까지 — 그 뒤·후보 없음은 WARN 로그(§13-8). `OfferExpirer` 30초. §6.1 매니저 전이는 `job/JobService`(start 는 당일·다른 활성 작업 없음, pause 는 `live_sessions.status ≠ live` 선행 조건, complete 는 같은 tx 에서 `SettlementService.createFor` + `completed_count+1` + `BookingCompleted`). §6.3 화면 값(`JobService`), §6.5 `settlement/`(other_fee 0 은 "0원"), §6.6 `managerhome/`. `live/` 는 B4 에서 세션 upsert·리셋·장비 상태 읽기만(LiveKit 은 B5). 재배정(`onRescheduled`)은 accepted→superseded, pending→cancelled, 체크리스트 삭제, 라이브 세션 리셋 뒤 우선 오퍼.
- ✅ B3: §6.1 `Booking` 전이 메서드 전부(`beginAssigning`·`assign`·`start`·`pause`·`resume`·`complete`·`cancel`·`cancelBySystem`·`reschedule`) — B4 는 부수 효과(체크리스트·live_sessions·정산)만 붙인다. §6.4 `catalog/PricingService`(수수료 HALF_UP). §6.8 `BookingSeriesService`(전체 취소·회차 매칭·종료·종료 안내) + `SeriesDispatcher`(매일 03:00 — 2026-09-28 09:00 으로 옮김) + `StaleBookingCleaner`(03:10). 매칭은 `booking/BookingDispatcher` 인터페이스로 — B3 는 `NoopBookingDispatcher`(로그), B4 의 `OfferService` 가 `@Primary` 로 구현. 스케줄러는 계획의 `common/scheduling` 이 아니라 **각 기능 패키지 안**에 둔다(`common` 은 기능 패키지를 볼 수 없다 — ArchitectureTest). 푸시 이벤트(`BookingCancelled`·`BookingRescheduled`·`BookingUnassigned`·`SeriesEnding`)는 `push/PushHandler` 가 받되 B6 전에는 로그만. 결제는 `payment/PaymentGateway` + `NoopPaymentGateway`(구조만, 호출 지점은 `BookingService` 의 `// PAYMENT:`).
- ✅ B1: `KoreanFormat` 전 메서드(§6.7). 체크리스트 템플릿은 `home` 만 seed — 다른 카테고리는 배정 시 `home` 템플릿을 대신 쓴다(B4, 판단 항목).
- **전체 검수 2차 (2026-09-21)**: `live_sessions` 는 상태를 바꾸는 모든 경로(pause/resume/stop·웹훅·재조정기·`jobs/pause` 선행 조건)가 `FOR UPDATE` 로 읽는다(`LiveSessionRepository.lockById/lockByRoomName`); 재조정기는 세션마다 별도 트랜잭션 + `SKIP LOCKED`(`lockByIdIfFree`) · mute 를 보냈지만 확인하지 못하면 `live + mute_pending`(`LiveSession.muteUnconfirmed`) — `paused + mute_pending` 은 `mayBeStreaming()=false` 라 `jobs/pause` 가 통과해 §8.2.1 을 깬다 · 재조정기: `in_progress ∧ paused(트랙 muted) ∧ pending 없음 → unmute`(계획 §9.2), 트랙 sid 가 DB 와 다르면 갱신 · 예약 잠금 순서 **시리즈 → 예약 id 오름차순**(회차 취소·전체 취소·`endSeriesIfNothingLeft`·`OfferExpirer`·`SeriesDispatcher`) · 탈퇴는 프로필 잠금 → 가드 → 익명화를 한 트랜잭션에서(매니저는 `manager_profiles` 가 있을 때만 잠금) · OTP 검증은 번호 advisory lock, 바디캠 등록은 매니저 advisory lock · 아웃박스는 커밋 뒤 `OutboxQueued` 로 즉시 한 번(배치는 한 번에 하나) · `AddressRegion`: '광주시' 는 다음 토큰으로 광주광역시/경기도 광주시 판별 · 옵션 key 는 검증·가격·저장 전부 중복 제거 · `LiveSession.isRecordingPending()` 은 `created` 를 대기로 보지 않는다
- **전체 검수 1차 (2026-09-20)**: 스케줄러 트랜잭션(TransactionTemplate)·라이브 mute 확인 규칙(`LiveSession.mayBeStreaming`, 카메라 재접속은 확인 뒤에만 paused, 진행 중 작업의 stop 거절)·탈퇴 매니저 승인 해제·주소 시/도 정규화(`AddressRegion.canonicalSido`)·고객 취소/변경 잠금·정기 회차 변경 규칙(기간 밖 거부·즉시 매칭)·인증(ES256, OTP 오답 카운트, 가입 토큰 소비 순서, 탈퇴 잔재, IP 한도, 04:10 정리). 자세한 목록은 `PROGRESS.md`
