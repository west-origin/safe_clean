// Figma: Booking/NotesRequests (64:200) · Office (2038:1113) · Appliance (2038:903) · MoveIn (2038:693) — 구조 동일, 주의사항 칩 문구·아이콘만 다름. 393×1149 (스크롤)
// 헤더 + NoticeCard(칩 9개) + RequestCard(요청사항 입력) + TipCard + "다음". OptionGrid (원본 이미지 · 참고용) 은 무시.
// 프로토타입 연결 없음 → "다음" = /confirm (PROGRESS.md 판단 항목)
// 예약 플로우 7단계(/booking/:category/notes). 주의사항 칩(여러 개 켜기)과 요청사항 글을 예약 초안(sessionStorage)에 둔다.
// 칩 목록·화면 문구는 서버(GET /catalog/categories/{c}/notice-chips)에서,
// 미리 켤 칩은 GET /customer/profile/suggested-notices 에서 받는다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, Card, NoticeChip, TextArea, TipCard, Button, ASSETS, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getNoticeChips, getSuggestedNotices, saveBookingStep } from '../../api/customer.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { INPUT_LIMITS } from '../../api/mock.js';
import styles from './BookingNotesRequests.module.css';

// 요청사항 글자 수 한도(아래 TextArea 의 maxLength = INPUT_LIMITS.request): 서버 CreateBookingRequest.request @Size(max = 1000) 과 같게

/** 주의사항·요청사항 화면. 헤더 + 주의사항 칩 카드 + 요청사항 입력 카드 + 팁 카드 + "다음"(확인 화면으로) */
export default function BookingNotesRequests() {
  const navigate = useNavigate();
  const { category } = useParams();
  const [draft, update] = useBookingDraft(category);
  // 가입 때 '반려동물 있음' 을 골랐으면 그 칩을 미리 켜 둔다 — 같은 것을 두 번 묻지 않게 (사용성 검토 2026-09-22).
  // 이 화면에 처음 들어왔을 때만(초안에 아직 주의사항 기록이 없을 때) 적용하고, 고객이 끄면 그대로 둔다.
  const { data, error, reload } = useAsync(async () => {
    const chips = await getNoticeChips(category);
    const suggested = await getSuggestedNotices(); // 실패해도 던지지 않고 빈 목록을 준다 (customer.js)
    return { ...chips, suggested };
  }, [category]);
  // 지금 켜진 칩. 초안에 notices 가 아직 없으면(undefined) 추천 중 이 카테고리 칩에 있는 것만 켠다.
  // ?? 는 null·undefined 일 때만 다음 값으로 넘어가므로, 고객이 다 꺼서 생긴 빈 배열 [] 은 추천으로 되돌리지 않는다
  const notices = draft.notices ?? data?.suggested?.filter((key) => data.chips.some((c) => c.key === key)) ?? [];
  const request = draft.request ?? '';


  if (error) return <ScreenError error={error} onRetry={reload} navBar={<StepNavBar step={7} onBack={() => navigate(-1)} />} />;
  if (!data) return <ScreenLoading navBar={<StepNavBar step={7} onBack={() => navigate(-1)} />} />;

  // 칩 하나 켜기/끄기. update 에 함수를 넘겨 초안의 최신 값(prev)을 기준으로 바꾼다
  const toggle = (key) =>
    update((prev) => {
      const list = prev.notices ?? notices; // 아직 기록이 없으면 미리 켜 둔 것에서 시작한다
      return { notices: list.includes(key) ? list.filter((k) => k !== key) : [...list, key] };
    });

  // "다음": 지금 보이는 칩·요청사항을 초안에 확정하고 확인 화면으로
  const next = async () => {
    await saveBookingStep(category, 'notes', { notices, request });
    update({ notices, request }); // 미리 켜 둔 칩(반려동물)을 한 번도 안 건드려도 초안에 남긴다 — 안 그러면 확인·예약에서 빠졌다 (자체 점검 #14)
    navigate(`/booking/${category}/confirm`);
  };

  const { copy } = data; // 화면 문구 묶음(제목·카드 제목·안내·팁). 없으면 아래 본문을 그리지 않는다
  return (
    <ScreenLayout noPadding gap="none" navBar={<StepNavBar step={7} onBack={() => navigate(-1)} />}>
      {copy && (
        <div className={cx(common.body, styles.body)}>
          <div className={cx(common.stack, styles.stack)}>
            <header className={styles.header}>
              <h1 className={cx('text-heading-m', common.sectionTitle)}>{copy.title}</h1>
              <p className={cx('text-body-s', styles.description)}>{copy.description}</p>
            </header>

            <Card padding="none" gap="none" className={styles.noticeCard}>
              <div className={styles.noticeHeader}>
                <h2 className={cx('text-body-bold', styles.cardTitle)}>{copy.noticeTitle}</h2>
                <p className={cx('text-caption-l', styles.cardDescription)}>{copy.noticeDescription}</p>
              </div>
              <div className={styles.chipGrid} role="group" aria-label={copy.noticeTitle}>
                {data.chips.map((c) => (
                  <NoticeChip key={c.key} label={c.label} icon={c.icon} selected={notices.includes(c.key)} onToggle={() => toggle(c.key)} />
                ))}
              </div>
            </Card>

            <Card padding="none" gap="none" className={styles.requestCard}>
              <div className={styles.requestHeader}>
                <h2 className={cx('text-body-bold', styles.cardTitle)}>{copy.requestTitle}</h2>
                <p className={cx('text-caption-l', styles.cardDescription)}>{copy.requestDescription}</p>
              </div>
              {/* maxLength = INPUT_LIMITS.request (서버도 요청사항을 1000자로 제한한다) */}
              <TextArea ariaLabel={copy.requestTitle} placeholder={copy.requestPlaceholder} value={request} onChange={(e) => update({ request: e.target.value })} maxLength={INPUT_LIMITS.request} />
            </Card>

            <TipCard icon={ASSETS.requestTipIcon} title={copy.tipTitle}>
              {copy.tipBody}
            </TipCard>
          </div>

          <div className={common.cta}>
            <Button variant="primary" onClick={next}>
              다음
            </Button>
          </div>
        </div>
      )}
    </ScreenLayout>
  );
}
