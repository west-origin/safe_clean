// 예약 플로우 6단계 '옵션 선택' 화면(BookingOptions.jsx) 테스트.
// 무료 옵션 개수 한도(택2), '다음' 뒤 초안에 저장되는 값(고른 key·요약 문구), 다음 화면 이동을 확인한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingOptions from './BookingOptions.jsx';

// 목: api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다. 옵션 목록은 mock.js 의 BOOKING_OPTIONS 에서 온다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Booking/Options 흐름', () => {
  // 예약 초안(sessionStorage)이 앞 테스트에서 남지 않게 비운다
  beforeEach(() => sessionStorage.clear());

  // 준비: 가정 청소(home) 옵션 화면에서 시작. 옵션 칸은 role="checkbox" 버튼이라 getByRole('checkbox') 로 찾는다.
  // 확인: 무료 2개를 고르면 세 번째가 비활성, 유료 하나를 더 고르고 '다음' → 요청사항 화면으로 가고,
  // 초안(sessionStorage 의 'ansimclean.booking.home')에 key 배열과 한 줄 요약 문구가 들어 있다.
  it('무료 옵션은 2개까지: 2개 고르면 나머지가 비활성, 다음을 누르면 초안에 저장되고 /notes 로 간다', async () => {
    render(
      <MemoryRouter initialEntries={['/booking/home/options']}>
        <Routes>
          <Route path="/booking/:category/options" element={<BookingOptions />} />
          <Route path="/booking/:category/notes" element={<p>요청사항 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    const dishes = await screen.findByRole('checkbox', { name: '설거지' });
    fireEvent.click(dishes);
    fireEvent.click(screen.getByRole('checkbox', { name: '세탁' }));
    expect(screen.getByRole('checkbox', { name: '침구교체' })).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox', { name: '반려동물 배변 청소 +3,000원' })); // 유료 옵션 이름은 줄바꿈을 공백으로 바꾸고 가격을 붙인 것 (OptionItem)
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('요청사항 화면')).toBeInTheDocument();
    const draft = JSON.parse(sessionStorage.getItem('ansimclean.booking.home'));
    expect(draft.freeOptions).toEqual(['dishes', 'laundry']);
    expect(draft.paidOptions).toEqual(['pet']);
    expect(draft.optionsSummary).toBe('설거지, 세탁, 반려동물 배변 청소');
  });
});
