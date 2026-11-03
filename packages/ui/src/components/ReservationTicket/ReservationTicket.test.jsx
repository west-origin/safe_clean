// ReservationTicket 테스트: 이름·일정·매칭 알약·매니저·진행 상태·두 칸이 보이고, note 가 있으면 버튼 대신 안내, actions 는 누르면 콜백.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import ReservationTicket from './ReservationTicket.jsx';

const BASE = { title: '가정', schedule: '2026년 11월 20일 (금) 오전 10시', avatar: { src: 'a.png' }, manager: '김미영 매니저', progress: '예약 완료', times: [{ label: '방문 시작', value: '11.20 (금) 10:00' }, { label: '종료 예정', value: '11.20 (금) 13:00' }] };

describe('ReservationTicket', () => {
  it('내용 + 버튼', () => {
    const onCancel = vi.fn();
    render(<ReservationTicket {...BASE} badge="assigned" actions={[{ label: '예약 변경', onClick: () => {} }, { label: '예약 취소', onClick: onCancel }]} />);
    const card = screen.getByRole('article', { name: '가정' });
    ['2026년 11월 20일 (금) 오전 10시', '매칭 완료', '김미영 매니저', '예약 완료', '방문 시작', '11.20 (금) 13:00'].forEach((t) => expect(card).toHaveTextContent(t));
    fireEvent.click(within(card).getByRole('button', { name: '예약 취소' }));
    expect(onCancel).toHaveBeenCalled();
  });

  it('note 가 있으면 버튼 대신 안내, badge 없으면 알약 없음', () => {
    render(<ReservationTicket {...BASE} note="1:1 문의로 알려주세요." actions={[{ label: '예약 취소', onClick: () => {} }]} />);
    expect(screen.getByText('1:1 문의로 알려주세요.')).toBeInTheDocument();
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByText('매칭 중')).toBeNull();
  });

  it('매칭 중이면 알약 문구가 매칭 중이고, progress 가 없으면 진행 상태 줄이 없다', () => {
    render(<ReservationTicket {...BASE} badge="assigning" manager="매니저님을 매칭 중이에요" progress={undefined} />);
    const card = screen.getByRole('article', { name: '가정' });
    expect(card).toHaveTextContent('매칭 중');
    expect(card).toHaveTextContent('매니저님을 매칭 중이에요');
    expect(card).not.toHaveTextContent('예약 완료');
  });
});
