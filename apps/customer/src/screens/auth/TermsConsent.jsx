// Figma: Auth/TermsConsent (79:244)
// 프로토타입 연결 없음 → "다음" 은 /auth/profile-info (SignUp 에서 받은 ?method= 유지), 뒤로 = 이전 화면 (PROGRESS.md 판단 항목)
// ?next=complete 면 가입 완료 화면에서 동의 기록이 없어 되돌아온 것 — 동의 뒤 /auth/complete 로
// 고객 앱 가입 1단계(/auth/terms). 약관 목록은 서버(GET /terms?audience=customer)에서 받는다.
// 체크한 약관 id 는 서버로 바로 보내지 않고 가입 초안(localStorage)에만 둔다 — 서버에는 가입 완료 때 한꺼번에 간다.
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, BackButton, StepDots, Card, Checkbox, TermRow, NoteCard, Button, ASSETS, cx, useAsync, useAction, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getTerms, agreeTerms, getAgreedTermsDraft } from '../../api/customer.js';
import { PRIVACY_NOTE } from '../../api/mock.js';
import auth from './auth.module.css';
import styles from './TermsConsent.module.css';

/** 약관 동의 화면. 필수 약관을 모두 체크해야 "다음" 이 켜지고, 누르면 동의를 초안에 저장한 뒤 다음 가입 화면으로 간다 */
export default function TermsConsent() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const method = params.get('method') ?? 'phone'; // 가입 방법 (SignUp 화면이 붙여 보낸 값). 없으면 전화번호 가입
  const { data: terms, error, reload } = useAsync(() => getTerms(), []);
  const [agreed, setAgreed] = useState(() => new Set(getAgreedTermsDraft())); // '보기' 로 본문에 다녀와도 체크가 남는다
  const known = (set) => (terms ? [...set].filter((id) => terms.some((t) => t.id === id)) : [...set]); // 서버가 모르는 옛 초안 id 는 보내지 않는다
  // "다음": 체크 상태를 초안에 저장하고 다음 화면으로. 완료 화면에서 되돌아온 경우(?next=complete)는 완료 화면으로 돌아간다
  const next = useAction(async () => {
    await agreeTerms(known(agreed));
    navigate(params.get('next') === 'complete' ? '/auth/complete' : `/auth/profile-info?method=${method}`);
  });
  const persist = (set) => agreeTerms(known(set)).catch(() => {}); // 본문 보기로 나갔다 와도 유지되게 즉시 초안에

  if (error) return <ScreenError error={error} onRetry={reload} onBack={() => navigate(-1)} />;
  if (!terms) return <ScreenLoading />;

  // terms.length > 0 조건: 목록이 비었을 때 every() 가 true 가 되어 "전체 동의" 가 켜지거나 "다음" 이 열리지 않게 한다
  const allAgreed = terms.length > 0 && terms.every((t) => agreed.has(t.id));
  const requiredAgreed = terms.length > 0 && terms.filter((t) => t.required).every((t) => agreed.has(t.id));

  // 약관 한 줄 체크/해제. Set 을 새로 만들어 넘겨야 React 가 바뀐 것으로 보고 다시 그린다
  const toggle = (id, checked) => {
    const nextSet = new Set(agreed);
    if (checked) nextSet.add(id);
    else nextSet.delete(id);
    persist(nextSet);
    setAgreed(nextSet);
  };

  // "전체 동의하기": 켜면 목록의 약관 전부, 끄면 전부 해제
  const toggleAll = (checked) => {
    const nextSet = checked ? new Set(terms.map((t) => t.id)) : new Set();
    persist(nextSet);
    setAgreed(nextSet);
  };

  return (
    <ScreenLayout noPadding gap="none" className={auth.screen}>
      <BackButton className={auth.back} onClick={() => navigate(-1)} />
      <div className={cx(auth.body, styles.body)}>
        <div className={cx(auth.stack, styles.stack)}>
          <header className={auth.header}>
            <StepDots step={1} total={4} />
            <div className={auth.titleWrap}>
              <h1 className={cx('text-heading-l', auth.title)}>{'서비스 이용을 위한\n약관에 동의해주세요'}</h1>
            </div>
          </header>

          <div className={styles.cards}>
            <Card padding="none" gap="none" divided className={styles.agreement}>
              <Checkbox label="전체 동의하기" checked={allAgreed} onChange={toggleAll} bold className={styles.agreeAll} />
              <ul className={styles.termRows}>
                {terms.map((t) => (
                  <li key={t.id}>
                    <TermRow label={t.label} checked={agreed.has(t.id)} onChange={(c) => toggle(t.id, c)} onView={() => navigate(`/terms/${t.id}`)} />
                  </li>
                ))}
              </ul>
            </Card>
            <NoteCard icon={ASSETS.termsNoteIcon}>{PRIVACY_NOTE}</NoteCard>
          </div>
        </div>

        {/* 선택 약관은 체크하지 않아도 된다 — 필수 약관만 모두 켜지면 "다음" 이 열린다 */}
        <div className={auth.cta}>
          <Button variant="primary" disabled={!requiredAgreed} loading={next.loading} onClick={next.run}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
