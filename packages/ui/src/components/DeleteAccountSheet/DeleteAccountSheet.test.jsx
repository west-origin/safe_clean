// DeleteAccountSheet 테스트: 비밀번호가 비면 탈퇴 버튼이 꺼져 있고, 입력 후 누르면 onConfirm 에 그 값이 가며,
// 화면이 넘긴 error 문구가 보이고 비밀번호 칸이 aria-invalid 로 표시되는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import DeleteAccountSheet from './DeleteAccountSheet.jsx';

describe('DeleteAccountSheet', () => {
  it('비밀번호를 넣어야 탈퇴 버튼이 켜지고 onConfirm 에 비밀번호를 넘긴다. 오류 문구는 필드 아래', () => {
    const onConfirm = vi.fn();
    render(<DeleteAccountSheet open onClose={() => {}} onConfirm={onConfirm} error="진행 중인 예약을 먼저 취소해주세요" />);
    const button = screen.getByRole('button', { name: '탈퇴하기' });
    expect(button).toBeDisabled();
    fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'password1' } });
    fireEvent.click(button);
    expect(onConfirm).toHaveBeenCalledWith('password1');
    expect(screen.getByText('진행 중인 예약을 먼저 취소해주세요')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호 확인')).toHaveAttribute('aria-invalid', 'true');
  });
});
