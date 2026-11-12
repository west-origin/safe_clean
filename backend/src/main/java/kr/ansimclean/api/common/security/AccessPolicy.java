package kr.ansimclean.api.common.security;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 권한 판단을 한 곳에서 한다 (rbac.md §1-2). 쓰기와 민감 조회는 컨트롤러·서비스가 역할을 if 로 가르지 않고 여기를 부른다:
 * <pre>policy.require(actor, AccessPolicy.Action.JOB_FORCE_COMPLETE, AccessPolicy.Resource.of("booking", id));</pre>
 * 규칙에 없는 조합은 거부한다(기본 거부, §1-3). 경로 단위 검사(SecurityConfig)와 별개인 두 번째 겹이다.
 * 새 기능을 만들 때: Action 에 항목을 더하고 아래 switch 에 규칙 한 줄. 규칙의 근거(rbac.md 절)를 주석으로 남긴다.
 * 지금 규칙(R4): 어드민 전용 작업 — 역할 admin ∧ 가입 완료 ∧ MFA(aal2) 세션. 권역·상태 규칙은 R5 이후에 더한다.
 */
@Component
public class AccessPolicy {
    /** 권한을 확인할 작업 */
    public enum Action {
        JOB_FORCE_COMPLETE,  // 운영자 작업 강제 완료 (POST /admin/jobs/{id}/complete) — rbac.md §6.3 in_progress·paused → done
        DEVICE_REGISTER,     // 바디캠 등록 (POST /admin/devices)
        BOOKING_REDISPATCH,  // 운영자 재매칭 (POST /admin/bookings/{id}/redispatch)
        PENALTY_WAIVE,       // 패널티 면제 (POST /admin/penalties/{id}/waive) — §4.1 정산·보너스·페널티 관리
        INQUIRY_ANSWER,      // 문의 답변 (POST /admin/inquiries/{id}/answer) — §10 (권역 문의의 지역 매니저 처리는 R15)
        NOTICE_MANAGE,       // 공지 목록·등록·수정·삭제 (/admin/notices/**) — §10 "공지 등록·수정·삭제는 어드민만" (권역 지정은 R16)
    }

    /** 작업 대상. type = 대상 종류("booking"·"penalty" 등), id = 그 행 id(없으면 null). 권역 규칙(R5 이후)이 여기에 권역을 더한다 */
    public record Resource(String type, UUID id) {
        public static Resource of(String type, UUID id) {
            return new Resource(type, id);
        }
    }

    /** 허용이면 그냥 돌아오고, 아니면 FORBIDDEN(403)을 던진다. actor 가 null 이면(로그인 없음) UNAUTHENTICATED */
    public void require(CurrentUser actor, Action action, Resource resource) {
        if (actor == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        if (!allows(actor, action, resource)) throw new ApiException(ErrorCode.FORBIDDEN);
    }

    /** 규칙 표. 여기에 없는 조합은 false(기본 거부) */
    boolean allows(CurrentUser actor, Action action, Resource resource) {
        return switch (action) {
            // 기존 관리자 API 5개는 어드민 + MFA 만 (rbac.md §2.1 — 공용 X-Admin-Token 폐지)
            case JOB_FORCE_COMPLETE, DEVICE_REGISTER, BOOKING_REDISPATCH, PENALTY_WAIVE, INQUIRY_ANSWER -> isAdminWithMfa(actor);
            case NOTICE_MANAGE -> isAdminWithMfa(actor); // rbac.md §10
        };
    }

    /** 어드민 작업의 공통 조건: 역할 admin ∧ 가입 완료 ∧ 이 토큰이 MFA(aal2) 를 통과했다 */
    private static boolean isAdminWithMfa(CurrentUser actor) {
        return actor.isAdmin() && actor.profileComplete() && actor.mfa();
    }
}
