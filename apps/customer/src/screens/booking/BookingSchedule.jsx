// (코드에서 정함) 날짜/시간 선택 — 2026-10-31 사용자 첨부 시안 '날짜/시간을 선택해 주세요.' (Figma 에 없음)
// 주소 선택 "다음" 에서 온다 (/booking/:category/schedule). 새 예약 전용 — 내 예약 › 예약 변경(?edit=)은 예전 화면 BookingDateTime 그대로.
//   서비스 시간: 기본 2시간, '변경' → 아래 시트(DurationStepper)에서 1시간씩 최대 8시간. 고른 값은 초안 durationHours (서버에는 아직 안 보낸다)
//   방문 일: '선택' → 방문일 화면(BookingVisitDate), 방문 시간: '선택' → 방문 시간 화면(BookingVisitTime, 날짜가 없으면 방문일 먼저)
//   (정기 예약 할인 안내 상자와 '시간' 옆 ? 아이콘은 2026-11-01 사용자 요청으로 뺐다)
//   아래 금액은 사용자 요청으로 임의 책정한 표시용 (mock.js SCHEDULE_PRICE). 실제 금액은 확인 화면의 서버 견적이 정한다
// 예약 가능 날짜·시간 판단(최소 3시간·예약 가능 기간·막힌 시간)은 예전 화면과 같은 useBookingAvailability.
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, ManagerIntro, SectionDivider, ScheduleRow, PriceCtaBar, DurationStepper, Sheet, Button, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError, showToast, formatDateShortWeekday, fromDateKey } from '@ansimclean/ui';
import { getTimeSlots, saveBookingStep } from '../../api/customer.js';
import { SCHEDULE_COPY as COPY, SCHEDULE_PRICE as PRICE } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import useBookingAvailability from './useBookingAvailability.js';
import { won, schedulePrice } from './bookingPrice.js';
import styles from './BookingSchedule.module.css';

/** '09:00' → '9:00' */
const shortTime = (t) => t.replace(/^0(\d)/, '$1');

/** 날짜/시간 선택 화면. 서비스 시간·방문 일·방문 시간 세 줄 + 금액과 "다음" */
export default function BookingSchedule() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 확인 화면 '수정하기'(?returnTo=)에서 왔으면 "다음" 이 그 화면으로 돌아간다
  const [draft, update] = useBookingDraft(category);
  const { data: slots, error, reload } = useAsync(() => getTimeSlots(), []);
  const availability = useBookingAvailability(slots, draft.date, draft.time, category);
  const [sheet, setSheet] = useState(null); // 'duration' | 'detail' | null
  const hours = draft.durationHours ?? PRICE.standardHours;
  const [pending, setPending] = useState(hours); // 시트 안에서 고치는 중인 시간 — '확인' 을 눌러야 초안에
  const frequency = draft.frequency ?? 'recurring';

  const next = useAction(async () => {
    if (!availability.isStillAvailable()) {
      showToast('예약 가능한 날짜와 시간을 다시 선택해주세요');
      return;
    }
    await saveBookingStep(category, 'schedule', { date: draft.date, time: draft.time, durationHours: hours });
    update({ durationHours: hours });
    navigate(back.next(`/booking/${category}/options`));
  });

  const navBar = <NavBar onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!slots) return <ScreenLoading navBar={navBar} />;

  const hasDate = Boolean(draft.date) && !availability.isDateDisabled(fromDateKey(draft.date));
  const hasTime = hasDate && Boolean(draft.time) && !availability.isTimeDisabled(draft.time);
  const openDuration = () => {
    setPending(hours);
    setSheet('duration');
  };

  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <>
          <PriceCtaBar priceLabel={won(schedulePrice(hours, frequency))} detailLabel={COPY.detail} onDetail={() => setSheet('detail')}>
            <Button variant="primary" chevron={false} className={common.ctaRounded} disabled={!availability.canContinue} loading={next.loading} onClick={next.run}>
              다음
            </Button>
          </PriceCtaBar>
          <ActionError error={next.error} fallback="일정을 저장하지 못했어요. 다시 시도해주세요." />
        </>
      }
    >
      <div className={styles.body}>
        <div className={cx(common.page, styles.top)}>
          <h1 className={cx('text-heading-m', common.pageTitle)}>{COPY.title}</h1>
          {/* TODO(figma): '어떤 교육을 받나요?' 를 누르면 갈 화면이 시안에 없다 (PROGRESS.md 판단 항목) */}
          <ManagerIntro title={COPY.intro} linkLabel={COPY.introLink} />
        </div>

        <SectionDivider />

        <section className={cx(common.page, styles.times)} aria-labelledby="schedule-time-title">
          <h2 id="schedule-time-title" className={cx('text-heading-s', common.pageTitle, styles.sectionTitle)}>
            {COPY.timeSection}
          </h2>
          <ScheduleRow value={`${hours}시간`} caption={COPY.duration} actionLabel={COPY.change} onAction={openDuration} />
          <ScheduleRow value={hasDate ? formatDateShortWeekday(draft.date) : undefined} caption={COPY.date} actionLabel={hasDate ? COPY.change : COPY.choose} onAction={() => navigate(`/booking/${category}/visit-date`)} />
          <ScheduleRow
            value={hasTime ? shortTime(draft.time) : undefined}
            caption={COPY.time}
            actionLabel={hasTime ? COPY.change : COPY.choose}
            onAction={() => navigate(`/booking/${category}/${hasDate ? 'visit-time' : 'visit-date'}`)}
          />
        </section>
      </div>

      {/* 서비스 시간 시트: −/+ 로 고치고 '확인' 을 눌러야 반영 */}
      <Sheet open={sheet === 'duration'} onClose={() => setSheet(null)} ariaLabel={COPY.duration}>
        <DurationStepper
          hours={pending}
          min={PRICE.standardHours}
          max={PRICE.maxHours}
          onChange={setPending}
          priceLabel={won(schedulePrice(pending, frequency))}
          note={COPY.durationNote(PRICE.standardHours)}
          className={styles.stepper}
        />
        <Button
          variant="primary"
          chevron={false}
          className={common.ctaRounded}
          onClick={() => {
            update({ durationHours: pending });
            setSheet(null);
          }}
        >
          {COPY.confirm}
        </Button>
      </Sheet>

      {/* '자세히': 금액이 어떻게 나왔는지 */}
      <Sheet open={sheet === 'detail'} onClose={() => setSheet(null)} title={COPY.detailTitle}>
        <dl className={styles.detail}>
          {COPY.detailRows(hours, PRICE.hourly, frequency === 'recurring' ? PRICE.recurringDiscount : 0).map(([k, v]) => (
            <div key={k} className={styles.detailRow}>
              <dt className={cx('text-body-default', styles.detailKey)}>{k}</dt>
              <dd className={cx('text-body-bold', styles.detailValue)}>{v}</dd>
            </div>
          ))}
        </dl>
        <p className={cx('text-body-s', styles.detailNote)}>{COPY.detailNote}</p>
        <Button variant="primary" chevron={false} className={common.ctaRounded} onClick={() => setSheet(null)}>
          {COPY.confirm}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
