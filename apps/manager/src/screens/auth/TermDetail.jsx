// Manager/Auth/Terms/:id (코드에서 정함) — SignUp/Terms 의 "보기>" 가 여는 약관 본문.
// 본문은 서버(GET /terms/:id?audience=manager, api/manager.js getManagerTerm)에서 받는다.
// mock.js 의 MANAGER_TERM_BODIES 는 테스트와 DEV(VITE_API_MOCK=1)의 가짜 서버(test/fakeHttp.js)가 같은 모양으로 돌려주는 값이다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getManagerTerm } from '../../api/manager.js';
import styles from './auth.module.css';

/**
 * 약관 본문 화면 (/auth/terms/:id, 로그인 없이 열리는 공개 화면). 받은 { title, body } 중
 * 제목은 NavBar 에, 본문은 줄바꿈을 살려(.primary 의 pre-line) 보인다. 뒤로는 방문 기록의 이전 화면으로 간다
 * (예: SignUpTerms 의 "보기>" 에서 왔으면 약관 동의 화면 — 체크는 가입 초안에 남아 있다).
 */
export default function TermDetail() {
  const navigate = useNavigate();
  const { id } = useParams(); // 주소의 :id 자리 (약관 id)
  const { data, error, reload } = useAsync(() => getManagerTerm(id), [id]);
  const back = () => navigate(-1);
  // 서버가 404(없는 약관 id)면 제목을 '약관을 찾을 수 없어요' 로 바꾸고, 그 밖의 오류는 ScreenError 기본 제목
  if (error) return <ScreenError error={error} onRetry={reload} navBar={<NavBar title="약관" onBack={back} />} title={error?.status === 404 ? '약관을 찾을 수 없어요' : undefined} />;
  if (!data) return <ScreenLoading navBar={<NavBar title="약관" onBack={back} />} />;
  return (
    <ScreenLayout navBar={<NavBar title={data.title} onBack={back} />}>
      <p className={cx('text-body-default', styles.primary)}>{data.body}</p>
    </ScreenLayout>
  );
}
