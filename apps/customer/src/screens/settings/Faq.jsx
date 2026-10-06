// Settings › 자주 묻는 질문 (Figma 에 화면 없음 — 펼침 목록)
// 들어오는 길: 설정 화면의 '자주 묻는 질문' 메뉴(/settings/faq). 질문·답은 서버(GET /support/faq?audience=customer)에서 받는다.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Card, FaqItem, ScreenError, ScreenLoading, useAsync } from '@ansimclean/ui';
import { getFaq } from '../../api/customer.js';

/** 자주 묻는 질문 화면. 질문을 누르면 답이 펼쳐지고, 같은 질문을 다시 누르면 접힌다 */
export default function Faq() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => getFaq(), []);
  const [openId, setOpenId] = useState(null); // 펼친 질문 id 하나만 기억한다 — 다른 질문을 펼치면 앞의 것은 접힌다
  const navBar = <NavBar title="자주 묻는 질문" onBack={() => navigate('/settings')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  return (
    <ScreenLayout navBar={navBar}>
      <Card padding="none" gap="none">
        {data.map((f) => (
          <FaqItem key={f.id} question={f.question} answer={f.answer} open={openId === f.id} onToggle={() => setOpenId(openId === f.id ? null : f.id)} />
        ))}
      </Card>
    </ScreenLayout>
  );
}
