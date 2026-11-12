// 예약 확인: 서비스 정보·방문 장소와 일정·옵션·주의사항·요금, 하단 예약 확정 버튼.
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, Sheet, Chip, Button, fromDateKey, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getBookingCategory, getFrequencyOptions, getBookingOptions, getNoticeChips, getConfirmCopy, getSavedAddress, getBookingQuote, createBooking, newSubmitKey } from '../../api/customer.js';
import { QUOTE_COPY, CONFIRM_EMPTY_COPY, CONFIRM_COPY } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingConfirm.module.css';

// 옵션 라벨의 줄바꿈(앞뒤 공백 포함)을 공백 하나로 바꿔 칩 안에서 한 줄로 보이게 한다
const oneLine = (label) => label.replace(/\s*\n\s*/g, ' ').trim();

/** 예약 최종 확인 화면. 항목마다 "수정하기" 로 그 단계에 갔다가 돌아오고, 일정·주소·견적이 모두 있어야 "완료" 가 열린다 */
export default function BookingConfirm() {
  const navigate = useNavigate();
  const [editVisitOpen, setEditVisitOpen] = useState(false);
  const { category } = useParams();
  const back = useReturnTo(); // 여기서는 '수정하기' 주소(그 단계 + ?returnTo=이 화면)를 만드는 withReturn 만 쓴다
  const [draft, update, clear] = useBookingDraft(category);
  const useNewAddress = draft.reuseAddress === 'no'; // Step2 에서 '기존 주소 사용' 에 아니요를 골랐다
  // 화면에 필요한 목록·문구를 한꺼번에 받는다. 하나라도 실패하면 오류 화면 (기존 주소가 없는 404 만은 null 로 받아 화면을 연다)
  const { data: info, error, reload } = useAsync(
    () =>
      Promise.all([
        getBookingCategory(category),
        getFrequencyOptions(),
        getBookingOptions(category),
        getNoticeChips(category),
        getConfirmCopy(category),
        // 새 주소를 쓰면 기존 주소는 필요 없다. 기존 주소가 없으면(404 → null) 화면은 열리고 "주소를 입력해주세요" 로 안내한다. 조회 실패(네트워크 등)는 오류 화면 → 다시 시도
        useNewAddress ? Promise.resolve(null) : getSavedAddress(),
      ]).then(([cat, frequencies, options, notices, confirm, savedAddress]) => ({ cat, frequencies, options, chips: notices.chips, ...confirm, savedAddress })),
    [category, useNewAddress],
  );
  // 요금은 일정·옵션·주기가 정해진 뒤 서버 견적으로. 초안이 바뀌면 다시 받는다
  // (다시 받는 조건은 아래 deps 의 카테고리·주기·날짜·시간·옵션뿐이다. 옵션 배열은 문자열 하나로 합쳐 비교한다)
  const optionKeys = [...(draft.freeOptions ?? []), ...(draft.paidOptions ?? [])].join(',');
  const quoteQ = useAsync(() => getBookingQuote(category, draft), [category, draft.frequency, draft.date, draft.time, optionKeys]);
  // 주소: "아니요" 면 새 주소만, "네" 면 기존 주소만. 둘 다 없으면 null → 완료 불가 (info 가 오기 전에는 null)
  const address = useNewAddress ? (draft.roadAddress ? { roadAddress: draft.roadAddress, detailAddress: draft.detailAddress } : null) : (info?.savedAddress ?? null);
  const hasSchedule = Boolean(draft.date && draft.time);
  const quote = hasSchedule ? quoteQ.data : null;
  const canComplete = Boolean(address?.roadAddress?.trim() && hasSchedule && quote); // 상세주소는 비워도 된다(단독주택, 주소 확인 화면에서 한 번 묻는다)
  // "완료": 예약 생성. 멱등 키(submitKey)를 초안에 먼저 저장해 두어, 실패 뒤 다시 눌러도 같은 키가 가고 예약이 두 번 생기지 않는다.
  // 초안 내용을 바꾸면 useBookingDraft 가 submitKey 를 지워 다음 "완료" 때 새 키를 만든다 (bookingDraft.js).
  // 주소는 위에서 고른 address(새 주소 또는 기존 주소)로 초안 값을 덮어 보낸다
  const submit = useAction(async () => {
    const submitKey = draft.submitKey ?? newSubmitKey();
    if (!draft.submitKey) update({ submitKey });
    const booking = await createBooking(category, { ...draft, ...address, submitKey });
    clear(); // 성공한 뒤에만 초안을 지운다 — 실패하면 입력이 그대로 남는다
    // replace: 방문 기록의 이 화면 자리를 완료 화면으로 바꾼다 (파일 머리 설명)
    navigate(`/booking/${category}/complete?id=${booking.id}`, { replace: true });
  });


  if (error) return <ScreenError error={error} onRetry={reload} navBar={<NavBar onBack={() => navigate(-1)} />} />;
  if (!info) return <ScreenLoading navBar={<NavBar onBack={() => navigate(-1)} />} />;

  const frequency = draft.frequency ?? 'recurring'; // 주기가 없으면 정기청소 (customer.js bookingBody 도 같은 기본값)
  // 초안에는 key 만 있으므로, 서버 목록에서 고른 key 의 항목(라벨)을 찾아 칩으로 그린다
  const chosenOptions = [...info.options.free.filter((o) => (draft.freeOptions ?? []).includes(o.key)), ...info.options.paid.filter((o) => (draft.paidOptions ?? []).includes(o.key))];
  const chosenNotices = info.chips.filter((c) => (draft.notices ?? []).includes(c.key));
  const go = (step) => navigate(back.withReturn(`/booking/${category}/${step}`, `/booking/${category}/confirm`)); // 한 단계만 고치고 돌아온다

  const serviceLabel = category === 'home' && draft.homeService ? (draft.homeService === 'studio' ? '원룸 청소' : '가정 청소') : info.cat?.label;
  const frequencyLabel = info.frequencies.find((f) => f.key === frequency)?.label;
  const date = fromDateKey(draft.date);
  const visitDate = date ? `${date.getFullYear()}. ${String(date.getMonth() + 1).padStart(2, '0')}. ${String(date.getDate()).padStart(2, '0')}. (${['일', '월', '화', '수', '목', '금', '토'][date.getDay()]})` : '날짜와 시간을 선택해주세요';
  const timeLabel = (time) => {
    if (!time) return '';
    const [hour, minute] = time.split(':').map(Number);
    return `${hour < 12 ? '오전' : '오후'} ${hour % 12 || 12}:${String(minute).padStart(2, '0')}`;
  };
  const visitTime = hasSchedule ? [draft.time, draft.endTime].filter(Boolean).map(timeLabel).join(' ~ ') : '시간을 선택해주세요';


  return (
    <ScreenLayout noPadding gap="none" navBar={<NavBar onBack={() => navigate(-1)} />}
      stickyCta={<>
        <Button variant="primary" chevron={false} className={common.ctaRounded} disabled={!canComplete} loading={submit.loading} onClick={submit.run}>예약 확정하기</Button>
        <ActionError error={submit.error} fallback="예약을 완료하지 못했어요. 다시 시도해주세요." className={styles.error} />
      </>}
    >
      <div className={styles.body}>
        <header className={styles.header}>
          <h1 className={cx('text-heading-m', styles.title)}>{CONFIRM_COPY.title}</h1>
          <p className={cx('text-body-s', styles.description)}>{CONFIRM_COPY.description}</p>
        </header>
        <ConfirmSection title="서비스 정보" onEdit={() => go('frequency')}>
          <dl className={styles.rows}>
            <ConfirmRow label="서비스 종류"><span className={styles.badge}>{serviceLabel}</span></ConfirmRow>
            <ConfirmRow label="청소 유형">{frequencyLabel}</ConfirmRow>
            <ConfirmRow label="서비스 주기">{info.frequencyDescriptions[frequency] ?? frequencyLabel}</ConfirmRow>
          </dl>
        </ConfirmSection>
        <ConfirmSection title="방문 장소 및 일정" onEdit={() => setEditVisitOpen(true)}>
          <dl className={styles.rows}>
            <ConfirmRow label="방문 주소">
              {address?.roadAddress ?? '주소를 입력해주세요'}
              {address?.detailAddress && <span className={cx('text-body-s', styles.detail)}>{address.detailAddress}</span>}
            </ConfirmRow>
            <ConfirmRow label="예약 방식"><span className={styles.badge}>{frequency === 'recurring' ? '정기예약' : '1회예약'}</span></ConfirmRow>
            <ConfirmRow label={frequency === 'recurring' ? '첫 방문' : '방문 날짜'}>{visitDate}</ConfirmRow>
            <ConfirmRow label="방문 시간">{visitTime}</ConfirmRow>
          </dl>
          {frequency === 'recurring' && <p className={cx('text-body-bold', styles.repeat)}>↻ {info.frequencyDescriptions[frequency] ?? frequencyLabel}</p>}
        </ConfirmSection>
        <ConfirmSection title="선택 옵션" onEdit={() => go('options')}>
          {chosenOptions.length ? <div className={styles.chips}>{chosenOptions.map((o) => <Chip key={o.key}>{oneLine(o.label)}</Chip>)}</div> : <p className="text-body-default">{CONFIRM_EMPTY_COPY.options}</p>}
        </ConfirmSection>
        <ConfirmSection title="주의사항" onEdit={() => go('notes')}>
          {draft.noticeSummary ? <p className="text-body-default">{draft.noticeSummary.replace(/^\[[^\]]*\]\s*/, '')}</p> : chosenNotices.length ? <div className={styles.chips}>{chosenNotices.map((c) => <Chip key={c.key}>{c.label}</Chip>)}</div> : <p className="text-body-default">{CONFIRM_EMPTY_COPY.notices}</p>}
        </ConfirmSection>
        <ConfirmSection title="요청사항" onEdit={() => go('notes')}>
          <p className={cx('text-body-default', styles.request)}>{draft.request?.trim() || CONFIRM_EMPTY_COPY.request}</p>
        </ConfirmSection>
        {hasSchedule && <ConfirmSection noDivider title={QUOTE_COPY.title} onEdit={!quote && quoteQ.error && ![400, 409].includes(quoteQ.error.status) ? quoteQ.reload : undefined}>
          <ActionError error={quoteQ.error} fallback={QUOTE_COPY.failed} />
          {quote && <>
            <dl className={styles.priceRows}>
              <div className={styles.priceRow}><dt>총 결제금액</dt><dd>{quote.perVisit.label}</dd></div>
              {quote.perVisit.optionsLabel && <div className={styles.priceRow}><dt>{QUOTE_COPY.optionsLabel}</dt><dd>{quote.perVisit.optionsLabel}</dd></div>}
              {quote.savingNote && <div className={styles.priceRow}><dt>{QUOTE_COPY.savingLabel}</dt><dd className={styles.saving}>{quote.savingNote}</dd></div>}
            </dl>
            {frequency === 'recurring' && <div className={styles.chips}>{quote.visits.map((v) => <Chip key={v.date}>{v.label}</Chip>)}</div>}
          </>}
        </ConfirmSection>}
      </div>
      <Sheet open={editVisitOpen} onClose={() => setEditVisitOpen(false)} title="방문 장소 및 일정 수정">
        <Button variant="secondary" chevron={false} onClick={() => go('address')}>주소 수정하기</Button>
        <Button variant="secondary" chevron={false} onClick={() => go('schedule')}>일정 수정하기</Button>
      </Sheet>
    </ScreenLayout>
  );
}

function ConfirmSection({ title, onEdit, children, noDivider = false }) {
  return <section className={cx(styles.section, noDivider && styles.noDivider)}>
    <div className={styles.sectionHead}>
      <h2 className="text-heading-s">{title}</h2>
      {onEdit && <button type="button" className={cx('text-body-s', styles.edit)} aria-label={`${title} 수정하기`} onClick={onEdit}>수정</button>}
    </div>
    {children}
  </section>;
}
function ConfirmRow({ label, children }) {
  return <div className={styles.row}><dt className="text-body-s">{label}</dt><dd className="text-body-bold">{children}</dd></div>;
}
