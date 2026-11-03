// (코드에서 정함) 예약 화면 하단 줄 — 왼쪽 '총 결제금액' 글자 + 금액, 오른쪽 "다음" 버튼. 2026-11-03 사용자 요청:
// 날짜/시간 화면의 하단을 컴포넌트로 빼서 옵션·주의사항 화면도 같은 모양으로 쓴다 (예전 '자세히' 버튼·시트는 없앴다).
// 금액은 화면이 계산해 넘긴다(bookingPrice.js — 표시용). 버튼 위에 둘 안내·오류(예: 빨간 '선택해 주세요')는 children 으로.
// 쓰는 곳: BookingSchedule · BookingOptions · BookingNotesRequests 의 ScreenLayout stickyCta.
import { PriceCtaBar, Button } from '@ansimclean/ui';
import { SCHEDULE_COPY as COPY } from '../../api/mock.js';
import { won } from './bookingPrice.js';
import common from './booking.module.css';

/**
 * @param {object} props
 * @param {number} props.price 총 결제금액(원)
 * @param {() => void} props.onNext "다음"
 * @param {boolean} [props.disabled] "다음" 끄기
 * @param {boolean} [props.loading] "다음" 처리 중
 * @param {import('react').ReactNode} [props.children] 하단 줄 위에 둘 안내·오류
 */
export default function BookingPriceFooter({ price, onNext, disabled = false, loading = false, children }) {
  return (
    <>
      {children}
      <PriceCtaBar priceLabel={won(price)} caption={COPY.total}>
        <Button variant="primary" chevron={false} className={common.ctaRounded} disabled={disabled} loading={loading} onClick={onNext}>
          다음
        </Button>
      </PriceCtaBar>
    </>
  );
}
