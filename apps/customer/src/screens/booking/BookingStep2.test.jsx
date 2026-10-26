// 예약 플로우 3단계 '기존 주소를 사용할까요?' 화면(BookingStep2.jsx) 테스트.
// '옵션 선택' 칸을 눌렀을 때 주소 선택에 따라 옵션 화면 / 주소 검색 화면으로 갈리는지 확인한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingStep2 from './BookingStep2.jsx';
import { ADDRESS_RESULTS } from '../../api/mock.js';

// 목: api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — 기본 주소 조회도 거기서 답한다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 가정 청소(home)의 Step2 화면을 띄운다. 이동할 두 화면은 문구 한 줄(<p>)짜리 자리표시로 대신해 도착 여부만 본다
function renderStep2(category = 'home', suffix = '') {
  return render(
    <MemoryRouter initialEntries={[`/booking/${category}/step2${suffix}`]}>
      <Routes>
        <Route path="/booking/:category/step2" element={<BookingStep2 />} />
        <Route path="/booking/:category/options" element={<p>옵션 화면</p>} />
        <Route path="/booking/:category/address-search" element={<p>주소 검색 화면</p>} />
        <Route path="/booking/:category/confirm" element={<p>예약 확인 화면</p>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Booking/Step2 주소 선택', () => {
  // 예약 초안(sessionStorage)이 앞 테스트에서 남지 않게 비운다 — 둘째 테스트는 기본값 '네' 에 기대기 때문
  beforeEach(async () => {
    sessionStorage.clear();
    const { __fake } = await import('../../api/http.js');
    __fake.hasDefaultAddress = true;
  });

  // 새 주소를 아직 넣지 않은 채 옵션 칸을 누르면, 기존 주소로 예약되지 않게 주소 검색부터 가야 한다
  it('"아니요" 는 같은 화면에 검색과 상세주소를 열고 미입력 상태에서 이동을 막는다', async () => {
    renderStep2();
    fireEvent.click(await screen.findByRole('radio', { name: '아니요' }));
    expect(screen.getByRole('searchbox', { name: '주소 검색' })).toBeInTheDocument();
    expect(screen.getByLabelText('도로명주소')).toBeInTheDocument();
    expect(screen.getByLabelText('상세주소')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    const options = screen.getByRole('button', { name: /옵션을 선택해주세요/ });
    expect(options).toBeDisabled();
    fireEvent.click(options);
    expect(screen.queryByText('주소 검색 화면')).toBeNull();
    expect(screen.queryByText('옵션 화면')).toBeNull();
  });

  it('"네"(기본) 면 옵션 필드는 옵션 화면으로 간다', async () => {
    renderStep2();
    fireEvent.click(await screen.findByRole('button', { name: /옵션을 선택해주세요/ }));
    expect(await screen.findByText('옵션 화면')).toBeInTheDocument();
  });

  it('검색 결과를 골라도 상세주소까지 입력해야 다음으로 간다', async () => {
    renderStep2();
    fireEvent.click(await screen.findByRole('radio', { name: '아니요' }));
    fireEvent.change(screen.getByRole('searchbox', { name: '주소 검색' }), {
      target: { value: '테헤란로' },
    });
    const [result] = await screen.findAllByRole('button', {
      name: new RegExp(ADDRESS_RESULTS[0].main),
    });
    fireEvent.click(result);
    expect(screen.getByLabelText('도로명주소')).toHaveValue(ADDRESS_RESULTS[0].main);
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('상세주소'), { target: { value: '   ' } });
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('상세주소'), { target: { value: ' 101동 202호 ' } });
    expect(screen.getByRole('button', { name: /옵션을 선택해주세요/ })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('옵션 화면')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.home'))).toMatchObject({
      reuseAddress: 'no',
      roadAddress: ADDRESS_RESULTS[0].main,
      detailAddress: '101동 202호',
    });
  });

  it.each(['home', 'office', 'appliance', 'move-in'])(
    '%s에서도 직접 입력한 도로명·상세주소를 모두 요구한다',
    async (category) => {
      renderStep2(category);
      fireEvent.click(await screen.findByRole('radio', { name: '아니요' }));
      fireEvent.change(screen.getByLabelText('도로명주소'), {
        target: { value: '서울 강남구 테헤란로 123' },
      });
      expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
      fireEvent.change(screen.getByLabelText('상세주소'), { target: { value: '3층' } });
      expect(screen.getByRole('button', { name: '다음' })).toBeEnabled();
      fireEvent.change(screen.getByRole('searchbox', { name: '주소 검색' }), {
        target: { value: '새 주소 검색' },
      });
      expect(screen.getByLabelText('도로명주소')).toHaveValue('');
      expect(screen.getByLabelText('상세주소')).toHaveValue('');
      expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    }
  );

  it('새 도로명으로 수정하면 이전 상세주소를 지워 재입력을 요구한다', async () => {
    sessionStorage.setItem(
      'ansimclean.booking.home',
      JSON.stringify({
        reuseAddress: 'no',
        roadAddress: '서울 강남구 테헤란로 123',
        detailAddress: '3층',
      })
    );
    renderStep2();
    expect(await screen.findByRole('button', { name: '다음' })).toBeEnabled();
    fireEvent.change(screen.getByLabelText('도로명주소'), {
      target: { value: '서울 강남구 테헤란로 124' },
    });
    expect(screen.getByLabelText('상세주소')).toHaveValue('');
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
  });

  it('확인 화면에서 주소를 수정한 경우 입력 후 확인 화면으로 돌아간다', async () => {
    renderStep2('home', '?returnTo=%2Fbooking%2Fhome%2Fconfirm');
    fireEvent.click(await screen.findByRole('radio', { name: '아니요' }));
    fireEvent.change(screen.getByLabelText('도로명주소'), {
      target: { value: '서울 강남구 테헤란로 123' },
    });
    fireEvent.change(screen.getByLabelText('상세주소'), { target: { value: '2층' } });
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('예약 확인 화면')).toBeInTheDocument();
  });

  it('저장 주소가 없으면 네 상태로 다음 단계에 갈 수 없다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.hasDefaultAddress = false;
    renderStep2();
    expect(await screen.findByRole('button', { name: '다음' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /옵션을 선택해주세요/ })).toBeDisabled();
  });
});
