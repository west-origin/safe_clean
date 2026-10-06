// WeekCalendar 테스트: 고른 날짜가 든 주가 월요일부터 보이는지, 날짜를 누르면 onChange('YYYY-MM-DD') 를 부르는지,
// ‹ › 로 한 주씩 움직이는지, 기본 판정이 주말을 막는지, 서머타임이 끝나는 주를 지나도 요일 칸이 밀리지 않는지 본다.
import { test, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import process from 'node:process';
import WeekCalendar from './WeekCalendar.jsx';

test('고른 날짜가 든 주를 월~일로 보여주고, 주 단위로 움직이며, 고르면 onChange', () => {
  // isDisabled={() => false}: 막힌 날이 없게 해서, 테스트를 돌리는 날짜와 상관없이 누를 수 있게 한다.
  // 다음 주로 한 번, 이전 주로 두 번 → 처음 주(21일)보다 한 주 앞(14일)이 보여야 한다
  const onChange = vi.fn();
  render(<WeekCalendar value="2026-09-23" onChange={onChange} isDisabled={() => false} />);
  expect(screen.getByText('2026년 9월')).toBeInTheDocument();
  expect(screen.getByRole('radio', { name: '9월 21일' })).toBeInTheDocument(); // 월요일
  expect(screen.getByRole('radio', { name: '9월 23일' })).toHaveAttribute('aria-checked', 'true');
  fireEvent.click(screen.getByRole('radio', { name: '9월 24일' }));
  expect(onChange).toHaveBeenCalledWith('2026-09-24');
  fireEvent.click(screen.getByRole('button', { name: '다음 주' }));
  expect(screen.getByRole('radio', { name: '9월 28일' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '이전 주' }));
  fireEvent.click(screen.getByRole('button', { name: '이전 주' }));
  expect(screen.getByRole('radio', { name: '9월 14일' })).toBeInTheDocument();
});

test('기본 판정으로 주말과 지난 날은 고를 수 없다', () => {
  // 먼 미래(2099-09-21, 월요일) 주를 띄워 '지난 날' 조건에 걸리지 않게 하고, 토요일(26일)은 막히고 화요일(22일)은 열리는지 본다
  // (테스트 이름과 달리 '지난 날은 막힌다' 는 여기서 확인하지 않는다)
  render(<WeekCalendar initialDate={new Date(2099, 8, 21)} />);
  expect(screen.getByRole('radio', { name: '9월 26일' })).toBeDisabled(); // 토
  expect(screen.getByRole('radio', { name: '9월 22일' })).not.toBeDisabled();
});

// 서머타임 시험: 기기 시간대를 미국 동부(America/New_York)로 바꿔 둔다. Node 는 process.env.TZ 를 바꾸면 그 뒤의 Date 계산에 바로 쓴다.
// 테스트가 끝나면 원래 값으로 되돌려 다른 테스트에 새지 않게 한다
const ORIGINAL_TZ = process.env.TZ;
afterEach(() => {
  if (ORIGINAL_TZ === undefined) delete process.env.TZ;
  else process.env.TZ = ORIGINAL_TZ;
});

test('서머타임이 끝나는 날(하루 25시간)을 지나 다음 주로 가도 월요일 칸이 월요일이다 (미국 동부 시간)', () => {
  process.env.TZ = 'America/New_York';
  // 2026-11-01(일)이 미국 동부의 서머타임이 끝나는 날이다. 10/26 주에서 다음 주로 가면 11/2(월) ~ 11/8(일) 이어야 한다
  expect(new Date(2026, 10, 1, 12).getTimezoneOffset()).not.toBe(new Date(2026, 9, 26, 12).getTimezoneOffset()); // 시간대가 정말 바뀌었는지 (전후 시차가 다르다)
  render(<WeekCalendar value="2026-10-28" isDisabled={() => false} />);
  fireEvent.click(screen.getByRole('button', { name: '다음 주' }));
  const names = screen.getAllByRole('radio').map((r) => r.getAttribute('aria-label'));
  expect(names).toEqual(['11월 2일', '11월 3일', '11월 4일', '11월 5일', '11월 6일', '11월 7일', '11월 8일']);
  // 한 주 더 가고 두 주 돌아와도 제자리 (10/26 주)
  fireEvent.click(screen.getByRole('button', { name: '다음 주' }));
  expect(screen.getAllByRole('radio')[0]).toHaveAccessibleName('11월 9일');
  fireEvent.click(screen.getByRole('button', { name: '이전 주' }));
  fireEvent.click(screen.getByRole('button', { name: '이전 주' }));
  expect(screen.getAllByRole('radio')[0]).toHaveAccessibleName('10월 26일');
});
