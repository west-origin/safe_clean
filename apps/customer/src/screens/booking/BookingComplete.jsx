// Figma: Booking/Complete (46:552) — 4 카테고리 동일. 그라디언트 배경 + CompleteIllustration (flat image, 문구 포함) + BookingInfoCard(예약일·시간·주소·담당 클리너).
// Figma 에 버튼·프로토타입 연결이 없어 '화면을 탭하면 /home' 이었는데, 아무 버튼도 안 보여 사용자가 멈췄다 (사용성 검토 2026-09-22)
// → 카드 아래에 '내 예약 보기'(기본)·'홈으로'(보조) 버튼을 두고, 보이지 않는 전체 화면 탭 영역은 없앴다. 값은 ?id= 로 예약을 다시 조회 (새로고침해도 남는다)
// (코드에서 정함, B3) 5번째 행 "결제 금액" = 서버가 저장한 총액 (정기청소는 "6회 · 480,000원" 꼴 — 회차 수는 서버 series_term_months, BACKEND_PLAN.md §9.2-7)
// 예약 완료 화면(/booking/:category/complete?id=…). 확인 화면(BookingConfirm)이 예약을 만든 뒤 replace 로 이리 보낸다.
// 예약 내용은 GET /customer/bookings/{id} 로 다시 받고, 카드 행의 라벨·아이콘은 mock.js COMPLETE_ROWS 에서 온다.
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, BookingInfoCard, Button, ScreenError, ScreenLoading, useAsync, ASSETS } from '@ansimclean/ui';
import { getBooking, getCompleteRows } from '../../api/customer.js';
import { formatDateTime, formatTimeKo } from './bookingDraft.js';
import styles from './BookingComplete.module.css';

/** 예약 완료 화면. 일러스트 + 예약 요약 카드 + '내 예약 보기'·'홈으로' 버튼 */
export default function BookingComplete() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const id = params.get('id');
  // 예약 내용과 카드 행 정의를 함께 받는다 (Promise.all: 둘 다 끝나야 data 가 생기고, 하나라도 실패하면 error)
  const { data, error, reload } = useAsync(() => Promise.all([getBooking(id), getCompleteRows()]).then(([booking, rows]) => ({ booking, rows })), [id]);

  // 404(예약을 못 찾음)면 제목·설명을 바꿔 보인다. 뒤로 가기는 내 예약 탭으로 보낸다
  if (error) return <ScreenError error={error} onRetry={reload} title={error?.status === 404 ? '예약 정보를 찾을 수 없어요' : undefined} description={error?.status === 404 ? '내 예약 탭에서 다시 확인해주세요.' : undefined} onBack={() => navigate('/reservations')} />;
  if (!data) return <ScreenLoading />;

  const { booking, rows } = data;
  // 카드에 넣을 값. 키(date·time·address·cleaner·amount)는 COMPLETE_ROWS 의 key 와 같아야 그 행에 들어간다
  const values = {
    date: formatDateTime(booking.date),
    time: formatTimeKo(booking.time) || (booking.time ?? ''), // 확인 화면·수리 완료와 같은 '오전 9시' 표기 (검증 검토)
    address: [booking.roadAddress, booking.detailAddress].filter(Boolean).join(' '),
    // 서버가 주는 문구 그대로 (배정 전 안내, 매칭 실패면 예약 목록과 같은 실패 안내, 매니저 없이 취소됐으면 '배정 전 취소' — api.md).
    // 수리 예약 완료 화면(RepairComplete)은 배정 전이면 Figma '배정 중' 이고, 배정됐거나 매칭에 실패했거나(matchingFailed) 취소됐을 때(cancelled)만 이 문구를 쓴다
    cleaner: booking.cleaner ?? '',
    // 정기청소는 "방문 횟수 · 총액", 1회는 총액만
    amount: booking.frequency === 'recurring' && booking.visits ? `${booking.visits.length}회 · ${booking.totalLabel}` : (booking.totalLabel ?? ''),
  };

  return (
    <ScreenLayout statusBar={false} noPadding gap="none" className={styles.screen}>
      {/* 완료 문구까지 그려진 래스터 일러스트 — 그래서 alt 에 그 문구를 적는다 */}
      <img className={styles.illustration} src={ASSETS.bookingComplete.src} srcSet={ASSETS.bookingComplete.srcSet} width={ASSETS.bookingComplete.width} height={ASSETS.bookingComplete.height} alt="예약이 완료되었어요!" />
      <div className={styles.bottom}>
        <BookingInfoCard rows={rows.map((r) => ({ icon: r.icon, label: r.label, value: values[r.key] }))} />
        <div className={styles.actions}>
          <Button variant="primary" chevron={false} onClick={() => navigate('/reservations')}>
            내 예약 보기
          </Button>
          {/* 일러스트 위라 글자만 있으면 잘 안 보인다 — 흰 버튼으로 */}
          <Button variant="secondary" chevron={false} className={styles.homeButton} onClick={() => navigate('/home')}>
            홈으로
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
