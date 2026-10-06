package kr.ansimclean.api.push;

/**
 * 푸시 문구 — Figma 에 푸시 화면이 없어 코드에서 정함 (BACKEND_PLAN.md §6·§8 의 표현을 그대로: "매니저가 배정됐어요", "청소가 시작됐어요 · 실시간 영상 보기",
 * "바디캠 연결이 끊겼어요", "정기청소가 곧 끝나요"). 디자인이 오면 여기만 바꾼다. PROGRESS.md 판단 항목.
 * public 이 아니라 같은 push 패키지 안에서만 보인다 — 예: PushHandler 가 이벤트마다 문구를 고른다.
 * 각 상수 위의 '← 이벤트' 는 PushHandler 에서 그 문구를 고르는 아웃박스 이벤트다.
 * SERIES_ENDING 본문은 정기청소를 매월 1회로 바꿀 때(2026-09-22) '마지막 방문이 다가와요 …' 로 바꿨다 —
 * BACKEND_PLAN.md §6.8·docs/domain.md 에는 예전 본문('다음 달도 예약해 보세요')이 남아 있다.
 */
final class PushCopy {
    private PushCopy() {} // 상수만 모아 둔 클래스라 객체를 만들지 않는다

    // 알림 한 건의 제목(title)·본문(body)
    record Text(String title, String body) {}

    // 매니저
    // ← OffersDispatched (우선 오퍼가 아닌 보통 콜)
    static final Text NEW_CALL = new Text("새 콜이 도착했어요", "새로운 청소 요청을 확인해보세요");
    // ← OffersDispatched (preferred = 우선 오퍼 1명). 정기청소 다음 회차를 직전 매니저에게 먼저 줄 때(OfferService.dispatchPreferred)만 온다 —
    // 일정 변경으로 원래 매니저에게 가는 우선 오퍼는 이 이벤트를 적지 않고 아래 BOOKING_RESCHEDULED 가 안내한다 (문제 목록 15번)
    static final Text PREFERRED_CALL = new Text("단골 고객의 정기청소 콜이 도착했어요", "이전에 맡았던 고객이에요. 먼저 확인해보세요");
    // ← BookingCancelled (고객 취소·정기청소 해지 — 배정된 매니저, 또는 밤에 일정을 바꿔 안내를 미뤄 둔 원래 매니저에게)
    static final Text BOOKING_CANCELLED = new Text("예약이 취소됐어요", "고객이 예약을 취소했어요. 일정을 확인해주세요");
    // ← BookingCancelled 의 reason = not_started (StaleBookingCleaner 가 미방문으로 취소)
    static final Text BOOKING_NOT_STARTED = new Text("방문하지 않은 예약이 취소됐어요", "방문일이 지나도록 청소를 시작하지 않아 예약이 취소됐어요. 문의가 있으면 고객센터로 알려주세요"); // 코드에서 정함 (검수 4차)
    // ← BookingRescheduled (이전 매니저에게 우선 오퍼를 다시 보냈거나, 그 값이 없는 예전 이벤트)
    static final Text BOOKING_RESCHEDULED = new Text("예약 일정이 변경됐어요", "고객이 일정을 바꿔 다시 매칭돼요. 새 콜을 확인해주세요");
    /** (코드에서 정함) 바뀐 시간에 우선 오퍼를 줄 수 없을 때(근무 불가·겹치는 작업·정지) — 콜 목록에 없는 콜을 찾게 하지 않는다 (자체 점검 #18·#56) */
    static final Text BOOKING_RESCHEDULED_RELEASED = new Text("예약 일정이 변경됐어요", "고객이 일정을 바꿔 이 예약은 다른 매니저에게 다시 배정돼요");
    // ← BookingCompleted (매니저에게, 정산 알림 설정도 켜져 있을 때)
    static final Text SETTLEMENT_CREATED = new Text("정산이 등록됐어요", "완료한 청소의 정산 내역을 확인해보세요");
    // ← CameraOffline (reason 이 waiting 이 아닐 때) / 아래 CAMERA_WAITING ← CameraOffline 의 reason = waiting
    static final Text CAMERA_DISCONNECTED = new Text("바디캠 연결이 끊겼어요", "촬영이 멈췄어요. 바디캠 전원과 네트워크를 확인해주세요");
    // waiting 은 LiveReconciler 가 대기 시작(LiveSession.cameraWaitSince — 작업 시작, 작업 중 끊긴 시각, 일시정지 뒤 재개한 시각)부터
    // 5분이 지나면 대기 구간마다 한 번 보낸다. 그래서 본문은 기준 시점을 말하지 않는다 (코드에서 정함, 문제 목록 16번 — 예전 '작업을 시작한 지 5분이 지났어요' 는
    // 끊긴 뒤·재개 뒤에는 틀렸다). 작업 중 끊겼으면 매니저는 '바디캠 연결이 끊겼어요' 를 받고 5분 뒤 이 알림을 한 번 더 받는다
    static final Text CAMERA_WAITING = new Text("바디캠이 아직 연결되지 않았어요", "5분 넘게 연결되지 않았어요. 바디캠 전원과 네트워크를 확인해주세요");
    /** 매니저 정책 (docs/manager-policy.md, 사용자 승인 2026-09-22) — 점수는 body 에 채워 넣는다 */
    static final Text PENALTY_RECORDED = new Text("패널티가 기록됐어요", "MY › 패널티 내역에서 확인해주세요");
    // ← ManagerPenalized 의 stage = warn (이번 기록으로 경고 단계, 기본 3점을 넘음). 코드에서 정함 — docs/manager-policy.md §3 '3점 = 경고 알림',
    // 패널티 내역 화면의 '3점이 되면 경고 알림을 보내드려요' 가 약속한 알림이다 (문제 목록 14번, 사용자 결정 A).
    // 본문: %s = 누적 점수, 다음 두 %s = 그 위의 첫 정지 단계 점수·일수(설정값). 정지 단계가 없으면 PENALTY_WARNING_BODY_NO_NEXT
    static final String PENALTY_WARNING_TITLE = "패널티 경고";
    static final String PENALTY_WARNING_BODY = "누적 %s점이에요. %s점이 되면 %s일 동안 콜 배정이 정지돼요";
    static final String PENALTY_WARNING_BODY_NO_NEXT = "누적 %s점이에요. MY › 패널티 내역에서 확인해주세요";
    // ← ManagerPenalized 의 stage = suspend (이번 기록으로 정지 단계, 기본 5점·8점을 넘음). 코드에서 정함 — 정책 §3 '정지 기간에는 새 콜을 받지 못합니다.
    // 이미 수락한 방문은 그대로 진행해주세요' 를 따른다 (문제 목록 14번). 본문: 누적 점수, 정지 마지막 날("9월 29일", 그날까지 정지)
    static final String PENALTY_SUSPENDED_TITLE = "콜 배정이 정지됐어요";
    static final String PENALTY_SUSPENDED_BODY = "누적 %s점으로 %s까지 새 콜을 받을 수 없어요. 이미 수락한 방문은 그대로 진행해주세요";
    // 방문 취소 뒤 다시 매칭할 후보가 한 명도 없어 곧바로 매칭 실패가 되면 JobService.doCancelByManager 가 이 알림을 적지 않는다 —
    // 고객은 MatchingFailed('매니저를 찾지 못했어요') 하나만 받는다 (문제 목록 5번)
    static final Text MANAGER_RELEASED = new Text("매니저 배정이 취소됐어요", "다른 매니저를 찾고 있어요. 배정되면 바로 알려드릴게요"); // ← ManagerReleased. 받는 사람은 고객이다

    // 고객
    static final Text MANAGER_ASSIGNED = new Text("매니저가 배정됐어요", "예약 카드에서 배정된 매니저를 확인해보세요"); // ← BookingAssigned
    static final Text CLEANING_STARTED = new Text("청소가 시작됐어요", "실시간 영상 보기"); // ← CameraOnline (첫 연결일 때만)
    static final Text CLEANING_DONE = new Text("청소가 완료됐어요", "청소 기록과 영상을 확인해보세요"); // ← BookingCompleted
    // ← BookingCancelled 의 reason = not_started (매니저 미방문으로 시스템이 취소)
    static final Text NOT_VISITED = new Text("매니저가 방문하지 않았어요", "예약이 취소됐어요. 불편을 드려 죄송해요. 다른 일정으로 다시 예약해주세요"); // 코드에서 정함 (검수 4차)
    static final Text UNASSIGNED = new Text("매니저를 배정하지 못했어요", "예약이 취소됐어요. 다른 일정으로 다시 예약해주세요"); // ← BookingUnassigned
    // ← InquiryAnswered. 고객 묶음에 있지만 문의한 사람이면 매니저도 이 문구를 받는다
    static final Text INQUIRY_ANSWERED = new Text("문의에 답변이 도착했어요", "1:1 문의에서 답변을 확인해주세요"); // 코드에서 정함
    // ← ManagerLate. 본문의 %d 에 PushHandler 가 String.format 으로 분을 채운다
    /** (코드에서 정함) 약속 시간 + 지각 기준이 지나도 시작하지 않았을 때 고객에게 — {분} 은 지각 기준(tardyMinutes) (자체 점검 #25) */
    static final String MANAGER_LATE_TITLE = "매니저가 늦어지고 있어요";
    // '넘게' — 서버가 멈췄다가 따라잡아 보낼 때도(최대 12시간 뒤) 사실이 되게 (자체 점검 후속 검토)
    static final String MANAGER_LATE_BODY = "약속 시간이 %d분 넘게 지났어요. 도착이 더 늦어지면 고객센터로 알려주세요";
    // 코드에서 정함. 가능한 매니저가 새로 생기면 매시 자동으로 다시 찾고(MatchingRetry, 사용자 결정 2026-09-27), 일정을 바꾸면 바로 다시 매칭된다.
    // 예전 '계속 찾고 있어요' 는 아무도 다시 찾지 않을 때라 사실이 아니었다 (자체 점검 #13)
    // ← MatchingFailed. 아래 네 문구는 retryable(자동 재매칭 가능)·changeable(일정 변경 가능) 조합으로 고른다:
    // 둘 다 → MATCHING_FAILED, 재매칭만 → RETRY_ONLY, 변경만 → CHANGE_ONLY, 둘 다 아님 → FINAL
    // '일정을 바꾸면 바로 …' 에서 '바로' 를 뺐다 — 밤(21시~08시)에 바꾸면 급하지 않은 예약은 아침 08:05 에 찾는다 (코드에서 정함, 합친 38건 검토 R15)
    static final Text MATCHING_FAILED = new Text("매니저를 찾지 못했어요", "가능한 매니저가 생기면 다시 찾아볼게요. 일정을 바꾸면 다시 찾아요");
    // 가능한 길만 말한다 — 자동 재매칭이 못 보는 예약(밤·약속 3시간 안), 일정을 바꿀 수 없는 예약(24시간 안) (코드에서 정함, 자체 점검 후속 검토)
    static final Text MATCHING_FAILED_RETRY_ONLY = new Text("매니저를 찾지 못했어요", "가능한 매니저가 생기면 다시 찾아볼게요. 1:1 문의로 알려주셔도 돼요");
    static final Text MATCHING_FAILED_CHANGE_ONLY = new Text("매니저를 찾지 못했어요", "다른 날짜·시간으로 바꾸면 다시 찾아요. 1:1 문의로 알려주셔도 돼요");
    static final Text MATCHING_FAILED_FINAL = new Text("매니저를 찾지 못했어요", "방문 시간까지 매니저를 찾지 못할 수 있어요. 1:1 문의로 알려주세요");
    static final Text SERIES_ENDING = new Text("정기청소가 곧 끝나요", "마지막 방문이 다가와요. 다음 정기청소를 예약해 보세요"); // ← SeriesEnding
    static final Text RECORDING_READY = new Text("청소 영상이 준비됐어요", "예약 카드에서 녹화 영상을 볼 수 있어요"); // ← RecordingReady
    // ← RecordingFailed
    static final Text RECORDING_FAILED = new Text("녹화 영상을 저장하지 못했어요", "불편을 드려 죄송해요. 문의하시면 도와드릴게요"); // 코드에서 정함
}
