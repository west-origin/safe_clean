// 예약 화면 하단의 표시용 금액 (2026-10-31 사용자 요청으로 임의 책정 — mock.js SCHEDULE_PRICE).
// 날짜/시간 화면(BookingSchedule)과 옵션 화면(BookingOptions)이 같은 계산을 쓴다. 서버 견적·예약 생성도 같은 시간당 요금과 정기 할인을 적용한다.
// 요금표 전환(2026-11-13, 6-b): 가정·원룸(home)·분해청소(appliance)는 시간 요금 대신 요금표(mock.js PRICE_PLANS — 서버 V074 와 같은 숫자).
//   실제 금액은 확인 화면의 서버 견적이 정한다. 여기는 화면 하단 표시용이다.
import { SCHEDULE_PRICE as PRICE } from '../../api/mock.js';
import { draftPlan } from '../../api/pricePlans.js';

// 요금제 판단은 api/pricePlans.js 하나 — 예약 요청 본문(customer.js bookingBody)도 같은 함수를 쓴다
export { usesPricePlan, homeTypeOf, visitsPerWeekOf, draftPlan, weekdayOf } from '../../api/pricePlans.js';

/** 1234 → '1,234원' */
export const won = (n) => `${n.toLocaleString('ko-KR')}원`;

/** 서비스 시간 금액: 시간 × 시간당 금액, 정기면 할인 */
export const schedulePrice = (hours, frequency) => hours * PRICE.hourly - (frequency === 'recurring' ? PRICE.recurringDiscount : 0);

/** 옵션 가격 문구('+50,000원') → 50000. 숫자가 없으면 0 */
export const optionPrice = (label) => Number(String(label ?? '').replace(/[^0-9]/g, '')) || 0;

/**
 * 하단 '총 결제금액'(표시용). 요금제면 결제 금액 + 유료 옵션 × 회차 수(서버도 회차마다 더한다), 아니면 시간 요금 + 유료 옵션.
 * @param {number} hours 시간 요금일 때의 청소 시간
 * @param {number} [paidOptions=0] 고른 유료 옵션 1회분 합
 */
export function displayPrice(category, draft, hours, paidOptions = 0) {
  const plan = draftPlan(category, draft);
  if (plan) return plan.amount + paidOptions * plan.visits;
  return schedulePrice(hours, draft.frequency ?? 'recurring') + paidOptions;
}

