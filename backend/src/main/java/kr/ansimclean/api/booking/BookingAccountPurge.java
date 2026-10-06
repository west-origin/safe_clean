package kr.ansimclean.api.booking;

import kr.ansimclean.api.auth.AccountPurge;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 탈퇴 + 5년: 고객이 남긴 예약·정기청소의 개인정보 칸을 비운다(주소 → 시도·시군구만, 상세 주소·요청·주의사항·수리 상세·사진 경로·자유 입력 취소 사유).
 * 예약 행은 지우지 않는다 — 매니저의 일정·정산 이력과 결제 기록이 가리킨다. 매니저 쪽은 예약에 개인정보가 없어 할 일이 없다
 *
 * 부르는 곳: auth.DeletedAccountPurger (매일 04:20, 계정마다 트랜잭션 하나). AccountPurge 를 구현한 빈을 목록으로 받아 차례로 부른다.
 * 이렇게 인터페이스로 두면 auth 패키지가 booking 패키지를 직접 알 필요가 없다(AccountPurge 설명 참고).
 * 한 계정의 파기가 한 트랜잭션이라 아래 update 중 하나가 실패하면 그 계정의 파기 전체가 되돌려지고 다음 날 다시 시도된다.
 */
@Component
public class BookingAccountPurge implements AccountPurge {
    private final BookingRepository bookings;

    public BookingAccountPurge(BookingRepository bookings) {
        this.bookings = bookings;
    }

    /** 고객 계정이면 상태 이력 사유 → 예약 → 정기청소 순으로 개인정보 칸을 비운다. 다시 불려도 같은 값으로 덮어쓸 뿐이라 결과가 같다 */
    @Override // AccountPurge 인터페이스의 메서드를 여기서 구현한다는 표시
    public void purge(UUID userId, String role) {
        if (!Roles.CUSTOMER.equals(role)) return; // 매니저 계정은 할 일 없음 (위 설명)
        bookings.scrubCustomerHistory(userId);
        bookings.scrubCustomerBookings(userId);
        bookings.scrubCustomerSeries(userId);
    }
}
