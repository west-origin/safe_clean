package kr.ansimclean.api.push;

import kr.ansimclean.api.common.events.BookingAssigned;
import kr.ansimclean.api.common.events.BookingCancelled;
import kr.ansimclean.api.common.events.BookingCompleted;
import kr.ansimclean.api.common.events.BookingRescheduled;
import kr.ansimclean.api.common.events.BookingUnassigned;
import kr.ansimclean.api.common.events.ManagerPenalized;
import kr.ansimclean.api.common.events.ManagerReleased;
import kr.ansimclean.api.common.events.CameraOffline;
import kr.ansimclean.api.common.events.CameraOnline;
import kr.ansimclean.api.common.events.InquiryAnswered;
import kr.ansimclean.api.common.events.ManagerLate;
import kr.ansimclean.api.common.events.MatchingFailed;
import kr.ansimclean.api.common.events.OffersDispatched;
import kr.ansimclean.api.common.events.RecordingFailed;
import kr.ansimclean.api.common.events.RecordingReady;
import kr.ansimclean.api.common.events.SeriesEnding;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxHandler;
import kr.ansimclean.api.profile.ProfileService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 아웃박스 이벤트 → 푸시 (§6 의 푸시 지점). 이벤트마다 받는 사람·문구·알림 설정 키가 다르다.
 * 알림 설정(Settings 토글)이 꺼져 있으면 보내지 않는다: 고객 push · 매니저 push(전체) + call(새 콜) / settlement(정산).
 * 단, 예약이 취소·배정 실패된 사실 같은 거래성 알림(예: BookingUnassigned·MatchingFailed·RecordingFailed)은 토글과 무관하게 보낸다 (완결성 검토 — PROGRESS 판단 항목).
 * collapseKey 는 이벤트 본문의 해시 — 같은 이벤트 재시도가 기기에 두 번 쌓이지 않게.
 * 흐름: 업무 코드가 이벤트를 outbox_events 에 적는다 → 커밋 뒤 OutboxDispatcher 가 handle 을 부른다 →
 * 여기서 받는 사람·문구(PushCopy)·data 를 고르고 PushService.send 로 보낸다 → 실패한 기기가 있으면 던져서 아웃박스가 다시 부르게 한다.
 * 토글을 보지 않는 case 는 toCustomerAlways·toManagerAlways 나 push.send 를 직접 부르는 곳이다
 * (그 밖의 예: ManagerLate, 미방문 취소인 BookingCancelled 의 reason = not_started, ManagerPenalized 의 경고·정지 단계).
 * data 의 type 값은 두 앱의 push/pushClient.js 가 알림을 탭했을 때 갈 화면을 고르는 데 쓴다.
 */
@Component
public class PushHandler implements OutboxHandler {
    // 이 핸들러가 맡는 이벤트 타입(이벤트 record 의 클래스 이름). OutboxDispatcher 가 이 목록을 보고 이벤트를 여기로 보낸다.
    // 아래 handle 의 case 와 같아야 한다 — 여기 없으면 '핸들러 없음' 으로 실패 처리되고, case 가 없으면 default 에서 던진다
    static final Set<String> TYPES = Set.of("BookingCancelled", "BookingRescheduled", "BookingUnassigned", "SeriesEnding", "ManagerPenalized", "ManagerReleased",
            "OffersDispatched", "BookingAssigned", "BookingCompleted", "CameraOnline", "CameraOffline", "RecordingReady", "RecordingFailed", "MatchingFailed", "InquiryAnswered",
            "ManagerLate");

    /** booking/StaleBookingCleaner.REASON_NOT_STARTED 와 같다 (push 는 booking 을 보지 않는다 — 문자열만 맞춘다) */
    static final String NOT_STARTED = "not_started";

    private final PushService push;
    private final ProfileService profiles; // 알림 설정(토글) 값을 읽는다
    private final JsonMapper json = JsonMapper.builder().build(); // 이벤트 JSON 을 record 로 읽는 도구(Jackson)

    public PushHandler(PushService push, ProfileService profiles) {
        this.push = push;
        this.profiles = profiles;
    }

    @Override
    public Set<String> types() {
        return TYPES;
    }

    /**
     * 이벤트 한 건 → 받는 사람에게 푸시. payloadJson 은 발행할 때 이벤트 record 를 JSON 으로 적은 원문이다.
     * 실패한 기기가 있으면 IllegalStateException 을 던진다 → OutboxDispatcher 가 실패로 적고 백오프 뒤 다시 부른다.
     */
    @Override
    public void handle(String type, String payloadJson) {
        // 같은 이벤트를 다시 처리하면 같은 값이 나온다(String.hashCode 는 글자가 같으면 같다). 본문이 글자까지 같은 두 이벤트도 같은 키가 된다
        String collapse = type + ":" + Integer.toHexString(payloadJson.hashCode());
        // switch 의 case "X" -> { … }: type 이 X 이면 그 블록만 돈다 (아래로 흘러내리지 않는다)
        switch (type) {
            case "OffersDispatched" -> {
                // 새 콜 → 후보 매니저들. push 와 call(새 콜 알림) 토글이 모두 켜진 매니저에게만. 탭하면 매니저 앱 콜 목록(/call)
                OffersDispatched e = json.readValue(payloadJson, OffersDispatched.class);
                PushCopy.Text text = e.preferred() ? PushCopy.PREFERRED_CALL : PushCopy.NEW_CALL;
                // 한 명이 실패해도 나머지에게 먼저 보낸다 — 앞 순서에서 던지면 뒤 후보는 시도 한도(OutboxEvent.MAX_ATTEMPTS)를 다 쓰도록 콜 푸시를 못 받는다 (검수 3차). 재시도의 중복은 collapseKey·tag 로 기기에서 합쳐진다
                int failed = 0;
                for (UUID managerId : e.managerIds()) failed += toManagerCount(managerId, "call", text, Map.of("type", "call", "bookingId", e.bookingId().toString()), collapse);
                failIfAny(failed);
            }
            case "BookingAssigned" -> {
                // 매니저 배정 → 고객 (push 토글)
                BookingAssigned e = json.readValue(payloadJson, BookingAssigned.class);
                toCustomer(e.customerId(), PushCopy.MANAGER_ASSIGNED, booking(e.bookingId(), "reservation"), collapse);
            }
            case "CameraOnline" -> {
                // 바디캠 연결 → 고객 '청소가 시작됐어요'. data type live = 탭하면 고객 앱 라이브 화면
                CameraOnline e = json.readValue(payloadJson, CameraOnline.class);
                if (e.first()) toCustomer(e.customerId(), PushCopy.CLEANING_STARTED, booking(e.bookingId(), "live"), collapse); // 재접속마다 알리지 않는다
            }
            case "BookingCompleted" -> {
                // 청소 완료 → 고객(push 토글) + 매니저 정산 알림(push·settlement 토글). 받는 사람별로 키 끝을 :c(고객)·:m(매니저)으로 나눈다
                BookingCompleted e = json.readValue(payloadJson, BookingCompleted.class);
                int failed = toCustomerCount(e.customerId(), PushCopy.CLEANING_DONE, booking(e.bookingId(), "reservation"), collapse + ":c");
                failed += toManagerCount(e.managerId(), "settlement", PushCopy.SETTLEMENT_CREATED, booking(e.bookingId(), "settlement"), collapse + ":m");
                failIfAny(failed); // 고객 실패가 매니저 정산 푸시를 막지 않게
            }
            case "BookingCancelled" -> {
                // reason = not_started(미방문) 면 고객·매니저 둘 다. 그 밖의 취소(고객 취소·정기청소 해지 — BookingService 는
                // 배정된 매니저나 밤에 일정 변경 안내를 미뤄 둔 원래 매니저가 있을 때만 발행한다)는 매니저에게만 (push 토글)
                BookingCancelled e = json.readValue(payloadJson, BookingCancelled.class);
                if (NOT_STARTED.equals(e.reason())) {
                    // 시스템 취소(매니저 미방문, StaleBookingCleaner): 둘 다 거래성 — 토글과 무관하게 보낸다
                    int failed = push.send(e.customerId(), PushCopy.NOT_VISITED.title(), PushCopy.NOT_VISITED.body(), booking(e.bookingId(), "reservation"), collapse + ":c");
                    if (e.managerId() != null) failed += push.send(e.managerId(), PushCopy.BOOKING_NOT_STARTED.title(), PushCopy.BOOKING_NOT_STARTED.body(), booking(e.bookingId(), "schedule"), collapse + ":m");
                    failIfAny(failed);
                } else if (e.managerId() != null) toManager(e.managerId(), null, PushCopy.BOOKING_CANCELLED, booking(e.bookingId(), "schedule"), collapse);
            }
            case "BookingRescheduled" -> {
                // 일정 변경 → 이전 매니저에게만 (push 토글). 이전 매니저가 없으면 아무에게도 보내지 않는다.
                // Boolean.FALSE.equals: 값이 없는(null) 예전 이벤트는 false 로 보지 않는다 → 새 콜 문구
                BookingRescheduled e = json.readValue(payloadJson, BookingRescheduled.class);
                if (e.previousManagerId() != null && Boolean.FALSE.equals(e.preferredOffered())) { // 콜이 없는데 '새 콜을 확인해주세요' 로 부르지 않게
                    toManager(e.previousManagerId(), null, PushCopy.BOOKING_RESCHEDULED_RELEASED, booking(e.bookingId(), "schedule"), collapse);
                } else if (e.previousManagerId() != null) {
                    toManager(e.previousManagerId(), null, PushCopy.BOOKING_RESCHEDULED, booking(e.bookingId(), "call"), collapse);
                }
            }
            case "BookingUnassigned" -> {
                // 매니저를 배정하지 못해 예약이 취소됨 → 고객 (거래성이라 토글과 무관)
                BookingUnassigned e = json.readValue(payloadJson, BookingUnassigned.class);
                toCustomerAlways(e.customerId(), PushCopy.UNASSIGNED, booking(e.bookingId(), "reservation"), collapse);
            }
            case "ManagerPenalized" -> {
                // 매니저 본인에게 — 기록 한 건에 푸시 하나. data type penalty = 매니저 앱 패널티 내역 화면 (docs/manager-policy.md §3, 문제 목록 14번)
                ManagerPenalized e = json.readValue(payloadJson, ManagerPenalized.class);
                Map<String, String> data = Map.of("type", "penalty");
                if ("warn".equals(e.stage())) {
                    // 경고 단계를 처음 넘음 → 경고 전용. 계정 상태 안내라 알림 설정과 무관하게 보낸다
                    String body = e.nextSuspendPoints() != null && e.nextSuspendDays() != null
                            ? String.format(PushCopy.PENALTY_WARNING_BODY, plainPoints(e.total()), e.nextSuspendPoints(), e.nextSuspendDays())
                            : String.format(PushCopy.PENALTY_WARNING_BODY_NO_NEXT, plainPoints(e.total()));
                    toManagerAlways(e.managerId(), new PushCopy.Text(PushCopy.PENALTY_WARNING_TITLE, body), data, collapse);
                } else if ("suspend".equals(e.stage()) && e.suspendedUntil() != null) {
                    // 정지 단계를 넘음 → 정지 전용(마지막 날 "9월 29일"). 계정 상태 안내라 알림 설정과 무관하게 보낸다
                    String until = KoreanFormat.dateShort(LocalDate.parse(e.suspendedUntil()));
                    toManagerAlways(e.managerId(), new PushCopy.Text(PushCopy.PENALTY_SUSPENDED_TITLE, String.format(PushCopy.PENALTY_SUSPENDED_BODY, plainPoints(e.total()), until)), data, collapse);
                } else if (!"0".equals(e.points()) && !"0.0".equals(e.points())) {
                    // 그 밖(단계를 넘지 않음·해지 심사만 넘음·단계 칸이 없는 예전 이벤트) → 일반 알림(push 토글). 0점(무료 취소)이면 알리지 않는다.
                    // 점수는 글자로 온다(ManagerPenalized) — "0" 과 "0.0" 을 0점으로 본다. 누적 점수는 경고·정지 푸시·내역 화면처럼 끝의 0 을 뗀다 ("2.0" → "2", 합친 38건 검토 R7)
                    toManager(e.managerId(), null, new PushCopy.Text(PushCopy.PENALTY_RECORDED.title(), "누적 " + plainPoints(e.total()) + "점 · " + PushCopy.PENALTY_RECORDED.body()),
                            data, collapse);
                }
            }
            case "ManagerReleased" -> {
                // 고객에게 — 매니저가 방문을 취소했고 다시 매칭한다(거래성이라 토글과 무관)
                ManagerReleased e = json.readValue(payloadJson, ManagerReleased.class);
                toCustomerAlways(e.customerId(), PushCopy.MANAGER_RELEASED, booking(e.bookingId(), "reservation"), collapse);
            }
            case "SeriesEnding" -> {
                // 정기청소 마지막 방문이 다가옴 → 고객 (push 토글). data 에는 bookingId 대신 seriesId
                SeriesEnding e = json.readValue(payloadJson, SeriesEnding.class);
                toCustomer(e.customerId(), PushCopy.SERIES_ENDING, Map.of("type", "series", "seriesId", e.seriesId().toString()), collapse);
            }
            case "CameraOffline" -> {
                // 바디캠 끊김·5분 넘게 미연결 → 매니저 (push 토글). data type job = 매니저 앱 작업 화면
                CameraOffline e = json.readValue(payloadJson, CameraOffline.class);
                PushCopy.Text text = "waiting".equals(e.reason()) ? PushCopy.CAMERA_WAITING : PushCopy.CAMERA_DISCONNECTED;
                toManager(e.managerId(), null, text, booking(e.bookingId(), "job"), collapse);
            }
            case "RecordingReady" -> {
                // 녹화 영상 준비 → 고객 (push 토글). data type recording = 고객 앱 다시보기
                RecordingReady e = json.readValue(payloadJson, RecordingReady.class);
                toCustomer(e.customerId(), PushCopy.RECORDING_READY, booking(e.bookingId(), "recording"), collapse);
            }
            case "MatchingFailed" -> {
                // 매칭 실패 → 고객 (토글과 무관). null(값이 없는 예전 이벤트)은 '가능' 으로 본다 — FALSE 일 때만 불가
                MatchingFailed e = json.readValue(payloadJson, MatchingFailed.class);
                boolean retry = !Boolean.FALSE.equals(e.retryable());
                boolean change = !Boolean.FALSE.equals(e.changeable());
                PushCopy.Text text = retry && change ? PushCopy.MATCHING_FAILED : retry ? PushCopy.MATCHING_FAILED_RETRY_ONLY : change ? PushCopy.MATCHING_FAILED_CHANGE_ONLY : PushCopy.MATCHING_FAILED_FINAL;
                toCustomerAlways(e.customerId(), text, booking(e.bookingId(), "reservation"), collapse);
            }
            case "ManagerLate" -> {
                // 거래성 알림(설정 토글과 무관) — 집에서 기다리는 고객에게 (docs/manager-policy.md §2, 자체 점검 #25)
                ManagerLate e = json.readValue(payloadJson, ManagerLate.class);
                toCustomerAlways(e.customerId(), new PushCopy.Text(PushCopy.MANAGER_LATE_TITLE, String.format(PushCopy.MANAGER_LATE_BODY, e.minutes())), booking(e.bookingId(), "reservation"), collapse);
            }
            case "InquiryAnswered" -> {
                InquiryAnswered e = json.readValue(payloadJson, InquiryAnswered.class);
                toCustomerAlways(e.userId(), PushCopy.INQUIRY_ANSWERED, Map.of("type", "inquiry", "inquiryId", e.inquiryId().toString()), collapse); // 고객·매니저 공통(거래성)
            }
            case "RecordingFailed" -> {
                // 녹화 저장 실패 → 고객 (토글과 무관)
                RecordingFailed e = json.readValue(payloadJson, RecordingFailed.class);
                toCustomerAlways(e.customerId(), PushCopy.RECORDING_FAILED, booking(e.bookingId(), "reservation"), collapse);
            }
            // TYPES 에 넣고 case 를 빠뜨렸을 때 — 던지면 아웃박스가 실패로 남긴다
            default -> throw new IllegalArgumentException("모르는 푸시 이벤트: " + type);
        }
    }

    // 점수 글자에서 끝의 0 을 뗀다 ("3.0" → "3", "4.50" → "4.5") — DB 의 점수 칸(numeric(3,1))을 더한 합은 "3.0" 모양으로 온다.
    // 패널티 푸시 세 갈래(경고·정지·일반)가 모두 쓴다 — 내역 화면(ManagerPenaltyService.screen 의 stripTrailingZeros)과 같은 글자
    private static String plainPoints(String points) {
        return new java.math.BigDecimal(points).stripTrailingZeros().toPlainString();
    }

    // 알림 data = { type: 탭했을 때 갈 화면 종류, bookingId }. 값은 두 앱의 pushClient.js 가 읽는 이름과 같아야 한다
    private static Map<String, String> booking(UUID bookingId, String screen) {
        return Map.of("type", screen, "bookingId", bookingId.toString());
    }

    // 고객에게 — push 토글이 꺼져 있으면 보내지 않는다. 실패한 기기가 있으면 던진다
    private void toCustomer(UUID customerId, PushCopy.Text text, Map<String, String> data, String collapseKey) {
        failIfAny(toCustomerCount(customerId, text, data, collapseKey));
    }

    /** 거래성 알림 — 설정 토글을 보지 않는다 */
    private void toCustomerAlways(UUID customerId, PushCopy.Text text, Map<String, String> data, String collapseKey) {
        failIfAny(push.send(customerId, text.title(), text.body(), data, collapseKey));
    }

    // toCustomer 와 같지만 던지지 않고 실패 기기 수만 돌려준다 — 한 이벤트에서 여러 명에게 보낼 때(예: BookingCompleted). 토글이 꺼져 있으면 0
    private int toCustomerCount(UUID customerId, PushCopy.Text text, Map<String, String> data, String collapseKey) {
        if (!enabled(customerId, List.of("push"))) return 0;
        return push.send(customerId, text.title(), text.body(), data, collapseKey);
    }

    /** 예외는 PushService(@Transactional) 밖인 여기서 — 안에서 던지면 이 이벤트의 아웃박스 트랜잭션이 rollback-only 가 된다 (예전에는 배치 전체) */
    private static void failIfAny(int failed) {
        if (failed > 0) throw new IllegalStateException("푸시 " + failed + "기기 발송 실패 — 재시도");
    }

    /** 매니저 계정 상태 안내(패널티 경고·콜 배정 정지) — 설정 토글을 보지 않는다. 실패한 기기가 있으면 던진다 */
    private void toManagerAlways(UUID managerId, PushCopy.Text text, Map<String, String> data, String collapseKey) {
        failIfAny(push.send(managerId, text.title(), text.body(), data, collapseKey));
    }

    // 매니저에게 — push(와 settingKey) 토글이 모두 켜져 있을 때만 보낸다. 실패한 기기가 있으면 던진다
    /** @param settingKey push 외에 추가로 켜져 있어야 하는 키 (call·settlement), 없으면 null */
    private void toManager(UUID managerId, String settingKey, PushCopy.Text text, Map<String, String> data, String collapseKey) {
        failIfAny(toManagerCount(managerId, settingKey, text, data, collapseKey));
    }

    /** 던지지 않고 실패 기기 수만 돌려준다 — 여러 수신자를 돌 때 */
    private int toManagerCount(UUID managerId, String settingKey, PushCopy.Text text, Map<String, String> data, String collapseKey) {
        List<String> keys = settingKey == null ? List.of("push") : List.of("push", settingKey);
        if (!enabled(managerId, keys)) return 0;
        return push.send(managerId, text.title(), text.body(), data, collapseKey);
    }

    // 알림 설정에서 keys 가 모두 켜져 있는가. 저장된 값이 없으면 ProfileService.notifications 가 기본값(marketing 만 꺼짐)을 채운다.
    // allMatch = 하나라도 꺼져 있으면 false
    private boolean enabled(UUID userId, List<String> keys) {
        Map<String, Boolean> settings = profiles.notifications(userId, keys);
        return keys.stream().allMatch(k -> settings.getOrDefault(k, true));
    }
}
