// TimeRangeGrid 와 화면(BookingSchedule)이 같이 쓰는 시각 계산. 컴포넌트 파일과 나눠 둔다 (Fast Refresh 는 컴포넌트만 내보내는 파일에서 동작한다).

/** '09:30' → 570 (분) */
export const minutesOf = (t) => {
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
};

/** 두 시각 사이 시간 수 (예: '09:00'~'12:00' → 3) */
export const hoursBetween = (start, end) => (minutesOf(end) - minutesOf(start)) / 60;
