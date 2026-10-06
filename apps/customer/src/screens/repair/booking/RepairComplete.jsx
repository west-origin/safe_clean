// Figma: 예약 완료 (1:236) — 그라디언트 배경, 완료 피드백 → 예약 정보 카드(방문 일정·시간·주소·기사 배정 "신규"/"배정 중") → 배정 안내(🔔) → "예약 확인"(→ 내 예약). 값은 ?id= 로 예약을 다시 조회
// 확인 화면(RepairConfirm)이 예약을 만든 뒤 ?id=<예약 id> 로 보낸다. 초안은 이미 지워졌으므로
// 예약 값(일정·주소·배정)은 getBooking(GET /customer/bookings/:id) 응답에서, 라벨·안내 문구는
// getRepairBooking 의 완료 문구에서 읽는다 — 새로고침해도 같은 내용이 보인다.
// (코드에서 정함) 매칭에 실패했거나(booking.matchingFailed) 취소된(booking.cancelled) 예약이면 기사 배정 행에 서버 문구(실패 안내·'배정 전 취소')를 보이고
// 배정 안내(🔔)는 숨기며, 머리글 설명은 배정 알림을 약속하는 뒷문장을 빼고 Figma 문구의 첫 문장만 보인다(copy.descriptionNoAssignment).
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, SuccessHeader, StackedInfoCard, StatusBadge, InfoNote, Button, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getBooking, getRepairBooking } from '../../../api/customer.js';
import { dateShort, timeKo } from './repairFlow.js';
import styles from './repair.module.css';

/** 수리 예약 완료 화면. 예약과 완료 문구를 함께 받아 그린다 */
export default function RepairComplete() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const id = params.get('id');
  // 예약 조회와 문구(카탈로그) 조회를 동시에 보내고, 둘 다 오면 { booking, copy } 로 묶는다
  const { data, error, reload } = useAsync(() => Promise.all([getBooking(id), getRepairBooking()]).then(([booking, catalog]) => ({ booking, copy: catalog.copy.complete })), [id]);

  // 없는 예약(404: 잘못된 id·남의 예약)이면 전용 문구, 그 밖의 오류는 ScreenError 기본 문구. 뒤로 가기는 내 예약 탭으로
  if (error) return <ScreenError error={error} onRetry={reload} title={error?.status === 404 ? '예약 정보를 찾을 수 없어요' : undefined} description={error?.status === 404 ? '내 예약 탭에서 다시 확인해주세요.' : undefined} onBack={() => navigate('/reservations')} />;
  if (!data) return <ScreenLoading />;

  const { booking, copy } = data;
  const address = [booking.roadAddress?.split(' ').slice(-2).join(' '), booking.detailAddress].filter(Boolean).join(' · '); // Figma "테헤란로 123 · 604동 1002호"
  // noAssignment = 이 예약에는 배정 알림이 오지 않는다: 매칭에 실패해 멈췄거나, 취소됐다.
  // 취소는 매칭 실패 표시 없이도(콜이 나가 있거나 밤 보류 중) 일어나서, matchingFailed 만 보면 취소된 예약을 다시 열 때 '배정 중' 과 배정 안내가 보였다 (합친 38건 검토 R16)
  const noAssignment = booking.matchingFailed || booking.cancelled;
  // 기사 배정 행: '신규' 배지 + 배정됐거나 noAssignment 면 서버가 준 cleaner 문구("김미영 매니저" / 매칭 실패 안내 / '배정 전 취소'), 그 밖에는 Figma '배정 중'.
  // 후보가 없어 예약 직후 매칭에 실패한 예약에 '배정 중' 을 보이면, 같은 때 온 실패 푸시·내 예약 카드의 실패 안내와 반대로 읽혔다 (문제 목록 8번)
  const showServerCleaner = booking.managerAssigned || noAssignment;
  const rows = [
    { icon: 'calendar', label: copy.rows.date, value: dateShort(booking.date) },
    { icon: 'clock', label: copy.rows.time, value: timeKo(booking.time) },
    { icon: 'pin', label: copy.rows.address, value: address },
    {
      icon: 'person',
      label: copy.rows.manager,
      children: (
        <div className={styles.assignment}>
          <StatusBadge status="new" label={copy.badge} />
          <p className={cx('text-caption-l', styles.assignmentStatus)}>{showServerCleaner ? booking.cleaner : copy.status}</p>
        </div>
      ),
    },
  ];

  return (
    <ScreenLayout noPadding gap="none" className={styles.completeScreen}>
      <div className={styles.completeBody}>
        <div className={styles.completeStack}>
          {/* 머리글 설명: Figma '방문 일정과 주소를 확인해 주세요. 기사 배정 완료 시 알림으로 바로 알려드릴게요.' — 매칭에 실패했거나 취소됐으면 첫 문장만.
              아래 🔔 안내를 숨기는 것과 같은 이유로, 뒷문장이 남으면 숨긴 약속이 같은 화면에 그대로 보였다 (합친 38건 검토 R8·R16) */}
          <SuccessHeader title={copy.title} description={noAssignment ? copy.descriptionNoAssignment : copy.description} />
          <StackedInfoCard rows={rows} />
          {/* '기사 배정이 완료되면 알림으로 바로 알려드릴게요' — 매칭에 실패했거나 취소됐으면 숨긴다. 매칭이 멈췄거나 예약이 없어진 뒤라 배정을 기다리라는 말이
              사실과 다르고, 지금 상태(자동으로 다시 찾음 / 1:1 문의 / 배정 전 취소)는 위 기사 배정 행이 말한다 (코드에서 정함, 문제 목록 8번·합친 38건 검토 R16) */}
          {!noAssignment && <InfoNote glyph="🔔">{copy.note}</InfoNote>}
        </div>
        <div className={cx(styles.cta, styles.completeCta)}>
          {/* replace: 내 예약에서 뒤로 가기를 눌러도 이 완료 화면으로 돌아오지 않게 */}
          <Button variant="primary" className={styles.ctaButton} onClick={() => navigate('/reservations', { replace: true })}>
            {copy.cta}
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
