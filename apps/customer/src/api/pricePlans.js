// 요금표 요금제 판단 (요금표 전환 2026-11-13, 6-b — 서버 PricingService.plan 과 같은 규칙, 숫자는 mock.js PRICE_PLANS).
// 예약 요청 본문(customer.js bookingBody)과 예약 화면 하단 표시 금액(screens/booking/bookingPrice.js)이 같이 쓴다. 실제 금액은 서버 견적이 정한다.
import { PRICE_PLANS as PLANS } from './mock.js';

/** 요금표를 쓰는 카테고리인가: 가정·원룸(home)·분해청소(appliance). 매장·사무실·입주는 예전 시간 요금 */
export const usesPricePlan = (category) => category === 'home' || category === 'appliance';

/** 가정/원룸 구분 — 홈의 '가정'·'원룸' 버튼이 초안 homeService 에 남긴 값. 없으면 가정 (서버는 home 예약에 homeType 이 꼭 있어야 한다) */
export const homeTypeOf = (draft) => (draft.homeService === 'studio' ? 'studio' : 'house');

/** 주 몇 회 (정기 요금제). 아직 안 골랐으면 1 */
export const visitsPerWeekOf = (draft) => ([1, 2, 3].includes(draft.visitsPerWeek) ? draft.visitsPerWeek : 1);

/**
 * 초안의 요금제 → { amount(결제 금액: 정기면 한 달), hours(1회 시간), visits(회차 수), weekly(주 N회 요금제인가) }.
 * 요금표를 쓰지 않는 카테고리면 null. 분해청소에서 후드 크기를 아직 안 골랐으면 amount 0.
 * 원룸 주 1회는 첫 달 금액(서버 견적도 첫 원룸 정기라고 보고 보여 준다 — 예약을 만들 때 다시 판정)
 */
export function draftPlan(category, draft) {
  if (category === 'appliance') return { amount: PLANS.hood.sizes[draft.hoodSize] ?? 0, hours: PLANS.hood.hours, visits: 1, weekly: false };
  if (category !== 'home') return null;
  const type = PLANS[homeTypeOf(draft)];
  if ((draft.frequency ?? 'recurring') !== 'recurring') return { amount: type.trial ?? type.once, hours: type.hours, visits: 1, weekly: false };
  const n = visitsPerWeekOf(draft);
  return { amount: type.firstMonth?.[n] ?? type.weekly[n], hours: type.hours, visits: n * PLANS.weeks, weekly: true };
}

/** 'YYYY-MM-DD' 의 요일을 서버 순서(0=월 … 6=일)로 */
export const weekdayOf = (dateKey) => {
  const [y, m, d] = dateKey.split('-').map(Number);
  return (new Date(y, m - 1, d).getDay() + 6) % 7;
};
