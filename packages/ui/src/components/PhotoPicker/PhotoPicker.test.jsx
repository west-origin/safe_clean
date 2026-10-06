// PhotoPicker 검증: 추가 칸·첨부 사진·도움말이 그려지고, 사진을 누르면 onRemove, 장수가 max 에 닿으면 추가 칸이 사라지는지,
// 파일을 고르면 onAdd 에 File 배열이 오는지.
// rerender 는 같은 자리에 props 만 바꿔 다시 그린다 — 첫 테스트는 이것으로 사진이 max 장까지 찬 뒤를 본다.
import { test, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import PhotoPicker from './PhotoPicker.jsx';

test('추가 칸·첨부 사진을 그리고, 사진을 탭하면 onRemove, 최대 장수가 차면 추가 칸을 숨긴다', () => {
  const onRemove = vi.fn();
  const { rerender } = render(<PhotoPicker photos={[{ id: 'a', src: 'blob:a' }]} onRemove={onRemove} max={2} helper="도움말" />);
  expect(screen.getByRole('button', { name: '사진 추가' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '사진 1 삭제' }));
  expect(onRemove).toHaveBeenCalledWith('a');
  expect(screen.getByText('도움말')).toBeInTheDocument();
  rerender(<PhotoPicker photos={[{ id: 'a', src: 'blob:a' }, { id: 'b', src: 'blob:b' }]} max={2} />);
  expect(screen.queryByRole('button', { name: '사진 추가' })).not.toBeInTheDocument();
});

// 테스트에서는 파일 고르기 창을 열 수 없어서, 가짜 File 을 만들어 숨긴 input 에 change 이벤트로 직접 넣는다.
test('파일을 고르면 onAdd 에 File 목록이 온다', () => {
  const onAdd = vi.fn();
  render(<PhotoPicker photos={[]} onAdd={onAdd} />);
  const file = new File(['x'], 'a.png', { type: 'image/png' });
  fireEvent.change(screen.getByLabelText('사진 파일 선택'), { target: { files: [file] } });
  expect(onAdd).toHaveBeenCalledWith([file]);
});
