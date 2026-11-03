// SwipeCalendar 테스트: 달마다 한 장, 끈 날은 disabled, 고른 날은 aria-pressed, 오늘은 '오늘', 누르면 onChange(key). 터치 영역 토큰을 쓴다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import SwipeCalendar from './SwipeCalendar.jsx';

const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'SwipeCalendar.module.css'), 'utf8');
const MONTHS = [{ year: 2026, month: 11 }, { year: 2026, month: 12 }];

describe('SwipeCalendar', () => {
  it('달 두 장, 끈 날·고른 날·오늘', () => {
    const onChange = vi.fn();
    render(<SwipeCalendar months={MONTHS} value="2026-11-20" todayKey="2026-11-03" onChange={onChange} isDisabled={(d) => d.getMonth() === 10 && d.getDate() < 3} />);
    expect(screen.getByRole('region', { name: '2026년 11월' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '2026년 12월' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '11월 2일' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '11월 3일 오늘' })).toHaveTextContent('오늘');
    expect(screen.getByRole('button', { name: '11월 20일' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: '12월 1일' }));
    expect(onChange).toHaveBeenCalledWith('2026-12-01');
  });

  it('가로로 한 장씩 멈추고 칸은 터치 크기', () => {
    expect(css).toMatch(/scroll-snap-type:\s*x mandatory/);
    expect(css).toMatch(/var\(--touch-target\)/);
  });
});
