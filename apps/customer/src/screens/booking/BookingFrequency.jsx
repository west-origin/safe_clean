// Figma: Booking/Frequency (85:221) · Office/Frequency (2038:1015) · Appliance/Frequency (2038:805) · MoveIn/Frequency (2038:595)
// 4개는 ServiceLabel 문구와 프로모션 배너만 다르다 → /booking/:category/frequency 하나. 프로토타입 연결 없음 → "다음"·날짜 필드 탭 = /datetime (PROGRESS.md 판단 항목)
// 예약 플로우 1단계. 정기청소 / 1회 청소 중 하나를 골라 예약 초안(sessionStorage)에 둔다.
// 카테고리 라벨·배너 종류는 서버(GET /catalog/categories/{c}), 주기 설명 문구와 정기청소 회차 수(seriesTermMonths)는 confirm-copy 에서 받는다.
// 주기 목록 자체는 서버에 묻지 않는 앱 고정값이다 (getFrequencyOptions → mock.js FREQUENCY_OPTIONS).
import { Navigate, useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, RadioOption, SelectField, PromoBanner, InfoNote, Button, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getBookingCategory, getFrequencyOptions, getConfirmCopy, saveBookingStep } from '../../api/customer.js';
import { RECURRING_TERM_NOTE, SERIES_TERM_MONTHS } from '../../api/mock.js';
import { useBookingDraft, formatDateTime } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingFrequency.module.css';

/** 서비스 주기 선택 화면. 주기 라디오 + 날짜/시간 칸(누르면 날짜 화면) + 프로모션 배너 + "다음" */
export default function BookingFrequency() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 확인 화면 '수정하기'(?returnTo=)에서 왔으면 "다음" 이 그 화면으로 돌아간다
  const [draft, update] = useBookingDraft(category);
  // 카테고리 정보 { label, promo }. 모르는 카테고리면 getBookingCategory 가 null 을 준다 (아래 Navigate 참고)
  const { data: info, error, reload } = useAsync(() => getBookingCategory(category), [category]);
  // 주기를 고르는 자리에서 그게 무슨 뜻인지(매월 같은 날짜에 한 번 …) 알려 준다 — 예전에는 확인 화면에서야 나왔다 (사용성 검토 2026-09-22)
  const optionsQ = useAsync(async () => {
    const [list, copy] = await Promise.all([getFrequencyOptions(), getConfirmCopy(category)]);
    // 회차 수 = 서버 app_settings.series_term_months (없으면 기본 6) — 운영자가 바꾸면 안내도 따라간다 (문제 목록 25번)
    return { list, descriptions: copy.frequencyDescriptions ?? {}, termMonths: copy.seriesTermMonths ?? SERIES_TERM_MONTHS };
  }, [category]);
  const options = optionsQ.data?.list ?? [];
  const descriptions = optionsQ.data?.descriptions ?? {};
  const termMonths = optionsQ.data?.termMonths ?? SERIES_TERM_MONTHS;


  const retry = () => { // 실패한 쪽을 모두 다시 — 확인 문구만 실패했을 때 '다시 시도' 가 아무것도 하지 않았다 (자체 점검 #35)
    if (error) reload();
    if (optionsQ.error) optionsQ.reload();
  };
  if (error || optionsQ.error) return <ScreenError error={error ?? optionsQ.error} onRetry={retry} navBar={<StepNavBar step={1} onBack={() => navigate(-1)} />} />;
  if (info === null) return <Navigate to="/home" replace />; // 없는 카테고리(서버 404) — 스켈레톤이 영원히 남지 않게
  if (!info || optionsQ.loading) return <ScreenLoading navBar={<StepNavBar step={1} onBack={() => navigate(-1)} />} />;

  const frequency = draft.frequency ?? 'recurring'; // Figma 기본 선택: 정기청소

  // "다음": 라디오를 한 번도 누르지 않았어도 지금 보이는 주기(기본값 포함)를 초안에 적고 날짜 화면으로 (returnTo 가 있으면 그리로)
  const next = async () => {
    await saveBookingStep(category, 'frequency', { frequency });
    update({ frequency });
    navigate(back.next(`/booking/${category}/datetime`));
  };

  return (
    <ScreenLayout noPadding gap="none" navBar={<StepNavBar step={1} onBack={() => navigate(-1)} />}>
      <div className={cx(common.body, styles.body)}>
        <div className={cx(common.stack, styles.stack)}>
          <section className={common.section}>
            <div className={styles.titleBlock}>
              <span className={cx('text-title-xs', styles.serviceLabel)}>{info?.label}</span>
              <h1 className={cx('text-heading-m', common.sectionTitle)}>서비스 주기</h1>
            </div>
            <div className={common.options} role="radiogroup" aria-label="서비스 주기">
              {options.map((o) => (
                <RadioOption key={o.key} label={o.label} description={descriptions[o.key]} selected={frequency === o.key} onSelect={() => update({ frequency: o.key })} />
              ))}
            </div>
            {/* 몇 회·얼마나 묶이는지 고르기 전에 알려 준다 (사용성 검토 2026-09-22) */}
            <InfoNote glyph="🔁">{RECURRING_TERM_NOTE(termMonths)}</InfoNote>
          </section>

          <section className={common.section}>
            <h2 className={cx('text-heading-m', common.sectionTitle)}>날짜/시간 선택</h2>
            {/* 입력칸처럼 보이는 버튼. 고른 일정이 있으면 "2026년 9월 16일 (수) 11:00" 꼴로, 누르면 날짜 화면으로 */}
            <SelectField placeholder="날짜/시간을 선택해주세요" value={formatDateTime(draft.date, draft.time)} icon="calendar" onClick={() => navigate(`/booking/${category}/datetime`)} />
          </section>

          {/* 서버가 준 promo 가 'recurring' 이면 정기청소 배너, 그 밖에는 혜택 알약만 있는 모양 */}
          {info && (info.promo === 'recurring' ? <PromoBanner /> : <PromoBanner variant="benefits" />)}
        </div>

        <div className={common.cta}>
          <Button variant="primary" onClick={next}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
