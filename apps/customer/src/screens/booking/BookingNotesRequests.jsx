// Figma: Booking/NotesRequests (64:200) · Office (2038:1113) · Appliance (2038:903) · MoveIn (2038:693) — 예전 구조(칩 카드 + 요청 카드 + 팁).
// 2026-11-01 사용자 첨부 '매니저님 방문 시 참고해야 할 정보를 선택해 주세요.' 시안으로 바꿨다 (Figma 에 없음):
//   반려동물(강아지·고양이·기타·없음) / 영유아(있음·없음) / 무료주차(가능·불가능·모름) 를 동그라미로 하나씩 고르고, 맨 아래 주의사항·요청사항 입력칸.
//   처음엔 아무것도 안 골라져 있고, 하나라도 안 고르면 "다음" 이 꺼지고 아래에 작은 빨간 안내가 보인다.
//   하단은 날짜/시간·옵션 화면과 같은 금액(표시용) + 자세히 + 다음.
// 예약 플로우 (/booking/:category/notes). 고른 답은 예약 초안 noticeAnswers 에 두고, "다음" 에서
//   - 서버 주의사항 칩 중 같은 뜻인 것(반려동물 있음 → pet, 영유아 있음 → baby — 그 카테고리 칩에 있을 때만)을 notices 로,
//   - 답 전체 한 줄을 noticeSummary 로 남긴다 → 예약을 만들 때 요청사항 맨 앞에 붙어 매니저에게 간다 (api/customer.js createBooking).
// 칩 목록·요청사항 칸 문구는 서버(GET /catalog/categories/{c}/notice-chips), 그 밖의 문구는 mock.js NOTES_SCREEN_COPY.
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, SectionDivider, CircleChoiceGroup, TextArea, PriceCtaBar, Sheet, Button, cx, useAsync, ScreenError, ScreenLoading, AnswerDogIcon, AnswerCatIcon, AnswerBirdIcon, AnswerBabyIcon, AnswerParkingIcon, AnswerNoParkingIcon, AnswerUnknownIcon } from '@ansimclean/ui';
import { getNoticeChips, saveBookingStep } from '../../api/customer.js';
import { INPUT_LIMITS, NOTES_SCREEN_COPY as COPY, OPTIONS_COPY, SCHEDULE_COPY, SCHEDULE_PRICE } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import { won, schedulePrice } from './bookingPrice.js';
import common from './booking.module.css';
import styles from './BookingNotesRequests.module.css';

// 선택지 key → 그림 (문항 key 별)
const ICONS = {
  pet: { dog: <AnswerDogIcon />, cat: <AnswerCatIcon />, other: <AnswerBirdIcon />, none: <AnswerCatIcon dashed /> },
  baby: { yes: <AnswerBabyIcon />, no: <AnswerBabyIcon dashed /> },
  parking: { free: <AnswerParkingIcon />, none: <AnswerNoParkingIcon />, unknown: <AnswerUnknownIcon /> },
};
const SUMMARY_LIMIT = 80; // 요청사항 앞에 붙는 답 한 줄 자리 — 서버 요청사항 한도(INPUT_LIMITS.request)에서 뺀다

/** 주의사항·요청사항 화면. 세 문항 동그라미 + 입력칸 + 금액과 "다음"(확인 화면으로) */
export default function BookingNotesRequests() {
  const navigate = useNavigate();
  const { category } = useParams();
  const [draft, update] = useBookingDraft(category);
  const { data, error, reload } = useAsync(() => getNoticeChips(category), [category]);
  const [detailOpen, setDetailOpen] = useState(false); // '자세히' 시트
  const answers = draft.noticeAnswers ?? {}; // { pet, baby, parking } — 처음엔 비어 있다
  const request = draft.request ?? '';

  const navBar = <NavBar onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  const missing = COPY.questions.filter((q) => !answers[q.key]);
  const hours = draft.durationHours ?? SCHEDULE_PRICE.standardHours;
  const basePrice = schedulePrice(hours, draft.frequency ?? 'recurring');
  const paidTotal = draft.paidOptionsTotal ?? 0;

  // "다음": 답을 서버 칩 key(notices)와 한 줄 요약(noticeSummary)으로 바꿔 초안에 두고 확인 화면으로
  const next = async () => {
    if (missing.length) return;
    const chipKeys = new Set((data.chips ?? []).map((c) => c.key));
    const notices = COPY.questions.filter((q) => q.chipKey && q.chipWhen.includes(answers[q.key]) && chipKeys.has(q.chipKey)).map((q) => q.chipKey);
    const noticeSummary =
      COPY.summaryPrefix +
      COPY.questions.map((q) => `${q.short ?? q.title}: ${q.options.find((o) => o.key === answers[q.key])?.label}`).join(' · ');
    await saveBookingStep(category, 'notes', { notices, noticeAnswers: answers, request });
    update({ notices, noticeSummary, request });
    navigate(`/booking/${category}/confirm`);
  };

  const [descBefore, descStrong, descAfter] = COPY.description;
  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <>
          {missing.length > 0 && (
            <p className={cx('text-body-xs', styles.missing)} role="status">
              {COPY.missing(missing.map((q) => q.short ?? q.title))}
            </p>
          )}
          <PriceCtaBar priceLabel={won(basePrice + paidTotal)} detailLabel={SCHEDULE_COPY.detail} onDetail={() => setDetailOpen(true)}>
            <Button variant="primary" chevron={false} className={common.ctaRounded} disabled={missing.length > 0} onClick={next}>
              다음
            </Button>
          </PriceCtaBar>
        </>
      }
    >
      <div className={styles.body}>
        <header className={cx(common.page, styles.header)}>
          <h1 className={cx('text-heading-m', common.pageTitle)}>{COPY.title}</h1>
          <p className={cx('text-body-default', styles.description)}>
            {descBefore}
            <strong className={styles.strong}>{descStrong}</strong>
            {descAfter}
          </p>
        </header>

        {COPY.questions.map((q) => (
          <div key={q.key}>
            <SectionDivider />
            <section className={cx(common.page, styles.question)} aria-labelledby={`notes-${q.key}`}>
              <div className={styles.questionHead}>
                <h2 id={`notes-${q.key}`} className={cx('text-heading-s', common.pageTitle)}>{q.title}</h2>
                <p className={cx('text-body-default', styles.hint)}>{q.description}</p>
              </div>
              <CircleChoiceGroup
                label={q.title}
                options={q.options.map((o) => ({ ...o, icon: ICONS[q.key][o.key] }))}
                value={answers[q.key]}
                onChange={(key) => update((prev) => ({ noticeAnswers: { ...(prev.noticeAnswers ?? {}), [q.key]: key } }))}
              />
            </section>
          </div>
        ))}

        <SectionDivider />
        <section className={cx(common.page, styles.question)} aria-labelledby="notes-request">
          <div className={styles.questionHead}>
            <h2 id="notes-request" className={cx('text-heading-s', common.pageTitle)}>{COPY.requestTitle}</h2>
            <p className={cx('text-body-default', styles.hint)}>{COPY.requestDescription}</p>
          </div>
          {/* 답 한 줄(최대 SUMMARY_LIMIT 자)이 앞에 붙어도 서버 한도(INPUT_LIMITS.request)를 넘지 않게 */}
          <TextArea ariaLabel={COPY.requestTitle} placeholder={COPY.requestPlaceholder} value={request} onChange={(e) => update({ request: e.target.value })} maxLength={INPUT_LIMITS.request - SUMMARY_LIMIT} className={styles.request} />
        </section>
      </div>

      {/* '자세히': 서비스 시간 금액 + 유료 옵션 합계 */}
      <Sheet open={detailOpen} onClose={() => setDetailOpen(false)} title={SCHEDULE_COPY.detailTitle}>
        <dl className={styles.detail}>
          <div className={styles.detailRow}>
            <dt className={cx('text-body-default', styles.hint)}>{`${SCHEDULE_COPY.duration} ${hours}시간`}</dt>
            <dd className={cx('text-body-bold', styles.detailValue)}>{won(basePrice)}</dd>
          </div>
          {paidTotal > 0 && (
            <div className={styles.detailRow}>
              <dt className={cx('text-body-default', styles.hint)}>{OPTIONS_COPY.paidDetail}</dt>
              <dd className={cx('text-body-bold', styles.detailValue)}>{`+${won(paidTotal)}`}</dd>
            </div>
          )}
        </dl>
        <p className={cx('text-body-s', styles.hint)}>{SCHEDULE_COPY.detailNote}</p>
        <Button variant="primary" chevron={false} className={common.ctaRounded} onClick={() => setDetailOpen(false)}>
          {SCHEDULE_COPY.confirm}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
