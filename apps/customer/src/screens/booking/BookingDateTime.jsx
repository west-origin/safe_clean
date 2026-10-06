// Figma: Booking/DateTime (85:251) — 393×916. CalendarPanel(상 4) → TimePanel(상 9) → Button/Primary(상 12, 하 52). 4 카테고리 동일.
// "다음" 은 날짜·시간을 모두 고르면 활성 → /step2 (PROGRESS.md 판단 항목). 예약 변경(?edit=)은 서버가 24시간 규칙으로 거부할 수 있어 useAction 으로 실패 문구를 보여준다
// 예약 플로우 2단계(/booking/:category/datetime). 두 가지로 쓰인다.
//   새 예약: 고른 날짜·시간을 예약 초안(sessionStorage)에 두고 다음 단계(Step2)로 간다. 서버에는 확인 화면에서 한꺼번에 간다.
//   예약 변경(?edit=예약 id): 내 예약 카드의 '예약 변경' 에서 온다. "변경 완료" 가 서버에 바로 일정 변경을 보낸다(changeReservation).
import { useEffect } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import { ScreenLayout, StepNavBar, NavBar, Calendar, TimeSlotGrid, InfoNote, Button, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError, notBookableWithin, isWithinHours, showToast } from '@ansimclean/ui';

// 달력에서 막는 날(아래 Calendar 의 isDisabled): 서버 규칙(§6.1 '내일부터 예약할 수 있어요')과 같게 — 오늘·이전·주말은 고를 수 없다
import { getTimeSlots, saveBookingStep, changeReservation } from '../../api/customer.js';
import { BOOKING_DATE_NOTE, CHANGE_LOCK_COPY } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingDateTime.module.css';

/** 날짜·시간 선택 화면. 달력 + 시간 버튼 + 안내 문구, 날짜·시간을 모두 골라야 버튼이 열린다 */
export default function BookingDateTime() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 확인 화면 '수정하기'(?returnTo=)에서 왔으면 "다음" 이 그 화면으로 돌아간다
  const [params] = useSearchParams();
  const editId = params.get('edit'); // 내 예약 › 예약 변경에서 왔을 때 (코드에서 정함). 초안은 예약 초안과 분리
  const [draft, update, clear] = useBookingDraft(editId ? `edit-${editId}` : category);
  const current = useLocation().state; // 예약 변경: 내 예약 카드가 넘긴 현재 일정 { date, time } — 달력·시간에 미리 채운다 (검증 검토)
  // deps 가 [editId] 뿐이라 이 화면에 들어올 때(또는 다른 예약으로 바뀔 때)만 돈다
  useEffect(() => {
    // 들어올 때마다 실제 현재 일정으로 — 저장하지 않고 나간 지난 선택이 '현재 일정' 처럼 채워지지 않게 (자체 점검 #36)
    if (editId && current?.date) update({ date: current.date, time: current.time });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId]);
  // 시간 버튼 목록 + 예약 가능 기간(bookableDays) + 변경·취소 기한(changeDeadlineHours)을 서버(GET /catalog/time-slots)에서 받는다
  const { data: slots, error, reload } = useAsync(() => getTimeSlots(), []);
  // 버튼 동작. 예약 변경이면 서버에 보내고, 성공한 뒤에만 변경 초안을 지우고 내 예약으로 간다 (실패하면 고른 값이 남는다).
  // 새 예약이면 값은 이미 초안에 있으므로(달력·시간을 누를 때 update) 다음 화면으로 가기만 한다
  const next = useAction(async () => {
    if (editId) {
      await changeReservation(editId, { date: draft.date, time: draft.time });
      clear();
      showToast('예약 일정을 바꿨어요'); // 취소는 알려 주는데 변경은 조용히 목록으로만 돌아갔다 (코드에서 정함, 전체 점검 G4)
      navigate('/reservations');
      return;
    }
    await saveBookingStep(category, 'datetime', { date: draft.date, time: draft.time });
    navigate(back.next(`/booking/${category}/step2`));
  });

  // 예약 변경은 8단계 플로우가 아니므로 진행 표시 대신 일반 상단 바
  const navBar = editId ? <NavBar title="예약 변경" onBack={() => navigate(-1)} /> : <StepNavBar step={2} onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!slots) return <ScreenLoading navBar={navBar} />;

  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={cx(common.body, styles.body)}>
        <div className={cx(common.stack, styles.stack)}>
          {/* 오늘·지난 날·주말과 예약 가능 기간(bookableDays) 뒤의 날짜는 막는다. 예약 변경이면 기간을 지금 방문일(오늘보다 뒤면)부터 센다 */}
          <Calendar value={draft.date} onChange={(date) => update({ date })} isDisabled={notBookableWithin(slots.bookableDays, editId ? current?.date : undefined)} />
          {/* 회색 날짜의 이유를 알 수 없었다 (사용성 검토 2026-09-22) */}
          <InfoNote glyph="📅">{BOOKING_DATE_NOTE}</InfoNote>
          <TimeSlotGrid slots={slots} value={draft.time} onChange={(time) => update({ time })} />
          {/* 고른 방문이 변경·취소 기한(방문 N시간 전) 안이면, 예약한 뒤엔 바꾸거나 취소할 수 없다고 미리 경고한다 */}
          {isWithinHours(draft.date, draft.time, slots.changeDeadlineHours) && (
            <InfoNote tone="warning" glyph="!">
              {CHANGE_LOCK_COPY.picked(slots.changeDeadlineHours)}
            </InfoNote>
          )}
        </div>
        <div className={cx(common.cta, styles.cta)}>
          <Button variant="primary" disabled={!draft.date || !draft.time} loading={next.loading} onClick={next.run}>
            {editId ? '변경 완료' : '다음'}
          </Button>
          {/* 실패 문구: 서버 문구(예: 변경 기한이 지나 거부된 BOOKING_NOT_CHANGEABLE)가 있으면 그것을, 없으면 fallback */}
          <ActionError error={next.error} fallback={'일정을 저장하지 못했어요. 다시 시도해주세요.'} className={styles.error} />
        </div>
      </div>
    </ScreenLayout>
  );
}
