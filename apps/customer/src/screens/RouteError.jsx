// 라우터 오류 화면: 화면 렌더 중 에러(errorElement) 와 없는 주소(404). Figma 에 없음 — ScreenError 재사용 (코드에서 정함)
// routes.jsx 가 최상위(RouteShell) 라우트의 errorElement 와 path="*" 라우트(notFound)에 둔다.
import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router';
import { ScreenError } from '@ansimclean/ui';

const HOME = '/home';

/**
 * @param {object} props
 * @param {boolean} [props.notFound] path="*" 라우트에서 true
 * 없는 주소면 '홈으로'(/home), 그 밖의 오류면 '다시 시도'(navigate(0) = 지금 페이지를 새로 불러온다).
 */
export default function RouteError({ notFound = false }) {
  const error = useRouteError();
  const navigate = useNavigate();
  const missing = notFound || (isRouteErrorResponse(error) && error.status === 404); // isRouteErrorResponse: 라우터가 만든 응답 오류(상태 코드가 있는 것)인지
  if (missing) return <ScreenError title="페이지를 찾을 수 없어요" description="주소를 다시 확인해주세요." retryLabel="홈으로" onRetry={() => navigate(HOME)} />;
  return <ScreenError title="화면을 표시하지 못했어요" description="다시 시도해도 계속되면 앱을 껐다 켜주세요." retryLabel="다시 시도" onRetry={() => navigate(0)} />;
}
