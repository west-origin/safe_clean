import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import Home from './Home.jsx';

// 매니저 홈의 상단 안내 카드 테스트: 콜 배정 정지 안내가 승인 대기보다 먼저인지, 승인 전이면 승인 대기가 보이는지.
// 가짜 서버의 homeManager 에 넣은 값이 GET /manager/home 응답의 manager 필드를 덮어쓴다 (test/fakeHttp.js).

// http.js 를 가짜 서버(test/fakeHttp.js)로 바꿔 끼운다. 상태는 import 한 모듈의 __fake 로 바꾼다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Manager/Home 상단 안내 카드', () => {
  // 덮어쓴 매니저 필드가 다음 테스트에 남지 않게 되돌린다
  afterEach(async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.homeManager = {};
  });

  it('콜 배정이 정지된 매니저에게는 승인 대기 대신 정지 안내를 보여준다 (자체 점검 #11)', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.homeManager = { approved: true, suspension: '9/29 까지 콜을 받을 수 없어요' };
    // Home 이 useNavigate 를 쓰므로 라우터(MemoryRouter: 메모리 안의 라우터) 안에서 그린다
    render(<MemoryRouter><Home /></MemoryRouter>);
    expect(await screen.findByText('콜 배정 정지 중이에요')).toBeInTheDocument();
    expect(screen.getByText('9/29 까지 콜을 받을 수 없어요')).toBeInTheDocument();
    expect(screen.queryByText('승인 대기 중이에요')).toBeNull();
  });

  it('승인 전이면 승인 대기 카드를 보여준다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.homeManager = { approved: false };
    render(<MemoryRouter><Home /></MemoryRouter>);
    expect(await screen.findByText('승인 대기 중이에요')).toBeInTheDocument();
  });
});
