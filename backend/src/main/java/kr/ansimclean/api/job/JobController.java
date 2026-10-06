package kr.ansimclean.api.job;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.job.dto.ActiveJobResponse;
import kr.ansimclean.api.job.dto.ChecklistRequest;
import kr.ansimclean.api.job.dto.ChecklistResponse;
import kr.ansimclean.api.job.dto.CompletedJobResponse;
import kr.ansimclean.api.job.dto.ContactResponse;
import kr.ansimclean.api.job.dto.JobIdResponse;
import kr.ansimclean.api.job.dto.JobResponse;
import kr.ansimclean.api.job.dto.PauseRequest;
import kr.ansimclean.api.job.dto.PauseResponse;
import kr.ansimclean.api.job.dto.PausedJobResponse;
import kr.ansimclean.api.job.dto.StartResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 매니저 작업 API (docs/api.md §5.3). 고정 경로(active·last-completed)가 {id} 보다 먼저 맞는다 */
// 모든 경로 앞에 /api/v1/manager/jobs 가 붙는다(@RequestMapping). 부르는 쪽 예: 매니저 앱 api/manager.js 의 getActiveJob·pauseJob.
// @AuthenticationPrincipal CurrentUser = 요청을 보낸 로그인 사용자. {id} 는 경로의 작업(= 예약) id 이고, 남의 작업이면 서비스가 404 로 답한다.
// Idempotency-Key 헤더(CODE_GUIDE.md 3-7)는 없어도 된다(required = false) — 앱은 시작·완료·취소에 보낸다.
@RestController
@RequestMapping(SecurityConfig.API + "/manager/jobs")
public class JobController {
    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    /** GET /manager/jobs/active — 진행 중(in_progress·paused) 작업 화면 값(경과 시간·체크리스트·장비). 없으면 404 */
    @GetMapping("/active")
    public ActiveJobResponse active(@AuthenticationPrincipal CurrentUser user) {
        return service.activeJob(user.id());
    }

    /** GET /manager/jobs/active/pause — 일시정지 화면(Job/Paused)의 "…부터 중단됨"·안내문. 일시정지 중인 작업이 없으면 404 */
    @GetMapping("/active/pause")
    public PausedJobResponse paused(@AuthenticationPrincipal CurrentUser user) {
        return service.pausedJob(user.id());
    }

    /** GET /manager/jobs/last-completed — 완료 화면(Job/Complete): 가장 최근에 끝낸 작업의 요약·예상 정산. 없으면 404 */
    @GetMapping("/last-completed")
    public CompletedJobResponse lastCompleted(@AuthenticationPrincipal CurrentUser user) {
        return service.lastCompleted(user.id());
    }

    /** GET /manager/jobs/{id} — 작업 상세(Job/Detail). 취소된 예약이면 409(INVALID_STATE) */
    @GetMapping("/{id}")
    public JobResponse job(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.job(user.id(), id);
    }

    /** GET /manager/jobs/{id}/contact — 고객 전화번호·지도 검색어. 방문일 ±1일 밖이면 404 (앱은 null 로 받아 버튼을 끈다) */
    @GetMapping("/{id}/contact")
    public ContactResponse contact(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.contact(user.id(), id);
    }

    /** POST /manager/jobs/{id}/start — "청소 시작". scheduled 이고 방문일이 오늘일 때만 in_progress 로 → { jobId, startedAt }. 다른 진행 중 작업이 있으면 409 */
    @PostMapping("/{id}/start")
    public StartResponse start(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.start(user.id(), id, key);
    }

    /**
     * POST /manager/jobs/{id}/pause — 작업 일시정지(본문 { reason }). 앱이 라이브 영상 차단(live pause)을 먼저 끝낸 뒤 부른다.
     * 영상이 아직 나갈 수 있으면 409 "영상을 먼저 중단해주세요". 사유가 비었거나 목록에 없으면 400
     */
    @PostMapping("/{id}/pause")
    public PauseResponse pause(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @Valid @RequestBody PauseRequest req) {
        return service.pause(user.id(), id, req.reason());
    }

    /** POST /manager/jobs/{id}/resume — 작업 재개(paused → in_progress). 앱은 이 다음에 라이브 영상 재개를 따로 부른다 */
    @PostMapping("/{id}/resume")
    public JobIdResponse resume(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.resume(user.id(), id);
    }

    /** 매니저 방문 취소 → 예약은 살아 있고 다시 매칭된다 (docs/manager-policy.md) */
    // 본문 { reason, detail }. 응답은 고객 예약 카드 모양(ReservationResponse)이다 — 서비스가 bookings.reservation 을 그대로 돌려준다
    @PostMapping("/{id}/cancel")
    public kr.ansimclean.api.booking.dto.ReservationResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                                                    @Valid @RequestBody kr.ansimclean.api.job.dto.ManagerCancelRequest req,
                                                                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.cancelByManager(user.id(), id, req.reason(), req.detail(), key);
    }

    /** POST /manager/jobs/{id}/complete — "청소 완료". 정산이 같은 트랜잭션에서 만들어지고 완료 푸시는 아웃박스로 나중에 나간다 → { jobId } */
    @PostMapping("/{id}/complete")
    public JobIdResponse complete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.complete(user.id(), id, key);
    }

    /** PUT /manager/jobs/{id}/checklist/{itemId} — 체크리스트 한 줄 체크·해제(본문 { done }). 진행 중(in_progress·paused)일 때만 */
    @PutMapping("/{id}/checklist/{itemId}")
    public ChecklistResponse checklist(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ChecklistRequest req) {
        return service.setChecklistItem(user.id(), id, itemId, req.done());
    }
}
