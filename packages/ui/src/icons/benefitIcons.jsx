// Figma: Booking/Frequency › Banner/RecurringPromo › Benefit › Icon/Benefit (2169:2270 · 2169:2274 · 2169:2278). 16×16, stroke 1.33. path 원본 그대로, 색은 currentColor.

// 쓰는 곳(예): PromoBanner (정기청소 배너의 혜택 알약 3개 안 아이콘). 세 아이콘은 path 만 다르고 틀은 같다(아래 wrap).

// wrap(d): path 문자열 d 하나를 받아, 같은 틀(16 viewBox · stroke 1.33 · 둥근 끝)로 그리는 아이콘 컴포넌트를 만든다.
// 돌려주는 컴포넌트는 다른 아이콘처럼 size(px)·className 을 받는다.
const wrap = (d) =>
  function BenefitIcon({ size = 16, className }) {
    return (
      <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
        <path d={d} stroke="currentColor" strokeWidth={1.33333} strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  };

/** 원하는 주기 자동 예약 (달력) */
export const BenefitCalendarIcon = wrap(
  'M2 5.33333H14M4.66667 2V4.66667M11.3333 2V4.66667M3.33333 3.33333H12.6667C13.0203 3.33333 13.3594 3.47381 13.6095 3.72386C13.8595 3.97391 14 4.31304 14 4.66667V12.6667C14 13.0203 13.8595 13.3594 13.6095 13.6095C13.3594 13.8595 13.0203 14 12.6667 14H3.33333C2.97971 14 2.64057 13.8595 2.39052 13.6095C2.14048 13.3594 2 13.0203 2 12.6667V4.66667C2 4.31304 2.14048 3.97391 2.39052 3.72386C2.64057 3.47381 2.97971 3.33333 3.33333 3.33333Z',
);
/** 정기 이용 할인 혜택 (태그) */
export const BenefitTagIcon = wrap('M5 5H5.00667M13.7333 8.93333L8 14.6667L2 8.66667V2.66667C2 2.48986 2.07024 2.32029 2.19526 2.19526C2.32029 2.07024 2.48986 2 2.66667 2H8.33333L13.7333 8.93333Z');
/** 언제나 깨끗한 공간 (집) */
export const BenefitHomeIcon = wrap('M2 7L8 2L14 7M3.66667 6.33333V13.3333C3.66667 13.5101 3.7369 13.6797 3.86193 13.8047C3.98695 13.9298 4.15652 14 4.33333 14H11.6667C11.8435 14 12.013 13.9298 12.1381 13.8047C12.2631 13.6797 12.3333 13.5101 12.3333 13.3333V6.33333');
