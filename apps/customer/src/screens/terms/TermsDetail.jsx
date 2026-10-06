// 약관 본문. Figma 에 화면 없음 — TermsConsent "보기>" 와 Settings 이용약관·개인정보 처리방침이 연다 (코드에서 정함)
// 주소 /terms/:id (로그인 전에도 열 수 있는 공개 경로). 본문은 서버 GET /terms/:id?audience=customer 에서 받는다.
// 뒤로가기는 바로 전 화면(navigate(-1))으로 — 약관 동의 화면·설정 화면 어느 쪽에서 열었든 그 화면으로 돌아간다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getTerm } from '../../api/customer.js';
import styles from './TermsDetail.module.css';

/** 약관 한 건의 본문 화면. 불러오는 동안은 제목 '약관', 받은 뒤에는 약관 제목을 상단 바에 보인다 */
export default function TermsDetail() {
  const navigate = useNavigate();
  const { id } = useParams(); // 약관 id (예: service, privacy) — 바뀌면 useAsync 가 다시 받는다
  const { data: term, error, reload } = useAsync(() => getTerm(id), [id]);
  const navBar = <NavBar title="약관" onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} title="약관을 불러오지 못했어요" />;
  if (!term) return <ScreenLoading navBar={navBar} />;
  return (
    <ScreenLayout navBar={<NavBar title={term.title} onBack={() => navigate(-1)} />}>
      <article className={styles.body}>
        <p className={cx('text-body-default', styles.text)}>{term.body}</p>
      </article>
    </ScreenLayout>
  );
}
