import { test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SelectedAddressCard from './SelectedAddressCard.jsx';

// SelectedAddressCard 테스트: 기본 제목(heading)·안내 문구·상세 주소가 화면에 나오는지만 본다.
// getByText(/정규식/) 는 글자 일부가 맞는 요소를 찾는다 — 상세 주소는 도로명과 한 문단에 있어 일부 일치로 찾는다.

test('제목·안내·주소 두 줄을 그린다', () => {
  render(<SelectedAddressCard roadAddress="서울특별시 강남구 테헤란로 123" detailAddress="604동 1002호" />);
  expect(screen.getByRole('heading', { name: '현재 선택된 주소' })).toBeInTheDocument();
  expect(screen.getByText('서비스 방문지')).toBeInTheDocument();
  expect(screen.getByText(/604동 1002호/)).toBeInTheDocument();
});
