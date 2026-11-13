package kr.ansimclean.api.job;

import kr.ansimclean.api.auth.AccountPurge;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 탈퇴 + 5년: 매니저가 방문 취소 요청에 적은 상세(자유 입력 — 질병 등이 적힐 수 있다)를 비운다 (V068).
 * 요청 행·사유 key·시각은 남긴다 — 예약의 재배정 이력이 가리킨다. 다시 불려도 결과가 같다.
 * 부르는 곳: auth.DeletedAccountPurger (AccountPurge 구현체 목록, booking.BookingAccountPurge 와 같은 방식)
 */
@Component
public class CancelRequestAccountPurge implements AccountPurge {
    private final JobCancelRequestRepository requests;

    public CancelRequestAccountPurge(JobCancelRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    public void purge(UUID userId, String role) {
        if (!Roles.isManagerRole(role)) return; // 고객 계정은 할 일 없음
        requests.scrubDetails(userId);
    }
}
