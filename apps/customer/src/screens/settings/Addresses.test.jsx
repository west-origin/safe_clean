// 주소 관리 화면(Addresses.jsx) 테스트: 주소를 추가하고 '기본으로' 를 누르면 '예약 기본 주소' 표시가 옮겨 가는지.
// 서버는 test/fakeHttp.js 의 가짜 서버다 — 처음에 '집' 주소 하나(기본)가 있고, 기본 변경은 바뀐 목록 전체를 돌려준다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Addresses from './Addresses.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

/** 주소 관리 — 어느 주소가 예약 "기존 주소" 인지 보이고 바꿀 수 있다 (직접 사용 점검 U15) */
describe('주소 관리', () => {
  it('새 주소를 기본으로 바꾸면 표시가 옮겨 간다', async () => {
    render(
      <MemoryRouter initialEntries={['/settings/addresses']}>
        <Routes>
          <Route path="/settings/addresses" element={<Addresses />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('예약 기본 주소')).toBeInTheDocument(); // 가입 주소(첫 주소)
    expect(screen.queryByRole('button', { name: '집 주소를 기본으로' })).not.toBeInTheDocument();

    // 시트를 열어 '회사' 주소를 채우고 저장한다. within(sheet): 시트(dialog) 안에서만 찾는다
    fireEvent.click(screen.getByRole('button', { name: '+ 주소 추가' }));
    const sheet = screen.getByRole('dialog');
    fireEvent.change(within(sheet).getByPlaceholderText('예: 집, 회사'), { target: { value: '회사' } });
    fireEvent.change(within(sheet).getByPlaceholderText('도로명주소를 입력해주세요.'), { target: { value: '서울 강남구 역삼로 1' } });
    fireEvent.change(within(sheet).getByPlaceholderText('상세주소를 입력해주세요.'), { target: { value: '5층' } });
    fireEvent.click(within(sheet).getByRole('button', { name: '저장' }));

    // 버튼 이름은 aria-label(`${label} 주소를 기본으로`) 로 찾는다
    fireEvent.click(await screen.findByRole('button', { name: '회사 주소를 기본으로' }));
    expect(await screen.findByRole('button', { name: '집 주소를 기본으로' })).toBeInTheDocument(); // 옛 기본에 버튼이 생기고
    expect(screen.queryByRole('button', { name: '회사 주소를 기본으로' })).not.toBeInTheDocument(); // 새 기본에서는 사라진다
    expect(screen.getAllByText('예약 기본 주소')).toHaveLength(1);
  });
});
