import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { useState } from 'react';
import Sheet from './Sheet.jsx';

// Sheet 테스트: 열림/닫힘 렌더, 닫는 세 가지 방법(ESC·배경·핸들)과 본문 클릭 무시,
// 부모가 렌더마다 새 onClose 를 넘겨도 입력창 포커스를 빼앗지 않는지.

// 부모가 입력할 때마다 다시 렌더되고 onClose 도 새 함수가 되는 흔한 형태
function Form() {
  const [open, setOpen] = useState(true);
  const [value, setValue] = useState('');
  return (
    <Sheet open={open} onClose={() => setOpen(false)} title="주소 추가">
      <input aria-label="이름" value={value} onChange={(e) => setValue(e.target.value)} />
    </Sheet>
  );
}

describe('Sheet', () => {
  it('열리면 dialog(aria-modal) 로 제목과 본문이 보이고, 닫히면 사라진다', () => {
    const { rerender } = render(
      <Sheet open onClose={() => {}} title="이 청소 요청을 거절하시겠어요?">
        <p>본문</p>
      </Sheet>
    );
    const dialog = screen.getByRole('dialog', { name: '이 청소 요청을 거절하시겠어요?' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByText('본문')).toBeInTheDocument();
    // 같은 자리에 open={false} 로 다시 그리면 dialog 가 DOM 에서 사라져야 한다
    rerender(<Sheet open={false} onClose={() => {}} title="t" />);
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('ESC, 배경 탭, 핸들 탭이 onClose 를 부르고 본문 클릭은 부르지 않는다', () => {
    const onClose = vi.fn();
    render(
      <Sheet open onClose={onClose} title="t">
        <p>본문</p>
      </Sheet>
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    fireEvent.pointerDown(screen.getByTestId('sheet-scrim')); // 스크림에서 누르기 시작해야 닫힌다(패널 안 드래그로 새어 나온 click 은 무시)
    fireEvent.click(screen.getByTestId('sheet-scrim'));
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));
    fireEvent.click(screen.getByText('본문'));
    // ESC·배경·핸들 세 번만 세어져야 한다. 본문 클릭은 닫지 않는다
    expect(onClose).toHaveBeenCalledTimes(3);
  });

  it('입력 중 부모가 새 onClose 를 넘겨도 포커스가 입력창에 남는다', () => {
    // 준비: 입력할 때마다 부모(Form)가 다시 렌더된다 → 확인: 두 번 입력한 뒤에도 포커스가 입력창에 남아 있는지
    render(<Form />);
    const input = screen.getByLabelText('이름');
    input.focus();
    fireEvent.change(input, { target: { value: '집' } });
    fireEvent.change(input, { target: { value: '집2' } });
    expect(document.activeElement).toBe(input);
    expect(input).toHaveValue('집2');
  });
});
