// Manager/Auth/SignUp/Terms (코드에서 정함) — 회원가입 1/3 약관 동의. 고객용 Auth/TermsConsent 와 같은 프리미티브(Checkbox·TermRow·NoteCard·StepDots)를 매니저 스타일(NavBar + StickyCTA)로.
// 필수 약관을 모두 켜야 "다음" → /auth/signup/info. "보기>" → /auth/terms/:id. 뒤로 → /auth/login
// ?next=complete 면 가입 완료 화면에서 동의 기록이 없어 되돌아온 것 — 동의 뒤 /auth/signup/complete 로
// 체크 상태는 바꿀 때마다 가입 초안(localStorage)에 저장하고, 서버에는 가입 완료(SignUpComplete) 때 한꺼번에 보낸다.
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, NavBar, StepDots, Card, Checkbox, TermRow, NoteCard, Button, ASSETS, ScreenError, ScreenLoading, useAsync, useAction, cx } from '@ansimclean/ui';
import { getAuthCopy, getManagerTerms, agreeManagerTerms, getAgreedTermsDraft } from '../../api/manager.js';
import styles from './auth.module.css';

/**
 * 회원가입 1단계 화면 (/auth/signup). 약관 목록과 아래 안내 문구는 서버(GET /terms?audience=manager),
 * 제목·버튼 문구는 AUTH_COPY.terms 에서 온다.
 */
export default function SignUpTerms() {
  const navigate = useNavigate();
  const [params] = useSearchParams(); // 주소의 ?쿼리 (next=complete 확인용)
  // 화면 문구와 서버의 약관 목록을 한 번에 받아 { copy, terms, note } 한 객체로 합친다
  const { data, error, reload } = useAsync(async () => ({ copy: await getAuthCopy('terms'), ...(await getManagerTerms()) }), []);
  const [agreed, setAgreed] = useState(() => new Set(getAgreedTermsDraft())); // '보기>' 로 본문에 다녀와도 체크가 남는다
  const known = (set) => (data ? [...set].filter((id) => data.terms.some((t) => t.id === id)) : [...set]); // 서버가 모르는 옛 초안 id 는 보내지 않는다
  // "다음": 지금 체크를 초안에 한 번 더 저장하고 다음 단계로. 완료 화면에서 되돌아왔으면(?next=complete) 완료 화면으로
  const next = useAction(async () => {
    await agreeManagerTerms(known(agreed));
    navigate(params.get('next') === 'complete' ? '/auth/signup/complete' : '/auth/signup/info');
  });
  const persist = (set) => agreeManagerTerms(known(set)).catch(() => {}); // 본문 보기로 나갔다 와도 유지되게 즉시 초안에
  const back = () => navigate('/auth/login');
  if (error) return <ScreenError error={error} onRetry={reload} navBar={<NavBar title="회원가입" onBack={back} />} />;
  if (!data) return <ScreenLoading navBar={<NavBar title="회원가입" onBack={back} />} />;

  const { copy, terms, note } = data;
  const allAgreed = terms.every((t) => agreed.has(t.id)); // '전체 동의하기' 체크 표시: 선택 약관까지 모두 켜졌을 때
  const requiredAgreed = terms.filter((t) => t.required).every((t) => agreed.has(t.id)); // "다음" 조건: 필수 약관만 모두 켜졌는지

  // 약관 한 줄 켜기/끄기. Set 을 새로 만들어 바꾼다 (같은 Set 을 고치면 React 가 바뀐 것을 모른다). 초안에도 바로 저장
  const toggle = (id, checked) => {
    const nextSet = new Set(agreed);
    if (checked) nextSet.add(id);
    else nextSet.delete(id);
    persist(nextSet);
    setAgreed(nextSet);
  };
  // '전체 동의하기': 켜면 모든 약관(선택 포함), 끄면 전부 해제
  const toggleAll = (checked) => {
    const next = checked ? new Set(terms.map((t) => t.id)) : new Set();
    persist(next);
    setAgreed(next);
  };


  return (
    <ScreenLayout
      navBar={<NavBar title="회원가입" onBack={back} />}
      stickyCta={
        <Button disabled={!requiredAgreed} loading={next.loading} onClick={next.run}>
          {copy.next}
        </Button>
      }
    >
      <StepDots step={1} total={3} className={styles.steps} />
      <h2 className={cx('text-heading-l', styles.title)}>{copy.title}</h2>

      <Card padding="none" gap="none" divided className={styles.agreement}>
        <Checkbox label={copy.agreeAll} checked={allAgreed} onChange={toggleAll} bold className={styles.agreeAll} />
        {/* 약관 한 줄씩: 체크 + "보기>"(약관 본문 화면 /auth/terms/:id) */}
        <ul className={styles.termRows}>
          {terms.map((t) => (
            <li key={t.id}>
              <TermRow label={t.label} checked={agreed.has(t.id)} onChange={(c) => toggle(t.id, c)} onView={() => navigate(`/auth/terms/${t.id}`)} />
            </li>
          ))}
        </ul>
      </Card>
      <NoteCard icon={ASSETS.termsNoteIcon}>{note}</NoteCard>
    </ScreenLayout>
  );
}
