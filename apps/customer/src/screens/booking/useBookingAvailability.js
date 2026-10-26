import { useEffect, useState } from 'react';
import { fromDateKey, isWithinHours, notBookableWithin, toDateKey } from '@ansimclean/ui';

// 방문 시각은 서울 기준. 수리의 기존 '내일부터' 규칙과 청소의 최소 3시간 규칙을 구분한다.
export function bookingAvailability(slots, date, time, category, horizonFrom, now = new Date()) {
  const cleaning = category !== 'repair';
  const hours = slots?.minimumLeadHours ?? 3;
  const times = [...(slots?.am ?? []), ...(slots?.pm ?? [])];
  const beforeLead = (key, value) => cleaning && isWithinHours(key, value, hours, now);
  const outsideDates = notBookableWithin(slots?.bookableDays, horizonFrom, { allowToday: cleaning, allowWeekends: true, now });
  const isDateDisabled = (day) => outsideDates(day) || (cleaning && times.every((value) => beforeLead(toDateKey(day), value) || slots?.unavailable?.includes(value)));
  const isTimeDisabled = (value) => !date || beforeLead(date, value) || Boolean(slots?.unavailable?.includes(value));
  const day = fromDateKey(date);
  return {
    isDateDisabled,
    isTimeDisabled,
    canContinue: Boolean(day && time && times.includes(time) && !isDateDisabled(day) && !isTimeDisabled(time)),
  };
}

// 열어 둔 화면에서도 경계가 지나면 바로 비활성화하고, 앱 복귀 시에도 현재 시각을 다시 읽는다.
export default function useBookingAvailability(slots, date, time, category, horizonFrom) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const refresh = () => setNow(new Date());
    const timer = setInterval(refresh, 1000);
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, []);
  return {
    ...bookingAvailability(slots, date, time, category, horizonFrom, now),
    // 클릭 직전 재검사: 직전 화면 갱신 후 경계를 넘은 경우도 진행하지 않는다.
    isStillAvailable: () => bookingAvailability(slots, date, time, category, horizonFrom).canContinue,
  };
}
