// CameraStatusItem 검증: ok·issue 두 상태가 라벨과 상태 문구를 함께 보여 주고, issue 클래스가 붙는지.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import CameraStatusItem from './CameraStatusItem.jsx';

describe('CameraStatusItem', () => {
  it('OK 와 Issue 가 라벨·값을 함께 보여준다 (색만으로 구분하지 않음)', () => {
    const { rerender } = render(<CameraStatusItem status="ok" label="카메라" value="정상" />);
    expect(screen.getByRole('status')).toHaveTextContent('카메라정상'); // 라벨과 값 글자가 이어 붙어 나온다
    rerender(<CameraStatusItem status="issue" label="카메라" value="연결 안 됨" />);
    expect(screen.getByRole('status')).toHaveTextContent('연결 안 됨');
    expect(screen.getByRole('status').className).toContain('issue');
  });
});
