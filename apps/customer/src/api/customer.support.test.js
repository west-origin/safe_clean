// customer.js 의 고객지원 함수(FAQ·공지·1:1 문의) 테스트. 진짜 서버 대신 가짜 서버(test/fakeHttp.js)를 쓴다.
// 확인하는 것: 경로에 audience=customer 가 붙는지, 문의의 빈 내용 차단·앞뒤 공백 자르기·멱등 키 헤더.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { getFaq, getNotices, getNotice, submitInquiry, loginWithPhone } from './customer.js';
import { session } from './session.js';
import { __fake } from './http.js'; // vi.mock 때문에 가짜 서버 모듈의 호출 기록·상태(__fake)가 온다

vi.mock('./http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

describe('customer api — 지원 (B6, docs/api.md §5.5)', () => {
  // 테스트마다 기록을 비우고 로그인한 상태로 시작한다
  beforeEach(async () => {
    localStorage.clear();
    session.clear();
    __fake.calls.length = 0;
    __fake.inquiries.length = 0;
    await loginWithPhone({ phone: '12345678', password: 'password1' });
  });

  it('FAQ·공지는 audience=customer 로 읽고 본문은 id 로', async () => {
    expect((await getFaq())[0]).toMatchObject({ question: '예약을 변경하거나 취소할 수 있나요?' });
    const list = await getNotices();
    expect(list[0]).toEqual({ id: 'n2', title: '실시간 청소 영상 기능이 추가되었어요', date: '2026.09.01' });
    expect((await getNotice('n2')).body).toContain('실시간 영상');
    // 없는 공지 id 는 서버(가짜)의 NOT_FOUND 가 그대로 던져진다
    await expect(getNotice('없음')).rejects.toMatchObject({ code: 'NOT_FOUND' });
  });

  it('문의는 공백이면 서버에 가지 않고, 멱등 키를 헤더로 보내며, 앞뒤 공백을 자른다', async () => {
    await expect(submitInquiry({ content: '   ', submitKey: 'k' })).rejects.toMatchObject({ code: 'VALIDATION' });
    expect(__fake.inquiries).toEqual([]); // 가짜 서버에 접수된 문의가 없다 = 어댑터가 보내기 전에 막았다
    expect(await submitInquiry({ content: '  예약이 안 돼요 ', submitKey: 'k-1' })).toEqual({ id: 'inq-1' });
    expect(__fake.calls.at(-1)).toMatchObject({ method: 'POST', path: '/support/inquiries', body: { content: '예약이 안 돼요' }, idempotencyKey: 'k-1' });
  });
});
