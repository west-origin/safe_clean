package kr.ansimclean.api.managerhome.dto;

import java.util.List;
import java.util.UUID;

// 매니저 앱 홈 화면이 한 번에 받는 응답 모양 (서버 → 앱 JSON).
// ManagerHomeService.home() 이 만들고 ManagerHomeController 의 GET /api/v1/manager/home 이 돌려준다.
// 앱에서는 apps/manager/src/api/manager.js 의 getHome() 이 받는다.
// 안쪽 record 는 홈 화면의 한 부분씩이다: Manager = 인사말·승인 대기/정지 안내, Today = 날짜·'오늘 예정 N건' 줄,
// InProgress = 진행 중 작업 카드, NewCalls = 새 청소 요청 카드.
// remaining = 오늘 아직 시작하지 않은(scheduled) 작업 카드들, 시작 시각순.
/** GET /manager/home — 앱 HOME 모양 (§6.6). inProgress·newCalls 는 없으면 null */
public record HomeResponse(Manager manager, Today today, InProgress inProgress, NewCalls newCalls, List<ScheduleItem> remaining) {
    /**
     * approved=false 면 아직 콜을 받을 수 없다 — 홈 상단 '승인 대기 중' 카드 (검증 검토: 매니저가 며칠을 기다려도 이유를 몰랐다).
     * suspension = 콜 배정 정지 안내("9월 29일 까지 콜을 받을 수 없어요"), 정지가 아니면 null — 정지된 매니저가 '승인 대기' 를 보지 않게 (자체 점검 #11)
     * 주의: approved 는 정지를 보지 않는다(ProfileService.isApproved). 콜 탭의 CallsResponse.approved 는 정지 중이면 false 라 뜻이 다르다
     */
    public record Manager(String name, boolean approved, String suspension) {}

    // 홈 상단 '오늘 예정 N건' 줄 (ManagerHomeService.home() 이 센다).
    // done 을 빼는 이유: 다 끝낸 날 '예정 1건' 과 '오늘 예정된 업무가 없어요' 가 같이 떴다. 오늘 방문일인 예약만 센다
    /** dateLabel = "9월 15일 (화)", count = 오늘 scheduled+in_progress+paused (끝난 done 은 세지 않는다) */
    public record Today(String dateLabel, int count) {}

    // 지금 진행 중(또는 일시정지)인 작업 카드. 진행 중인 작업이 없으면 이 칸 전체가 null.
    // title = "청소 진행 중", elapsed = 응답 시점의 경과 시간 "01:23:42"(일시정지한 시간은 뺀다), jobId = 예약 id
    /** since = "14:02부터 진행 · 강남구 역삼동" */
    /** status: inProgress | paused — 홈 카드 배지·이동 화면 (일시정지 작업이 LIVE 로 보이지 않게) */
    /** startedAt(ISO)·pausedSeconds 로 앱이 1초마다 경과 시간을 계산한다 (검증 검토: hms 문자열만 있어 30초씩 뛰었다) */
    public record InProgress(String title, String elapsed, String since, UUID jobId, String status, String startedAt, long pausedSeconds) {}

    // '새로운 청소 요청' 카드. count = 아직 만료되지 않은 내 콜 수 (OfferService.pendingFor). 콜이 없거나 정지·미승인 매니저면 null
    /** expiry = 가장 이른 만료, summary = "가장 가까운 콜 · 9월 18일 오후 2:00 · 85,000원" */
    public record NewCalls(int count, String expiry, String summary) {}
}
