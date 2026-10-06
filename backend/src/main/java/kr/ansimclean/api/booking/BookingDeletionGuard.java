package kr.ansimclean.api.booking;

import kr.ansimclean.api.auth.AccountDeletionGuard;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 탈퇴 차단 (§6.8, §5.1 deleteAccount): 끝나지 않은 예약·활성 정기청소가 있으면 안 된다. 매니저는 맡은 작업이 남아 있으면 안 된다 */
// auth.AccountDeletionGuard 의 예약 쪽 구현. AuthService.deleteAccount 가 프로필 행을 잠근 트랜잭션 안에서 모든 가드를 차례로 부르고,
// 문구가 돌아오면 그 문구를 message 로 INVALID_STATE 를 던져 탈퇴를 멈춘다.
// AuthService 는 가드를 List<AccountDeletionGuard> 로 받으므로, @Component 이기만 하면 그 목록에 자동으로 들어간다.
@Component
public class BookingDeletionGuard implements AccountDeletionGuard {
    // 탈퇴를 막을 때 돌려주는 안내 문구 (static final = 바뀌지 않는 상수. BookingDeletionGuardTest 가 이 값으로 비교한다)
    static final String CUSTOMER_BLOCKED = "진행 중이거나 예정된 예약이 있어 탈퇴할 수 없어요. 예약을 취소한 뒤 다시 시도해주세요";
    static final String MANAGER_BLOCKED = "맡은 작업이 남아 있어 탈퇴할 수 없어요";
    /** (코드에서 정함) 진행 중인 청소는 고객이 취소할 수 없다 — '취소한 뒤' 안내가 할 수 없는 일을 시키지 않게 (자체 점검 #17) */
    static final String CUSTOMER_IN_PROGRESS = "청소가 진행 중인 예약이 있어 탈퇴할 수 없어요. 청소가 끝난 뒤 다시 시도해주세요";

    private final BookingService bookings;

    public BookingDeletionGuard(BookingService bookings) {
        this.bookings = bookings;
    }

    /**
     * 막을 이유 문구, 괜찮으면 null.
     * 매니저: 자기에게 배정된 예약 중 끝나지 않은 것(BookingStatus.OPEN)이 하나라도 있으면 막는다.
     * 고객: 진행 중·일시정지 작업이 있으면 '끝난 뒤' 문구를 먼저, 그다음 끝나지 않은 예약이나 활성 정기청소가 있으면 '취소한 뒤' 문구.
     */
    @Override
    public String blockReason(UUID userId, String role) {
        if (Roles.MANAGER.equals(role)) return bookings.hasOpenJobs(userId) ? MANAGER_BLOCKED : null;
        // 진행 중 예약도 OPEN 에 들어 있어 아래 검사에 걸리지만, 고객이 취소할 수 없으니 먼저 따로 거른다
        if (bookings.hasActiveJobsAsCustomer(userId)) return CUSTOMER_IN_PROGRESS;
        return bookings.hasOpenBookings(userId) || bookings.hasActiveSeries(userId) ? CUSTOMER_BLOCKED : null;
    }
}
