// 라우터 오류 화면: 화면 렌더 중 에러(errorElement) 와 없는 주소(404). Figma 에 없음 — ScreenError 재사용 (코드에서 정함)
import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router';
import { ScreenError } from '@ansimclean/ui';

const HOME = '/';

// routes.jsx 가 두 자리에 둔다: 맨 바깥 라우트의 errorElement 와 path="*" 라우트(notFound).
// 없는 주소면 '홈으로', 그 밖의 오류면 '다시 시도'(새로 고침) 버튼을 보인다.
/**
 * @param {object} props
 * @param {boolean} [props.notFound] path="*" 라우트에서 true
 */
export default function RouteError({ notFound = false }) {
  const error = useRouteError(); // 라우터가 잡은 오류 (화면 코드 불러오기·렌더 중 던진 예외, 또는 라우터의 404 응답)
  const navigate = useNavigate();
  // isRouteErrorResponse = 라우터가 만든 'HTTP 응답 모양' 오류인지. 그 상태가 404 여도 없는 주소로 본다
  const missing = notFound || (isRouteErrorResponse(error) && error.status === 404);
  if (missing) return <ScreenError title="페이지를 찾을 수 없어요" description="주소를 다시 확인해주세요." retryLabel="홈으로" onRetry={() => navigate(HOME)} />;
  // navigate(0) = 방문 기록에서 0칸 이동 = 지금 페이지를 새로 고친다
  return <ScreenError title="화면을 표시하지 못했어요" description="다시 시도해도 계속되면 앱을 껐다 켜주세요." retryLabel="다시 시도" onRetry={() => navigate(0)} />;
}
