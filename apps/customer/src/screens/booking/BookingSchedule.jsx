// (코드에서 정함) 날짜/시간 선택 — 2026-10-31 사용자 첨부 시안 '날짜/시간을 선택해 주세요.' (Figma 에 없음)
// 주소 선택 "다음" 에서 온다 (/booking/:category/schedule). 새 예약 전용 — 내 예약 › 예약 변경(?edit=)은 예전 화면 BookingDateTime 그대로.
// 2026-11-03 사용자 첨부 시안으로 아래쪽을 바꿨다 (색은 시안의 보라 대신 브랜드 파랑 — 사용자 요청):
//   '방문 일' 상자(누르면 펼쳐지며 좌우로 넘기는 달력 SwipeCalendar) → '방문 시간' 상자(시작·종료 시간 TimeRangeGrid).
//   서비스 시간은 따로 고르지 않고 종료 − 시작 으로 정한다 (최소 standardHours ~ 최대 maxHours). 초안: date · time(시작) · endTime · durationHours.
//   하단은 '자세히' 버튼 대신 금액 위 '총 결제금액' 글자. 금액은 고른 시간만큼 바뀐다 (고르기 전엔 표준 시간 금액).
//   예전 '서비스 시간' 줄·시간 시트·방문일/방문 시간 화면 이동은 이 화면에서 뺐다 (BookingVisitDate·BookingVisitTime 라우트는 남아 있다).
//   아래 금액은 사용자 요청으로 임의 책정한 표시용 (mock.js SCHEDULE_PRICE). 실제 금액은 확인 화면의 서버 견적이 정한다
// 예약 가능 날짜·시간 판단(최소 3시간·예약 가능 기간·막힌 시간)은 예전 화면과 같은 useBookingAvailability.
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, ManagerIntro, SectionDivider, ScheduleAccordion, SwipeCalendar, TimeRangeGrid, hoursBetween, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError, showToast, formatDateShortWeekday, fromDateKey, startOfToday, toDateKey } from '@ansimclean/ui';
import { getTimeSlots, saveBookingStep } from '../../api/customer.js';
import { SCHEDULE_COPY as COPY, SCHEDULE_PRICE as PRICE } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import useBookingAvailability from './useBookingAvailability.js';
import { schedulePrice } from './bookingPrice.js';
import BookingPriceFooter from './BookingPriceFooter.jsx';
import styles from './BookingSchedule.module.css';

const DEFAULT_BOOKABLE_DAYS = 60; // 서버가 bookableDays 를 주지 않을 때 달력을 그릴 기간 (BookingVisitDate 와 같다)

/** 오늘이 든 달부터 days 뒤가 든 달까지 [{ year, month }] */
function monthsAhead(today, days) {
  const last = new Date(today.getFullYear(), today.getMonth(), today.getDate() + days);
  const list = [];
  for (let d = new Date(today.getFullYear(), today.getMonth(), 1); d <= last; d = new Date(d.getFullYear(), d.getMonth() + 1, 1)) {
    list.push({ year: d.getFullYear(), month: d.getMonth() + 1 });
  }
  return list;
}

/** '2026-11-23' → '23일(월)' */
const dayLabel = (key) => formatDateShortWeekday(key).replace(/^\d+월\s*/, '');

/** 날짜/시간 화면. 매니저 안내 + 방문 일·방문 시간 상자 + 총 결제금액과 "다음" */
export default function BookingSchedule() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 확인 화면 '수정하기'(?returnTo=)에서 왔으면 "다음" 이 그 화면으로 돌아간다
  const [draft, update] = useBookingDraft(category);
  const { data: slots, error, reload } = useAsync(() => getTimeSlots(), []);
  const availability = useBookingAvailability(slots, draft.date, draft.time, category);
  const frequency = draft.frequency ?? 'recurring';
  // 펼친 상자: 날짜가 없으면 방문 일, 종료 시간이 없으면 방문 시간
  const [open, setOpen] = useState(() => (!draft.date ? 'date' : !draft.endTime ? 'time' : null));

  const times = [...(slots?.am ?? []), ...(slots?.pm ?? [])];
  const hasDate = Boolean(slots) && Boolean(draft.date) && !availability.isDateDisabled(fromDateKey(draft.date));
  const start = hasDate && draft.time && !availability.isTimeDisabled(draft.time) ? draft.time : null;
  const end = start && draft.endTime && times.includes(draft.endTime) && hoursBetween(start, draft.endTime) >= PRICE.standardHours ? draft.endTime : null;
  const hours = end ? hoursBetween(start, end) : PRICE.standardHours;

  const next = useAction(async () => {
    if (!availability.isStillAvailable() || !end) {
      showToast('예약 가능한 날짜와 시간을 다시 선택해주세요');
      return;
    }
    await saveBookingStep(category, 'schedule', { date: draft.date, time: start, endTime: end, durationHours: hours });
    update({ durationHours: hours });
    navigate(back.next(`/booking/${category}/options`));
  });

  const navBar = <NavBar onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!slots) return <ScreenLoading navBar={navBar} />;

  const today = startOfToday();
  const toggle = (box) => setOpen((now) => (now === box ? null : box));
  // 날짜를 고르면: 전에 고른 시작 시간이 그 날에 안 되면 시간을 지우고, 날짜 상자를 닫고 시간 상자를 연다
  const pickDate = (key) => {
    const keep = Boolean(draft.time) && !availability.isTimeDisabledOn(key, draft.time);
    update(keep ? { date: key } : { date: key, time: undefined, endTime: undefined, durationHours: undefined });
    setOpen('time');
  };
  const pickRange = ({ start: s, end: e }) => {
    update({ time: s, endTime: e ?? undefined, durationHours: e ? hoursBetween(s, e) : undefined });
  };

  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <BookingPriceFooter price={schedulePrice(hours, frequency)} disabled={!availability.canContinue || !end} loading={next.loading} onNext={next.run}>
          <ActionError error={next.error} fallback="일정을 저장하지 못했어요. 다시 시도해주세요." />
        </BookingPriceFooter>
      }
    >
      <div className={styles.body}>
        <div className={cx(common.page, styles.top)}>
          <h1 className={cx('text-heading-m', common.pageTitle)}>{COPY.title}</h1>
          {/* TODO(figma): '어떤 교육을 받나요?' 를 누르면 갈 화면이 시안에 없다 (PROGRESS.md 판단 항목) */}
          <ManagerIntro title={COPY.intro} linkLabel={COPY.introLink} />
        </div>

        <SectionDivider />

        <div className={cx(common.page, styles.boxes)}>
          <ScheduleAccordion icon="calendar" label={hasDate ? dayLabel(draft.date) : COPY.date} open={open === 'date'} onToggle={() => toggle('date')}>
            <SwipeCalendar
              months={monthsAhead(today, slots.bookableDays ?? DEFAULT_BOOKABLE_DAYS)}
              value={draft.date}
              onChange={pickDate}
              isDisabled={availability.isDateDisabled}
              todayKey={toDateKey(today)}
            />
          </ScheduleAccordion>

          <ScheduleAccordion icon="clock" label={end ? COPY.range(start, end, hours) : COPY.time} open={open === 'time'} onToggle={() => toggle('time')}>
            {hasDate ? (
              <div className={styles.timePanel}>
                <p className={cx('text-body-s', styles.hint)}>{start && !end ? COPY.endHint(start) : COPY.timeHint}</p>
                <TimeRangeGrid
                  times={times}
                  start={start}
                  end={end}
                  onChange={pickRange}
                  isStartDisabled={availability.isTimeDisabled}
                  minHours={PRICE.standardHours}
                  maxHours={PRICE.maxHours}
                />
              </div>
            ) : (
              <p className={cx('text-body-s', styles.hint)}>{COPY.dateTitle}</p>
            )}
          </ScheduleAccordion>
        </div>
      </div>
    </ScreenLayout>
  );
}
