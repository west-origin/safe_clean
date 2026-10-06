// Calendar 컴포넌트와 dateKey.js 날짜 도우미(notBookableWithin, isWithinHours, seoulTodayKey)를 검증한다.
// 날짜 판정은 서버(Asia/Seoul)와 같은 결과여야 해서, 기기 시간대가 달라도 같은 값이 나오는지를 본다.
// getByRole('gridcell', { name: '9월 16일' }) 은 달력 칸의 aria-label 로 칸을 찾는다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Calendar from './Calendar.jsx';
import { isWithinHours, notBookableWithin, toDateKey, seoulTodayKey } from './dateKey.js';

const weekendOnly = (d) => d.getDay() === 0 || d.getDay() === 6; // Figma 예시(2025년 9월)는 이미 지난 달이라 과거 규칙을 끈다

// CSS 파일을 문자열로 읽어 터치 영역 토큰(--touch-target)을 쓰는지 확인한다
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'Calendar.module.css'), 'utf8');

describe('Calendar', () => {
  it('Figma 예시(2025년 9월)를 그리고 평일을 누르면 onChange, 주말·다른 달은 비활성', () => {
    const onChange = vi.fn();
    render(<Calendar initialMonth={new Date(2025, 8, 1)} onChange={onChange} isDisabled={weekendOnly} />);
    expect(screen.getByRole('grid', { name: '2025년 9월' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('gridcell', { name: '9월 16일' }));
    expect(onChange).toHaveBeenCalledWith('2025-09-16');
    expect(screen.getByRole('gridcell', { name: '9월 6일' })).toBeDisabled(); // 토
    expect(screen.getByRole('gridcell', { name: '8월 31일' })).toBeDisabled(); // 다른 달
    expect(css).toContain('var(--touch-target)');
  });

  it('선택한 날짜는 aria-selected, 이전/다음 달 이동', () => {
    render(<Calendar value="2025-09-16" isDisabled={weekendOnly} />);
    expect(screen.getByRole('gridcell', { name: '9월 16일' })).toHaveAttribute('aria-selected', 'true');
    fireEvent.click(screen.getByRole('button', { name: '다음 달' }));
    expect(screen.getByRole('grid', { name: '2025년 10월' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '이전 달' }));
    fireEvent.click(screen.getByRole('button', { name: '이전 달' }));
    expect(screen.getByRole('grid', { name: '2025년 8월' })).toBeInTheDocument();
  });

  it('기본 규칙: 오늘 이전 날짜는 선택 불가, 시간대와 무관하게 value 의 날짜가 선택된다', () => {
    const today = new Date();
    const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
    const key = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    render(<Calendar value={key(today)} initialMonth={today} />);
    expect(screen.getByRole('gridcell', { name: `${today.getMonth() + 1}월 ${today.getDate()}일`, selected: true })).toBeInTheDocument();
    // 오늘이 1일이면 어제는 지난달 칸이라 규칙과 상관없이 막혀 있거나 아예 없다 → 지난 날짜 규칙을 확인할 수 없어 건너뛴다
    if (yesterday.getMonth() === today.getMonth()) {
      expect(screen.getByRole('gridcell', { name: `${yesterday.getMonth() + 1}월 ${yesterday.getDate()}일` })).toBeDisabled();
    }
  });

  it('notBookableWithin: 오늘+days 뒤 날짜는 막고 그 안의 평일은 연다 (전체 재점검 B1)', async () => {
    const { notBookableWithin } = await import('./dateKey.js');
    // 가짜 시계: '지금' 을 아래 시각으로 고정한다. 테스트 끝에 useRealTimers 로 진짜 시계로 되돌린다
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-24T12:00:00+09:00')); // 2026-09-24(목) 서울 — 기기 시간대와 상관없이 같은 결과 (자체 점검 #40)
    const within60 = notBookableWithin(60);
    expect(within60(new Date(2026, 8, 25))).toBe(false); // 내일(금)
    expect(within60(new Date(2026, 10, 23))).toBe(false); // 60일째(월)
    expect(within60(new Date(2026, 10, 24))).toBe(true); // 61일째
    expect(notBookableWithin(undefined)(new Date(2027, 8, 24))).toBe(false); // 값이 없으면 기간 제한 없음(평일)
    vi.useRealTimers();
  });
});

describe('isWithinHours (직접 사용 점검 U2 — 서버의 변경·취소 기한과 같은 셈)', () => {
  const now = new Date('2026-09-24T13:30:00+09:00'); // 9/24 13:30 KST
  it('방문까지 N시간이 안 남았으면 true', () => {
    expect(isWithinHours('2026-09-25', '10:00', 24, now)).toBe(true); // 20.5시간
    expect(isWithinHours('2026-09-25', '13:30', 24, now)).toBe(false); // 딱 24시간 — 서버도 허용(starts_at − now ≥ 24h)
    expect(isWithinHours('2026-09-25', '14:00', 24, now)).toBe(false);
  });
  it('방문 시각과 오늘은 기기 시간대가 아니라 서울 기준이다 (자체 점검 #40)', () => {
    const pdt = new Date('2026-09-26T10:00:00-07:00'); // 미국 기기 현지 9/26 10:00 = KST 9/27 02:00
    expect(isWithinHours('2026-09-28', '09:00', 24, pdt)).toBe(false); // 31시간 남았다 — 기기 시간으로 읽으면 47시간
    expect(isWithinHours('2026-09-27', '16:00', 24, pdt)).toBe(true); // 14시간 — 기기 시간으로 읽으면 30시간이라 경고를 빠뜨렸다
    expect(seoulTodayKey(pdt)).toBe('2026-09-27');
    expect(seoulTodayKey(new Date('2026-09-26T14:59:00Z'))).toBe('2026-09-26');
    expect(seoulTodayKey(new Date('2026-09-26T15:00:00Z'))).toBe('2026-09-27'); // KST 자정
  });
  it('값이 비면 false', () => {
    expect(isWithinHours(null, '10:00', 24, now)).toBe(false);
    expect(isWithinHours('2026-09-25', null, 24, now)).toBe(false);
    expect(isWithinHours('2026-09-25', '10:00', undefined, now)).toBe(false);
  });
});

describe('notBookableWithin 기준일 (전체 점검 G3)', () => {
  // plus(n): 오늘 로컬 자정에서 n일 뒤. weekday(n): n일 뒤부터 토·일을 건너뛴 첫 평일 (배열로 돌려준다)
  const plus = (n) => { const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() + n); return d; };
  const weekday = (n) => { let d = plus(n); while (d.getDay() === 0 || d.getDay() === 6) d = plus(++n); return [d]; };
  it('기준일이 없으면 오늘부터, 있으면 그 방문일부터 센다', () => {
    const [far] = weekday(90);
    expect(notBookableWithin(60)(far)).toBe(true); // 새 예약: 오늘부터 60일 밖
    expect(notBookableWithin(60, toDateKey(plus(80)))(far)).toBe(false); // 80일 뒤 방문을 옮길 때는 그날부터 60일 안
    expect(notBookableWithin(60, toDateKey(plus(-5)))(far)).toBe(true); // 지난 날짜가 기준이면 오늘부터
  });
});

