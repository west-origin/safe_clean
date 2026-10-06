package kr.ansimclean.api.push;

import kr.ansimclean.api.common.events.BookingAssigned;
import kr.ansimclean.api.common.events.BookingCancelled;
import kr.ansimclean.api.common.events.BookingCompleted;
import kr.ansimclean.api.common.events.CameraOffline;
import kr.ansimclean.api.common.events.CameraOnline;
import kr.ansimclean.api.common.events.ManagerPenalized;
import kr.ansimclean.api.common.events.OffersDispatched;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 이벤트 → 받는 사람·문구·알림 설정 (§6 푸시 지점) */
// PushHandler(아웃박스 이벤트를 받아 누구에게 어떤 문구로 푸시할지 고르는 곳) 단위 테스트.
// 실제 발송(PushService)과 알림 설정 조회(ProfileService)는 Mockito 가짜다. 이벤트 record 를 JSON 으로 적어
// handle(타입, JSON) 에 넣고, push.send 가 어떤 받는 사람·제목·본문·data 로 불렸는지 verify 로 본다.
// push.send 의 결과는 실패한 기기 수다 — 가짜의 기본값 0 은 '모두 성공', 1 을 돌려주게 하면 실패 상황이 된다
class PushHandlerTest {
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();
    static final UUID BOOKING = UUID.randomUUID();
    PushService push = mock(PushService.class);
    ProfileService profiles = mock(ProfileService.class);
    PushHandler handler = new PushHandler(push, profiles);
    JsonMapper json = JsonMapper.builder().build(); // 이벤트 record → JSON 문자열 (아웃박스에 적히는 모양을 흉내 낸다)

    // 기본 준비: 알림 설정 조회는 요청한 키를 모두 켜짐(true)으로 돌려준다. 끈 상황은 각 테스트가 doReturn 으로 덮어쓴다
    @BeforeEach
    void setUp() {
        when(profiles.notifications(any(), any())).thenAnswer(inv -> {
            Map<String, Boolean> out = new java.util.LinkedHashMap<>();
            for (String k : inv.<List<String>>getArgument(1)) out.put(k, true);
            return out;
        });
    }

    // 새 콜(OffersDispatched)은 후보 매니저 두 명 모두에게, data 에 type=call·bookingId 를 싣는다.
    // 배정(BookingAssigned)은 고객에게, 완료(BookingCompleted)는 고객 '청소 완료' + 매니저 '정산 등록' 두 건.
    // ArgumentCaptor = verify 할 때 넘어간 인자를 붙잡아 두었다가 getValue() 로 내용을 확인하는 도구
    @Test
    void 새_콜은_후보_전원에게_배정은_고객에게_완료는_둘_다에게() {
        UUID other = UUID.randomUUID();
        handler.handle("OffersDispatched", json.writeValueAsString(new OffersDispatched(BOOKING, List.of(MANAGER, other), false)));
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(push).send(eq(MANAGER), eq("새 콜이 도착했어요"), any(), data.capture(), any());
        verify(push).send(eq(other), eq("새 콜이 도착했어요"), any(), any(), any());
        assertThat(data.getValue()).containsEntry("type", "call").containsEntry("bookingId", BOOKING.toString());

        handler.handle("BookingAssigned", json.writeValueAsString(new BookingAssigned(BOOKING, CUSTOMER, MANAGER)));
        verify(push).send(eq(CUSTOMER), eq("매니저가 배정됐어요"), any(), any(), any());

        handler.handle("BookingCompleted", json.writeValueAsString(new BookingCompleted(BOOKING, CUSTOMER, MANAGER)));
        verify(push).send(eq(CUSTOMER), eq("청소가 완료됐어요"), any(), any(), any());
        verify(push).send(eq(MANAGER), eq("정산이 등록됐어요"), any(), any(), any());
    }

    // 매칭 실패 이벤트의 retryable(나중에 다시 찾을 수 있나)·changeable(일정을 바꿀 수 있나) 조합 네 가지와
    // 두 값이 없는(null) 예전 이벤트까지, 고객 푸시 본문이 각각 맞는 문구인지 본다.
    // body 는 테스트 안의 작은 함수(BiFunction = 인자 둘을 받아 값을 돌려주는 람다): 앞의 호출 기록을 지우고(clearInvocations)
    // 이벤트를 처리한 뒤 붙잡은 본문을 돌려준다 — 그래야 매번 verify 가 이번 호출 한 건만 본다
    @Test
    void 매칭_실패_안내는_실제로_가능한_길만_말한다() { // 자체 점검 후속 검토
        java.util.function.BiFunction<Boolean, Boolean, String> body = (retry, change) -> {
            org.mockito.Mockito.clearInvocations(push);
            handler.handle("MatchingFailed", json.writeValueAsString(new kr.ansimclean.api.common.events.MatchingFailed(BOOKING, CUSTOMER, retry, change)));
            ArgumentCaptor<String> b = ArgumentCaptor.forClass(String.class);
            verify(push).send(eq(CUSTOMER), eq("매니저를 찾지 못했어요"), b.capture(), any(), any());
            return b.getValue();
        };
        assertThat(body.apply(true, true)).isEqualTo("가능한 매니저가 생기면 다시 찾아볼게요. 일정을 바꾸면 다시 찾아요");
        assertThat(body.apply(true, false)).isEqualTo("가능한 매니저가 생기면 다시 찾아볼게요. 1:1 문의로 알려주셔도 돼요");
        assertThat(body.apply(false, true)).isEqualTo("다른 날짜·시간으로 바꾸면 다시 찾아요. 1:1 문의로 알려주셔도 돼요");
        assertThat(body.apply(false, false)).isEqualTo("방문 시간까지 매니저를 찾지 못할 수 있어요. 1:1 문의로 알려주세요");
        assertThat(body.apply(null, null)).isEqualTo("가능한 매니저가 생기면 다시 찾아볼게요. 일정을 바꾸면 다시 찾아요"); // 배포 전에 쌓인 이벤트
    }

    // 지각(ManagerLate, 기준 30분) → 고객에게만, 본문에 '30분' 이 들어가고 data type=reservation. 매니저에게는 보내지 않는다.
    // 이 테스트는 알림 설정을 끄지 않는다 — 토글을 보지 않는 것은 PushHandler 가 이 이벤트에 toCustomerAlways 를 쓰기 때문이다
    @Test
    void 매니저가_늦으면_고객에게_설정과_무관하게_지각_기준_분을_넣어_알린다() { // 자체 점검 #25
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        handler.handle("ManagerLate", json.writeValueAsString(new kr.ansimclean.api.common.events.ManagerLate(BOOKING, CUSTOMER, MANAGER, 30)));
        verify(push).send(eq(CUSTOMER), eq("매니저가 늦어지고 있어요"), eq("약속 시간이 30분 넘게 지났어요. 도착이 더 늦어지면 고객센터로 알려주세요"), data.capture(), any());
        assertThat(data.getValue()).containsEntry("type", "reservation").containsEntry("bookingId", BOOKING.toString());
        verify(push, never()).send(eq(MANAGER), any(), any(), any(), any());
    }

    // 일정 변경(BookingRescheduled)은 이전 매니저에게만 간다. 세 경우:
    // ① 우선 오퍼를 다시 보냄(true) → '새 콜을 확인해주세요' + data type=call
    // ② 보내지 않음(false) → '다른 매니저에게 다시 배정돼요' + data type=schedule
    // ③ preferredOffered 필드가 없는 예전 이벤트 JSON(직접 쓴 문자열) → ① 과 같은 문구
    @Test
    void 일정_변경은_우선_오퍼를_다시_보냈을_때만_새_콜을_보라고_하고_아니면_다시_배정된다고_알린다() { // 자체 점검 #18·#56
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        handler.handle("BookingRescheduled", json.writeValueAsString(new kr.ansimclean.api.common.events.BookingRescheduled(BOOKING, CUSTOMER, MANAGER, true)));
        verify(push).send(eq(MANAGER), eq("예약 일정이 변경됐어요"), eq("고객이 일정을 바꿔 다시 매칭돼요. 새 콜을 확인해주세요"), data.capture(), any());
        assertThat(data.getValue()).containsEntry("type", "call");

        UUID other = UUID.randomUUID();
        handler.handle("BookingRescheduled", json.writeValueAsString(new kr.ansimclean.api.common.events.BookingRescheduled(BOOKING, CUSTOMER, other, false)));
        verify(push).send(eq(other), eq("예약 일정이 변경됐어요"), eq("고객이 일정을 바꿔 이 예약은 다른 매니저에게 다시 배정돼요"), data.capture(), any());
        assertThat(data.getValue()).containsEntry("type", "schedule");

        UUID legacy = UUID.randomUUID(); // 배포 전에 쌓인 이벤트(필드 없음)는 예전처럼
        handler.handle("BookingRescheduled", "{\"bookingId\":\"" + BOOKING + "\",\"customerId\":\"" + CUSTOMER + "\",\"previousManagerId\":\"" + legacy + "\"}");
        verify(push).send(eq(legacy), eq("예약 일정이 변경됐어요"), eq("고객이 일정을 바꿔 다시 매칭돼요. 새 콜을 확인해주세요"), any(), any());
    }

    // 첫 후보(MANAGER)에게 보내기가 1기기 실패해도 두 번째 후보에게는 보낸 뒤, 마지막에 IllegalStateException 을 던진다.
    // 던지면 아웃박스가 이 이벤트를 나중에 다시 처리한다
    @Test
    void 새_콜은_앞_후보가_실패해도_나머지에게_먼저_보낸_뒤_던진다() {
        UUID other = UUID.randomUUID();
        when(push.send(eq(MANAGER), any(), any(), any(), any())).thenReturn(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle("OffersDispatched", json.writeValueAsString(new OffersDispatched(BOOKING, List.of(MANAGER, other), false))))
                .isInstanceOf(IllegalStateException.class);
        verify(push).send(eq(other), eq("새 콜이 도착했어요"), any(), any(), any());
    }

    // 고객 발송이 1기기 실패하면 handle 이 IllegalStateException 을 던진다 (재시도 자체는 아웃박스 쪽 동작이라 여기서는 던지는지만 본다)
    @Test
    void 일시_실패가_있으면_핸들러가_던져_아웃박스가_재시도한다() {
        when(push.send(eq(CUSTOMER), any(), any(), any(), any())).thenReturn(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle("BookingAssigned", json.writeValueAsString(new BookingAssigned(BOOKING, CUSTOMER, MANAGER))))
                .isInstanceOf(IllegalStateException.class);
    }

    // 바디캠 재접속(first=false)은 아무에게도 보내지 않고, 첫 연결(first=true)만 고객에게 '청소가 시작됐어요'.
    // 끊김은 사유(disconnected: 끊김, waiting: 아직 미연결)에 따라 매니저에게 다른 제목으로 보낸다.
    // waiting 본문은 대기가 언제 시작됐든(작업 시작·끊김·재개) 맞는 문구다 — 예전 '작업을 시작한 지 5분' 은 끊긴 뒤·재개 뒤에 틀렸다 (문제 목록 16번)
    @Test
    void 카메라_첫_연결만_고객에게_알리고_끊김은_사유별_문구로_매니저에게() {
        handler.handle("CameraOnline", json.writeValueAsString(new CameraOnline(BOOKING, CUSTOMER, false)));
        verify(push, never()).send(any(), any(), any(), any(), any());
        handler.handle("CameraOnline", json.writeValueAsString(new CameraOnline(BOOKING, CUSTOMER, true)));
        verify(push).send(eq(CUSTOMER), eq("청소가 시작됐어요"), eq("실시간 영상 보기"), any(), any());

        handler.handle("CameraOffline", json.writeValueAsString(new CameraOffline(BOOKING, MANAGER, "disconnected")));
        verify(push).send(eq(MANAGER), eq("바디캠 연결이 끊겼어요"), any(), any(), any());
        handler.handle("CameraOffline", json.writeValueAsString(new CameraOffline(BOOKING, MANAGER, "waiting")));
        verify(push).send(eq(MANAGER), eq("바디캠이 아직 연결되지 않았어요"), eq("5분 넘게 연결되지 않았어요. 바디캠 전원과 네트워크를 확인해주세요"), any(), any());
    }

    // doReturn(값).when(가짜).메서드(…) = when(…).thenReturn(…) 과 같은 뜻을 거꾸로 쓴 모양. 여기서는 setUp 의 '모두 켜짐' 을 덮어쓴다.
    // when(profiles.notifications(…)) 로 쓰면 덮어쓰는 순간 setUp 의 thenAnswer 가 인자 null 로 한 번 돌아 예외가 나서 doReturn 을 쓴다.
    // 매니저: push 켜짐·call 꺼짐 → 새 콜은 안 가고, call 과 무관한 예약 취소 알림은 간다.
    // 고객: push 꺼짐 → 배정 알림이 안 간다. 마지막 줄은 배정 전 취소(매니저 없음)가 예외 없이 끝나는지만 본다
    @Test
    void 알림_설정이_꺼져_있으면_보내지_않는다_매니저_새_콜은_call_정산은_settlement_까지_본다() {
        org.mockito.Mockito.doReturn(Map.of("push", true, "call", false, "settlement", true)).when(profiles).notifications(eq(MANAGER), any());
        handler.handle("OffersDispatched", json.writeValueAsString(new OffersDispatched(BOOKING, List.of(MANAGER), true)));
        verify(push, never()).send(eq(MANAGER), any(), any(), any(), any());

        handler.handle("BookingCancelled", json.writeValueAsString(new BookingCancelled(BOOKING, CUSTOMER, MANAGER, "고객 요청")));
        verify(push).send(eq(MANAGER), eq("예약이 취소됐어요"), any(), any(), any()); // call 과 무관 — push 만 본다

        org.mockito.Mockito.doReturn(Map.of("push", false)).when(profiles).notifications(eq(CUSTOMER), any());
        handler.handle("BookingAssigned", json.writeValueAsString(new BookingAssigned(BOOKING, CUSTOMER, MANAGER)));
        verify(push, never()).send(eq(CUSTOMER), any(), any(), any(), any());

        handler.handle("BookingCancelled", json.writeValueAsString(new BookingCancelled(BOOKING, CUSTOMER, null, "고객 요청"))); // 배정 전 취소 — 받을 매니저 없음
    }

    // 패널티 푸시는 기록 한 건에 하나: 이번 기록으로 경고 단계(3점)를 넘으면 경고 전용, 정지 단계(5·8점)를 넘으면 정지 전용, 아니면 일반 '패널티가 기록됐어요'.
    // 경고·정지는 계정 상태 안내라 push 토글을 꺼도 간다(패널티 내역 화면의 '3점이 되면 경고 알림을 보내드려요' 약속). 일반 알림은 토글을 따른다.
    // 누적 점수 글자의 끝 0 은 뗀다("3.0" → "3"). 단계 칸이 없는 예전 이벤트 JSON 은 일반 알림
    @Test
    void 패널티_경고와_정지는_전용_문구로_알림_설정과_무관하게_보낸다() { // 문제 목록 14번 (사용자 결정 A)
        org.mockito.Mockito.doReturn(Map.of("push", false)).when(profiles).notifications(eq(MANAGER), any());
        Map<String, String> data = Map.of("type", "penalty");
        handler.handle("ManagerPenalized", json.writeValueAsString(new ManagerPenalized(MANAGER, BOOKING, "cancel_late", "1", "3.0", "warn", null, 5, 7)));
        verify(push).send(eq(MANAGER), eq("패널티 경고"), eq("누적 3점이에요. 5점이 되면 7일 동안 콜 배정이 정지돼요"), eq(data), any());

        handler.handle("ManagerPenalized", json.writeValueAsString(new ManagerPenalized(MANAGER, BOOKING, "no_show", "3", "6.0", "suspend", "2026-09-29", null, null)));
        verify(push).send(eq(MANAGER), eq("콜 배정이 정지됐어요"), eq("누적 6점으로 9월 29일까지 새 콜을 받을 수 없어요. 이미 수락한 방문은 그대로 진행해주세요"), eq(data), any());

        // 경고 위에 정지 단계가 없도록 설정을 바꿨으면 다음 단계 안내 없이 내역 화면으로 보낸다
        handler.handle("ManagerPenalized", json.writeValueAsString(new ManagerPenalized(MANAGER, BOOKING, "tardy", "0.5", "3.5", "warn", null, null, null)));
        verify(push).send(eq(MANAGER), eq("패널티 경고"), eq("누적 3.5점이에요. MY › 패널티 내역에서 확인해주세요"), eq(data), any());

        // 단계를 넘지 않은 기록(stage null)은 일반 알림 — 토글이 꺼져 있어 가지 않는다
        org.mockito.Mockito.clearInvocations(push);
        handler.handle("ManagerPenalized", json.writeValueAsString(new ManagerPenalized(MANAGER, BOOKING, "tardy", "0.5", "1.5", null, null, null, null)));
        verify(push, never()).send(any(), any(), any(), any(), any());

        // 배포 전에 쌓인 예전 이벤트(단계 칸 없음), 토글 켬 → 일반 문구 그대로
        org.mockito.Mockito.doReturn(Map.of("push", true)).when(profiles).notifications(eq(MANAGER), any());
        handler.handle("ManagerPenalized", "{\"managerId\":\"" + MANAGER + "\",\"bookingId\":\"" + BOOKING + "\",\"category\":\"tardy\",\"points\":\"0.5\",\"total\":\"1.5\"}");
        verify(push).send(eq(MANAGER), eq("패널티가 기록됐어요"), eq("누적 1.5점 · MY › 패널티 내역에서 확인해주세요"), eq(data), any());

        // 일반 알림도 끝의 0 을 뗀다 — DB 의 옛 행(1.0)과 방금 적은 행(1)을 더한 합은 "2.0" 으로 온다. 패널티 내역 화면·경고·정지 푸시처럼 "2" (합친 38건 검토 R7)
        handler.handle("ManagerPenalized", json.writeValueAsString(new ManagerPenalized(MANAGER, BOOKING, "cancel_late", "1", "2.0", null, null, null, null)));
        verify(push).send(eq(MANAGER), eq("패널티가 기록됐어요"), eq("누적 2점 · MY › 패널티 내역에서 확인해주세요"), eq(data), any());
    }

    // 모든 사용자의 push 를 끈 상태에서도, 미방문 취소(reason = not_started)는 고객·매니저 둘 다에게 간다 (거래성 알림)
    @Test
    void 매니저_미방문_취소는_고객과_매니저_둘_다_토글과_무관하게_받는다() {
        org.mockito.Mockito.doReturn(Map.of("push", false)).when(profiles).notifications(any(), any());
        handler.handle("BookingCancelled", json.writeValueAsString(new BookingCancelled(BOOKING, CUSTOMER, MANAGER, "not_started")));
        verify(push).send(eq(CUSTOMER), eq("매니저가 방문하지 않았어요"), any(), any(), any());
        verify(push).send(eq(MANAGER), eq("방문하지 않은 예약이 취소됐어요"), any(), any(), any());
    }
}
