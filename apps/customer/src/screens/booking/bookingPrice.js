// 예약 화면 하단의 표시용 금액 (2026-10-31 사용자 요청으로 임의 책정 — mock.js SCHEDULE_PRICE).
// 날짜/시간 화면(BookingSchedule)과 옵션 화면(BookingOptions)이 같은 계산을 쓴다. 실제 결제 금액은 확인 화면의 서버 견적이 정한다.
import { SCHEDULE_PRICE as PRICE } from '../../api/mock.js';

/** 1234 → '1,234원' */
export const won = (n) => `${n.toLocaleString('ko-KR')}원`;

/** 서비스 시간 금액: 시간 × 시간당 금액, 정기면 할인 */
export const schedulePrice = (hours, frequency) => hours * PRICE.hourly - (frequency === 'recurring' ? PRICE.recurringDiscount : 0);

/** 옵션 가격 문구('+50,000원') → 50000. 숫자가 없으면 0 */
export const optionPrice = (label) => Number(String(label ?? '').replace(/[^0-9]/g, '')) || 0;
