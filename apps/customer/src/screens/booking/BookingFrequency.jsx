// Figma: Booking/Frequency (85:221) · Office/Frequency (2038:1015) · Appliance/Frequency (2038:805) · MoveIn/Frequency (2038:595)
// 4개는 ServiceLabel 문구와 프로모션 배너만 다르다 → /booking/:category/frequency 하나. 프로토타입 연결 없음 → "다음"·날짜 필드 탭 = /datetime (PROGRESS.md 판단 항목)
// 예약 플로우 1단계. 정기청소 / 1회 청소 중 하나를 골라 예약 초안(sessionStorage)에 둔다.
// 카테고리 라벨·배너 종류는 서버(GET /catalog/categories/{c}), 주기 설명 문구와 정기청소 회차 수(seriesTermMonths)는 confirm-copy 에서 받는다.
// 주기 목록 자체는 서버에 묻지 않는 앱 고정값이다 (getFrequencyOptions → mock.js FREQUENCY_OPTIONS).
// 2026-10-31 사용자 첨부 시안으로 바꿨다: 단계 점 대신 뒤로가기만('청소 범위' 버튼은 사용자 요청으로 뺐다), '기본 청소' 제목·설명, 카드형 라디오(브랜드 색), '정기 서비스는?' 질문 카드,
//   하단 고정 '다음'. 시안에 없는 회차 안내(InfoNote)·날짜/시간 선택 섹션·프로모션 배너는 다른 화면에서 쓸 예정이라 지우지 않고 hidden 으로 숨겼다.
import { Navigate, useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, RadioOption, SelectField, PromoBanner, InfoNote, FaqCard, SectionDivider, Button, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getBookingCategory, getFrequencyOptions, getConfirmCopy, saveBookingStep } from '../../api/customer.js';
import { RECURRING_TERM_NOTE, SERIES_TERM_MONTHS, FREQUENCY_SCREEN_COPY as COPY } from '../../api/mock.js';
import { useBookingDraft, formatDateTime } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingFrequency.module.css';

/** 서비스 주기 선택 화면. 제목·설명 + 주기 카드 라디오 + '정기 서비스는?' 질문 카드 + 하단 "다음" (날짜/시간 칸·프로모션 배너는 숨김) */
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


  const navBar = <NavBar onBack={() => navigate(-1)} />;

  const retry = () => { // 실패한 쪽을 모두 다시 — 확인 문구만 실패했을 때 '다시 시도' 가 아무것도 하지 않았다 (자체 점검 #35)
    if (error) reload();
    if (optionsQ.error) optionsQ.reload();
  };
  if (error || optionsQ.error) return <ScreenError error={error ?? optionsQ.error} onRetry={retry} navBar={navBar} />;
  if (info === null) return <Navigate to="/home" replace />; // 없는 카테고리(서버 404) — 스켈레톤이 영원히 남지 않게
  if (!info || optionsQ.loading) return <ScreenLoading navBar={navBar} />;

  const frequency = draft.frequency ?? 'recurring'; // Figma 기본 선택: 정기청소

  // "다음": 라디오를 한 번도 누르지 않았어도 지금 보이는 주기(기본값 포함)를 초안에 적고 주소 선택으로 (returnTo 가 있으면 그리로).
  // 주소 선택 화면은 저장한 주소가 없으면 바로 주소 검색으로 넘긴다 (2026-10-31 사용자 요청)
  const next = async () => {
    await saveBookingStep(category, 'frequency', { frequency });
    update({ frequency });
    navigate(back.next(`/booking/${category}/address`));
  };

  // 정기 카드 설명: '청연 VIP 라운지' 만 브랜드 색 밑줄(<em>, RadioOption 이 그린다)
  const [benefitBefore, benefitEm, benefitAfter] = COPY.recurringBenefit;
  const recurringDescription = <>{benefitBefore}<br /><em>{benefitEm}</em>{benefitAfter}</>;

  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <Button variant="primary" chevron={false} className={common.ctaRounded} onClick={next}>
          다음
        </Button>
      }
    >
      <div className={styles.body}>
        <header className={styles.titleBlock}>
          <h1 className={cx('text-heading-m', common.sectionTitle)}>{COPY.title}</h1>
          {COPY.description[category] && <p className={cx('text-body-default', styles.description)}>{COPY.description[category]}</p>}
        </header>

        <section className={styles.section}>
          <h2 className={cx('text-heading-s', common.sectionTitle)}>서비스 주기</h2>
          <div className={styles.options} role="radiogroup" aria-label="서비스 주기">
            {options.map((o) => (
              <RadioOption
                key={o.key}
                variant="card"
                label={COPY.labels[o.key] ?? o.label}
                description={o.key === 'recurring' ? recurringDescription : undefined}
                selected={frequency === o.key}
                onSelect={() => update({ frequency: o.key })}
              />
            ))}
          </div>
          {/* 시안에 없어 숨김 — 회차 안내 문구(서버 회차 수 반영)는 다른 화면에서 쓴다 */}
          <div hidden>
            <InfoNote glyph="🔁">{RECURRING_TERM_NOTE(termMonths)}</InfoNote>
            {/* 서버가 준 주기 설명 문구(confirm-copy) — 예전 라디오 설명 */}
            {options.map((o) => descriptions[o.key] && <p key={o.key}>{descriptions[o.key]}</p>)}
          </div>
        </section>

        {/* 시안에 없어 숨김 — 날짜/시간 선택 섹션은 다른 화면에서 처리한다 (2026-10-31 사용자 요청: 기능은 지우지 말 것) */}
        <div hidden>
          <section className={common.section}>
            <h2 className={cx('text-heading-m', common.sectionTitle)}>날짜/시간 선택</h2>
            {/* 입력칸처럼 보이는 버튼. 고른 일정이 있으면 "2026년 9월 16일 (수) 11:00" 꼴로, 누르면 날짜 화면으로 */}
            <SelectField placeholder="날짜/시간을 선택해주세요" value={formatDateTime(draft.date, draft.time)} icon="calendar" onClick={() => navigate(`/booking/${category}/datetime`)} />
          </section>
          {/* 서버가 준 promo 가 'recurring' 이면 정기청소 배너, 그 밖에는 혜택 알약만 있는 모양 */}
          {info.promo === 'recurring' ? <PromoBanner /> : <PromoBanner variant="benefits" />}
        </div>

        <SectionDivider className={styles.divider} />

        <section className={styles.faq} aria-labelledby="frequency-faq-title">
          <h2 id="frequency-faq-title" className={cx('text-heading-s', common.sectionTitle, styles.faqTitle)}>{COPY.faqTitle}</h2>
          <div className={styles.faqTrack}>
            {COPY.faqs.map((q) => (
              <FaqCard key={q} question={q} className={styles.faqCard} />
            ))}
          </div>
        </section>
      </div>
    </ScreenLayout>
  );
}
