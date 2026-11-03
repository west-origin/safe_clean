// Figma: Booking/Complete (46:552) — 예전에는 그라디언트 배경 + 문구가 든 일러스트 + BookingInfoCard 였다.
// 2026-11-03 사용자 첨부 '예약 완료' 시안으로 바꿨다 (Figma 에 없음): 뒤로가기, 초록 원 체크, '예약 완료' + 설명 두 줄,
//   '주문 금액'·'총 결제 금액'(파랑) 두 줄, 아래 '홈으로 가기'(회색)·'예약상세 보기'(파랑) 버튼. 설명 문구는 사용자가 정했다 (mock.js BOOKING_COMPLETE_COPY).
//   예약 하나를 보는 상세 화면이 따로 없어 '예약상세 보기' 는 내 예약 탭(/reservations)으로 간다 — 결제하기 버튼도 거기 있다.
//   예전 카드(예약일·시간·주소·담당 클리너)와 '결제하기' 버튼은 시안에 없어 뺐다. 뒤로가기는 확인 화면이 아니라 홈으로 (예약은 이미 만들어졌다).
// 예약 완료 화면(/booking/:category/complete?id=…). 확인 화면(BookingConfirm)이 예약을 만든 뒤 replace 로 이리 보낸다.
// 금액은 GET /customer/bookings/{id} 로 다시 받은 서버 총액(totalLabel) — 할인 항목이 따로 없어 두 줄이 같은 값이다.
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, NavBar, Button, SuccessCheckIcon, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getBooking } from '../../api/customer.js';
import { BOOKING_COMPLETE_COPY as COPY } from '../../api/mock.js';
import common from './booking.module.css';
import styles from './BookingComplete.module.css';

/** 예약 완료 화면. 체크 + 제목·설명 + 금액 두 줄 + '홈으로 가기'·'예약상세 보기' */
export default function BookingComplete() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const id = params.get('id');
  const { data: booking, error, reload } = useAsync(() => getBooking(id), [id]);

  // 404(예약을 못 찾음)면 제목·설명을 바꿔 보인다. 뒤로 가기는 내 예약 탭으로 보낸다
  if (error) return <ScreenError error={error} onRetry={reload} title={error?.status === 404 ? '예약 정보를 찾을 수 없어요' : undefined} description={error?.status === 404 ? '내 예약 탭에서 다시 확인해주세요.' : undefined} onBack={() => navigate('/reservations')} />;
  const navBar = <NavBar onBack={() => navigate('/home', { replace: true })} />;
  if (!booking) return <ScreenLoading navBar={navBar} />;

  const amount = booking.totalLabel ?? '';
  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={cx(common.page, styles.body)}>
        <header className={styles.header}>
          <SuccessCheckIcon className={styles.check} />
          <h1 className={cx('text-heading-l', styles.title)}>{COPY.title}</h1>
          <p className={cx('text-body-default', styles.description)}>{COPY.description}</p>
        </header>

        <dl className={styles.amounts}>
          <div className={styles.row}>
            <dt className="text-body-default">{COPY.orderAmount}</dt>
            <dd className={cx('text-body-bold', styles.value)}>{amount}</dd>
          </div>
          <div className={cx(styles.row, styles.total)}>
            <dt className="text-body-default">{COPY.totalAmount}</dt>
            <dd className={cx('text-body-bold', styles.value)}>{amount}</dd>
          </div>
        </dl>

        <div className={styles.actions}>
          <Button variant="secondary" chevron={false} className={common.ctaRounded} onClick={() => navigate('/home', { replace: true })}>
            {COPY.home}
          </Button>
          <Button variant="primary" chevron={false} className={common.ctaRounded} onClick={() => navigate('/reservations', { replace: true })}>
            {COPY.detail}
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
