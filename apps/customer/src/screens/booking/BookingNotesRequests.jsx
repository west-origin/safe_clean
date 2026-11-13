// Figma: Booking/NotesRequests (64:200) · Office (2038:1113) · Appliance (2038:903) · MoveIn (2038:693) — 예전 구조(칩 카드 + 요청 카드 + 팁).
// 2026-11-01 사용자 첨부 '매니저님 방문 시 참고해야 할 정보를 선택해 주세요.' 시안으로 바꿨다 (Figma 에 없음):
//   반려동물(있음·없음) / 영유아(있음·없음) / 무료주차(가능·불가능·모름) 를 카드로 하나씩 고르고, 맨 아래 주의사항·요청사항 입력칸.
//   2026-11-03 사용자 요청: 카드형(첨부 시안) + 매장 아이콘 같은 플랫 컬러 아이콘('없음'·'불가능'은 같은 그림의 검정 선만),
//   (같은 날 다시) 아이콘은 홈 매장·가정·원룸 아이콘과 같은 플랫 형식 + 연회색 원. 없음은 회색 면, 무료주차 불가능·공동현관 없음은 금지 표시.
//   공동현관 비밀번호(있음·없음 → 있음이면 입력칸)·현관 비밀번호(처음부터 입력칸) 두 칸 추가, 요청사항 칸 예시 삭제.
//   처음엔 아무것도 안 골라져 있고, 하나라도 안 고르거나 필요한 비밀번호가 비어 있으면 "다음" 이 꺼지고 아래에 작은 빨간 안내가 보인다.
//   비밀번호는 초안(doorCodes)에만 두고 서버로 보내지 않는다 — 요청사항은 수락 전 콜 상세에도 보여서 여러 매니저에게 노출된다.
//   배정된 매니저에게만 전달하려면 서버에 따로 칸이 필요하다 (PROGRESS.md 판단 항목).
//   하단은 날짜/시간·옵션 화면과 같은 BookingPriceFooter('총 결제금액' + 금액(표시용) + 다음, 2026-11-03).
// 예약 플로우 (/booking/:category/notes). 고른 답은 예약 초안 noticeAnswers 에 두고, "다음" 에서
//   - 서버 주의사항 칩 중 같은 뜻인 것(반려동물 있음 → pet, 영유아 있음 → baby — 그 카테고리 칩에 있을 때만)을 notices 로,
//   - 답 전체 한 줄을 noticeSummary 로 남긴다 → 예약을 만들 때 요청사항 맨 앞에 붙어 매니저에게 간다 (api/customer.js createBooking).
// 칩 목록·요청사항 칸 문구는 서버(GET /catalog/categories/{c}/notice-chips), 그 밖의 문구는 mock.js NOTES_SCREEN_COPY.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, SectionDivider, ChoiceCardGroup, TextField, TextArea, cx, useAsync, ScreenError, ScreenLoading, AnswerDogIcon, AnswerBabyIcon, AnswerParkingIcon, AnswerUnknownIcon, AnswerDoorIcon } from '@ansimclean/ui';
import { getNoticeChips, saveBookingStep } from '../../api/customer.js';
import { INPUT_LIMITS, NOTES_SCREEN_COPY as COPY, SCHEDULE_PRICE } from '../../api/mock.js';
import BookingPriceFooter from './BookingPriceFooter.jsx';
import { useBookingDraft } from './bookingDraft.js';
import { displayPrice } from './bookingPrice.js';
import common from './booking.module.css';
import styles from './BookingNotesRequests.module.css';

// 선택지 key → 그림 (문항 key 별)
const ICONS = {
  pet: { yes: <AnswerDogIcon />, no: <AnswerDogIcon muted /> },
  baby: { yes: <AnswerBabyIcon />, no: <AnswerBabyIcon muted /> },
  parking: { free: <AnswerParkingIcon />, none: <AnswerParkingIcon prohibited />, unknown: <AnswerUnknownIcon /> },
  buildingDoor: { yes: <AnswerDoorIcon />, no: <AnswerDoorIcon prohibited /> },
};
const DOOR_CODE_LIMIT = 30; // 비밀번호 칸 글자 수 상한 (코드에서 정함 — '*1234#' 꼴이면 충분)
const SUMMARY_LIMIT = 80; // 요청사항 앞에 붙는 답 한 줄 자리 — 서버 요청사항 한도(INPUT_LIMITS.request)에서 뺀다

/** 주의사항·요청사항 화면. 세 문항 카드 + 비밀번호 두 칸 + 요청사항 입력칸 + 금액과 "다음"(확인 화면으로) */
export default function BookingNotesRequests() {
  const navigate = useNavigate();
  const { category } = useParams();
  const [draft, update] = useBookingDraft(category);
  const { data, error, reload } = useAsync(() => getNoticeChips(category), [category]);
  const answers = draft.noticeAnswers ?? {}; // { pet, baby, parking } — 처음엔 비어 있다
  const request = draft.request ?? '';
  const doorCodes = draft.doorCodes ?? {}; // { building: 'yes'|'no', buildingCode, frontCode } — 처음엔 비어 있다
  const setDoorCodes = (patch) => update((prev) => ({ doorCodes: { ...(prev.doorCodes ?? {}), ...patch } }));

  const navBar = <NavBar onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  // 안 고른 문항 — 예전 초안의 답(예: 반려동물 'dog')은 지금 선택지에 없으니 안 고른 것으로 본다
  const missing = COPY.questions.filter((q) => !q.options.some((o) => o.key === answers[q.key]));
  if (!doorCodes.building) missing.push({ title: COPY.buildingDoor.title });
  // 비어 있는 비밀번호 칸 — 공동현관은 '있음' 일 때만
  const missingCodes = [];
  if (doorCodes.building === 'yes' && !doorCodes.buildingCode?.trim()) missingCodes.push(COPY.buildingDoor.title);
  if (!doorCodes.frontCode?.trim()) missingCodes.push(COPY.frontDoor.title);
  const blocked = missing.length > 0 || missingCodes.length > 0;
  const hours = draft.durationHours ?? SCHEDULE_PRICE.standardHours;
  const paidTotal = draft.paidOptionsTotal ?? 0; // 옵션 화면이 남긴 유료 옵션 1회분 합

  // "다음": 답을 서버 칩 key(notices)와 한 줄 요약(noticeSummary)으로 바꿔 초안에 두고 확인 화면으로
  const next = async () => {
    if (blocked) return;
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
        <BookingPriceFooter price={displayPrice(category, draft, hours, paidTotal)} disabled={blocked} onNext={next}>
          {blocked && (
            <p className={cx('text-body-xs', styles.missing)} role="status">
              {missing.length > 0 ? COPY.missing(missing.map((q) => q.short ?? q.title)) : COPY.missing(missingCodes, '입력해')}
            </p>
          )}
        </BookingPriceFooter>
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
              <ChoiceCardGroup
                label={q.title}
                options={q.options.map((o) => ({ ...o, icon: ICONS[q.key][o.key] }))}
                value={answers[q.key]}
                onChange={(key) => update((prev) => ({ noticeAnswers: { ...(prev.noticeAnswers ?? {}), [q.key]: key } }))}
              />
            </section>
          </div>
        ))}

        <SectionDivider />
        <section className={cx(common.page, styles.question)} aria-labelledby="notes-building-door">
          <div className={styles.questionHead}>
            <h2 id="notes-building-door" className={cx('text-heading-s', common.pageTitle)}>{COPY.buildingDoor.title}</h2>
            <p className={cx('text-body-default', styles.hint)}>{COPY.buildingDoor.description}</p>
          </div>
          <ChoiceCardGroup label={COPY.buildingDoor.title} options={COPY.buildingDoor.options.map((o) => ({ ...o, icon: ICONS.buildingDoor[o.key] }))} value={doorCodes.building} onChange={(key) => setDoorCodes({ building: key })} />
          {doorCodes.building === 'yes' && (
            <TextField label={COPY.buildingDoor.title} hideLabel placeholder={COPY.buildingDoor.placeholder} value={doorCodes.buildingCode ?? ''} onChange={(e) => setDoorCodes({ buildingCode: e.target.value })} maxLength={DOOR_CODE_LIMIT} autoComplete="off" />
          )}
        </section>

        <SectionDivider />
        <section className={cx(common.page, styles.question)} aria-labelledby="notes-front-door">
          <div className={styles.questionHead}>
            <h2 id="notes-front-door" className={cx('text-heading-s', common.pageTitle)}>{COPY.frontDoor.title}</h2>
            <p className={cx('text-body-default', styles.hint)}>{COPY.frontDoor.description}</p>
          </div>
          <TextField label={COPY.frontDoor.title} hideLabel placeholder={COPY.frontDoor.placeholder} value={doorCodes.frontCode ?? ''} onChange={(e) => setDoorCodes({ frontCode: e.target.value })} maxLength={DOOR_CODE_LIMIT} autoComplete="off" required />
        </section>

        <SectionDivider />
        <section className={cx(common.page, styles.question)} aria-labelledby="notes-request">
          <div className={styles.questionHead}>
            <h2 id="notes-request" className={cx('text-heading-s', common.pageTitle)}>{COPY.requestTitle}</h2>
            <p className={cx('text-body-default', styles.hint)}>{COPY.requestDescription}</p>
          </div>
          {/* 답 한 줄(최대 SUMMARY_LIMIT 자)이 앞에 붙어도 서버 한도(INPUT_LIMITS.request)를 넘지 않게 */}
          <TextArea ariaLabel={COPY.requestTitle} value={request} onChange={(e) => update({ request: e.target.value })} maxLength={INPUT_LIMITS.request - SUMMARY_LIMIT} className={styles.request} />
        </section>
      </div>

    </ScreenLayout>
  );
}
