package kr.ansimclean.api.common.activity;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 활동 이력 남기기 (rbac.md §11). 민감 작업은 **업무 처리와 같은 트랜잭션**에서 부른다 —
 * Propagation.MANDATORY 라 트랜잭션 밖에서 부르면 예외가 난다(업무는 됐는데 이력만 빠지는 일이 없게, 롤백되면 이력도 같이 사라진다).
 * 쓰는 법: activity.record(actor, ActivityLogService.NOTICE_UPDATE, "notice", id.toString(), before, after, null);
 * before·after 는 JSON 으로 바꿀 수 있는 값(record·Map). 개인정보(전화번호·주소·출입 정보 원문)는 넣지 않는다(§11-11 로그 규칙과 같다).
 */
@Service
public class ActivityLogService {
    // 행위 키 — 새 기록 대상을 만들 때 여기에 상수를 더한다 (글자는 DB 에 그대로 남으니 바꾸지 않는다)
    public static final String NOTICE_CREATE = "notice.create";
    public static final String NOTICE_UPDATE = "notice.update";
    public static final String NOTICE_DELETE = "notice.delete";
    public static final String BANNER_CREATE = "banner.create";
    public static final String BANNER_UPDATE = "banner.update";       // 대체 문구·게시 기간
    public static final String BANNER_IMAGE = "banner.image";         // 이미지 교체
    public static final String BANNER_REORDER = "banner.reorder";
    public static final String BANNER_DELETE = "banner.delete";
    public static final String CANCEL_REQUEST_APPROVE = "booking.cancel_request.approve"; // 매니저 방문 취소 요청 승인 (배정 풀림 → 재배정)
    public static final String CANCEL_REQUEST_REJECT = "booking.cancel_request.reject";   // 매니저 방문 취소 요청 거절
    public static final String BOOKING_ASSIGN = "booking.assign";                         // 어드민 직접 배정
    public static final String VOUCHER_REFUND_ACCOUNT_VIEW = "voucher_refund.account_view"; // 금액권 계좌 환불의 계좌번호 열람
    public static final String VOUCHER_REFUND_PAY = "voucher_refund.pay";                   // 송금 완료 처리
    public static final String VOUCHER_REFUND_REJECT = "voucher_refund.reject";             // 반려

    private final ActivityLogRepository logs;
    private final AppClock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public ActivityLogService(ActivityLogRepository logs, AppClock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    /**
     * 사람이 한 작업을 남긴다. actor 의 id·역할을 그대로 적는다.
     * @param targetId 대상 id (없으면 null)
     * @param before 변경 전 값 (새로 만들면 null)
     * @param after 변경 후 값 (지우면 null)
     * @param reason 사유 (없으면 null)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ActivityLog record(CurrentUser actor, String action, String targetType, String targetId, Object before, Object after, String reason) {
        return logs.save(ActivityLog.of(actor.id(), actor.role(), action, targetType, targetId, toJson(before), toJson(after), reason, clock.now()));
    }

    /** 시스템(스케줄러 등)이 한 작업을 남긴다 — 행위자 칸이 비어 있다 */
    @Transactional(propagation = Propagation.MANDATORY)
    public ActivityLog recordSystem(String action, String targetType, String targetId, Object before, Object after, String reason) {
        return logs.save(ActivityLog.of(null, null, action, targetType, targetId, toJson(before), toJson(after), reason, clock.now()));
    }

    /** 한 대상의 이력, 최근 것부터 */
    @Transactional(readOnly = true)
    public List<ActivityLog> history(String targetType, String targetId) {
        return logs.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }

    private String toJson(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }
}
