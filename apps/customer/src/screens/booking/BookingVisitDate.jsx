// (코드에서 정함) 방문일 선택 — 2026-10-31 사용자 첨부 시안 '방문일을 선택해주세요.' (Figma 에 없음)
// 날짜/시간 화면(BookingSchedule)의 '방문 일 선택' 에서 온다 (/booking/:category/visit-date). X 로 닫으면 고르지 않고 돌아간다.
// 달력은 이번 달부터 예약 가능 기간(서버 bookableDays)이 끝나는 달까지 위아래로 이어 붙인다. 지난 날·고를 수 없는 날은 꺼진다
//   (판단은 예전 화면과 같은 useBookingAvailability — 최소 3시간·예약 가능 기간·막힌 시간).
// '확인': 고른 날을 초안에 넣는다. 전에 고른 시간이 그 날에 안 되면 지우고, 시간이 없으면 방문 시간 화면으로 바로 넘어간다.
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, ScrollCalendar, Button, cx, useAsync, ScreenError, ScreenLoading, startOfToday, toDateKey, fromDateKey } from '@ansimclean/ui';
import { getTimeSlots } from '../../api/customer.js';
import { SCHEDULE_COPY as COPY } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import useBookingAvailability from './useBookingAvailability.js';
import styles from './BookingVisitDate.module.css';

const DEFAULT_BOOKABLE_DAYS = 60; // 서버가 bookableDays 를 주지 않을 때 달력을 그릴 기간

/** 오늘이 든 달부터 days 뒤가 든 달까지 [{ year, month }] */
function monthsAhead(today, days) {
  const last = new Date(today.getFullYear(), today.getMonth(), today.getDate() + days);
  const list = [];
  for (let d = new Date(today.getFullYear(), today.getMonth(), 1); d <= last; d = new Date(d.getFullYear(), d.getMonth() + 1, 1)) {
    list.push({ year: d.getFullYear(), month: d.getMonth() + 1 });
  }
  return list;
}

/** 방문일 화면. 세로 달력 + 오른쪽 아래 '확인' */
export default function BookingVisitDate() {
  const navigate = useNavigate();
  const { category } = useParams();
  const [draft, update] = useBookingDraft(category);
  const [picked, setPicked] = useState(draft.date ?? null);
  const { data: slots, error, reload } = useAsync(() => getTimeSlots(), []);
  const availability = useBookingAvailability(slots, picked, draft.time, category);

  const navBar = <NavBar icon="close" onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!slots) return <ScreenLoading navBar={navBar} />;

  const today = startOfToday();
  const canConfirm = Boolean(picked) && !availability.isDateDisabled(fromDateKey(picked));
  const confirm = () => {
    const keepTime = Boolean(draft.time) && !availability.isTimeDisabled(draft.time);
    update({ date: picked, time: keepTime ? draft.time : undefined });
    if (keepTime) navigate(-1);
    else navigate(`/booking/${category}/visit-time`, { replace: true }); // 시간도 이어서 고른다 — 뒤로가기는 날짜/시간 화면으로
  };

  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <div className={common.ctaRight}>
          <Button variant="primary" chevron={false} className={common.confirmPill} disabled={!canConfirm} onClick={confirm}>
            {COPY.confirm}
          </Button>
        </div>
      }
    >
      <h1 className={cx('text-heading-m', common.pageTitle, styles.title)}>{COPY.dateTitle}</h1>
      <ScrollCalendar months={monthsAhead(today, slots.bookableDays ?? DEFAULT_BOOKABLE_DAYS)} value={picked} onChange={setPicked} isDisabled={availability.isDateDisabled} todayKey={toDateKey(today)} />
    </ScreenLayout>
  );
}
