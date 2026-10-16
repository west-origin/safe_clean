// Settings › 결제 수단 (Figma 에 화면 없음). 결제(PG) 연동 전이라 등록 기능은 없고 안내만 한다 — 실서버 단계에서 채운다
// 들어오는 길: 설정 화면의 '결제 수단' 메뉴(/settings/payment). 서버 GET /customer/payment-methods 는 연동 전에는 빈 목록을 준다.
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, EmptyState, ScreenError, ScreenLoading, useAsync } from '@ansimclean/ui';
import { getPaymentMethods } from '../../api/customer.js';
import { PAYMENT_COPY } from '../../api/mock.js';

/** 결제 수단 화면. 조회가 성공하면 받은 목록(data)과 상관없이 늘 '등록된 결제 수단이 없어요' 안내를 그린다 */
export default function PaymentMethods() {
  const navigate = useNavigate();
  // 목록은 아직 그리지 않지만, 조회 실패·로딩은 다른 설정 화면과 같은 틀로 보여 준다
  const { data, error, reload } = useAsync(() => getPaymentMethods(), []);
  const navBar = <NavBar title="결제 수단" onBack={() => navigate('/settings')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  return (
    <ScreenLayout navBar={navBar}>
      <EmptyState icon="smile" title="등록된 결제 수단이 없어요" description={PAYMENT_COPY.methodsNote} />
    </ScreenLayout>
  );
}
