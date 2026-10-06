// Figma: Manager/Job/Preparing/Error (2146:2840) — 이제 JobPreparing 이 장비 상태로 같은 프레임을 그린다(검증 검토). 옛 경로는 그리로 보낸다
// routes.jsx 의 /job/:id/preparing/error 가 이 화면이다.
import { Navigate, useParams } from 'react-router';

// 옛 주소 호환용. <Navigate replace> 는 렌더되자마자 이동하고, 방문 기록에서 옛 주소를 새 주소로 바꿔 뒤로 가기로 다시 오지 않게 한다
export default function JobPreparingError() {
  const { id } = useParams();
  return <Navigate to={`/job/${id}/preparing`} replace />;
}
