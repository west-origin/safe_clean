// Figma: 방문 날짜와 시간 선택 (1:356) — StepProgress 5/8 → 질문 → 달력(한 주 띠) → 방문 시간 선택(flat 3열) → 선택 일정 요약(📅 "9월 23일(수) 오전 11시") → "다음". 다음 → /notes
// 고른 날짜('YYYY-MM-DD')·시간('HH:mm')은 수리 예약 초안의 date·time 에 저장한다.
// 고를 수 있는 날짜 범위(bookableDays)·시간표(timeSlots)·변경 기한(changeDeadlineHours)은 getRepairBooking 이 서버 시간표에서 받아 온 값이다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, StepQuestion, WeekCalendar, TimeSlotGrid, InfoNote, Button, ScreenError, ScreenLoading, useAsync, cx, notBookableWithin, isWithinHours } from '@ansimclean/ui';
import { getRepairBooking } from '../../../api/customer.js';
import { BOOKING_DATE_NOTE, CHANGE_LOCK_COPY } from '../../../api/mock.js';
import { useBookingDraft } from '../../booking/bookingDraft.js';
import { draftKey, path, stepOf, serviceName, dateShort, timeKo } from './repairFlow.js';
import { useReturnTo } from '../../booking/useReturnTo.js';
import styles from './repair.module.css';

// 바로 아래 한 줄은 화면 설명이 아니라 달력의 날짜 규칙 설명이다. 실제 판정은 WeekCalendar 에 넘기는
// notBookableWithin 이 하고, 예약 가능 기간(bookableDays) 밖의 날도 막는다 (화면 설명은 그 아래)
/** 서버 규칙과 같게 오늘·이전은 고를 수 없고, 주말은 허용한다. */

/** 방문 날짜·시간을 고르는 화면. 둘 다 골라야 '다음' 을 누를 수 있다 */
export default function RepairDateTime() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo();
  const [draft, update] = useBookingDraft(draftKey(category));
  const { data: catalog, error, reload } = useAsync(() => getRepairBooking(), []);
  const { step, total } = stepOf(category, 'datetime');
  const navBar = <StepNavBar step={step} total={total} onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!catalog) return <ScreenLoading navBar={navBar} />;

  const copy = catalog.copy.datetime;
  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={styles.body}>
        <div className={cx(styles.stack, styles.stackWide)}>
          <StepQuestion service={serviceName(category, catalog, draft)} title={copy.title} description={copy.description} />
          {/* 오늘·지난 날과 예약 가능 기간 밖은 막되, 토·일도 선택할 수 있다. */}
          <WeekCalendar value={draft.date} onChange={(date) => update({ date })} isDisabled={notBookableWithin(catalog.bookableDays, undefined, { allowWeekends: true })} />
          <InfoNote glyph="📅">{BOOKING_DATE_NOTE}</InfoNote>
          <TimeSlotGrid layout="flat" title={copy.timeTitle} slots={catalog.timeSlots} value={draft.time} onChange={(time) => update({ time })} />
          {/* 날짜와 시간을 둘 다 고르면 고른 일정을 한 줄로 보여 준다 (예: 9월 23일(수) 오전 11시) */}
          {draft.date && draft.time && (
            <InfoNote glyph="📅" layout="inline">
              {dateShort(draft.date)} {timeKo(draft.time)}
            </InfoNote>
          )}
          {/* 고른 방문이 지금부터 변경·취소 기한(changeDeadlineHours 시간) 안이면, 예약한 뒤엔 바꾸거나 취소할 수 없다고 미리 알린다 */}
          {isWithinHours(draft.date, draft.time, catalog.changeDeadlineHours) && ( // 직접 사용 점검 U2
            <InfoNote tone="warning" glyph="!">
              {CHANGE_LOCK_COPY.picked(catalog.changeDeadlineHours)}
            </InfoNote>
          )}
        </div>
        <div className={styles.cta}>
          {/* 확인 화면에서 고치러 왔으면(?returnTo=) 확인 화면으로, 아니면 현장 상황 단계로 */}
          <Button variant="primary" className={styles.ctaButton} disabled={!draft.date || !draft.time} onClick={() => navigate(back.next(path(category, 'notes')))}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
