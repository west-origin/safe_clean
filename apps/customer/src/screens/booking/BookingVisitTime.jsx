// (코드에서 정함) 방문 시간 선택 — 2026-10-31 사용자 첨부 시안 '방문 시간을 선택해 주세요.' (Figma 에 없음)
// 날짜/시간 화면(BookingSchedule)의 '방문 시간 선택' 또는 방문일 '확인' 에서 온다 (/booking/:category/visit-time).
// 시간 목록은 서버(GET /catalog/time-slots)의 오전·오후, 고를 수 없는 시간(최소 3시간·막힌 시간)은 꺼진다.
// 날짜를 아직 안 골랐으면 방문일 화면으로 보낸다. '확인' 은 고른 시간을 초안에 넣고 날짜/시간 화면으로 돌아간다.
import { useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, TimeChoiceGrid, Button, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getTimeSlots } from '../../api/customer.js';
import { SCHEDULE_COPY as COPY } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import useBookingAvailability from './useBookingAvailability.js';
import styles from './BookingVisitTime.module.css';

/** 방문 시간 화면. 오전/오후 시간 + 오른쪽 아래 '확인' */
export default function BookingVisitTime() {
  const navigate = useNavigate();
  const { category } = useParams();
  const [draft, update] = useBookingDraft(category);
  const [picked, setPicked] = useState(draft.time ?? null);
  const { data: slots, error, reload } = useAsync(() => getTimeSlots(), []);
  const availability = useBookingAvailability(slots, draft.date, picked, category);

  const navBar = <NavBar icon="close" onBack={() => navigate(-1)} />;
  if (!draft.date) return <Navigate to={`/booking/${category}/visit-date`} replace />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!slots) return <ScreenLoading navBar={navBar} />;

  const canConfirm = Boolean(picked) && !availability.isTimeDisabled(picked);

  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <div className={common.ctaRight}>
          <Button
            variant="primary"
            chevron={false}
            className={common.confirmPill}
            disabled={!canConfirm}
            onClick={() => {
              update({ time: picked });
              navigate(-1);
            }}
          >
            {COPY.confirm}
          </Button>
        </div>
      }
    >
      <div className={cx(common.page, styles.page)}>
        <h1 className={cx('text-heading-m', common.pageTitle)}>{COPY.timeTitle}</h1>
        <TimeChoiceGrid
          groups={[
            { label: COPY.am, times: slots.am ?? [] },
            { label: COPY.pm, times: slots.pm ?? [] },
          ]}
          value={picked}
          onChange={setPicked}
          isDisabled={availability.isTimeDisabled}
        />
      </div>
    </ScreenLayout>
  );
}
