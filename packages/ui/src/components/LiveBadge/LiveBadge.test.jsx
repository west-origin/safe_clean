import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import LiveBadge from './LiveBadge.jsx';
import RealtimePill from '../RealtimePill/RealtimePill.jsx';
import ViewerChip from '../ViewerChip/ViewerChip.jsx';
import CleaningStatusCard from '../CleaningStatusCard/CleaningStatusCard.jsx';
import ReassuranceBanner from '../ReassuranceBanner/ReassuranceBanner.jsx';

// 라이브 화면의 작은 조각들(LiveBadge·RealtimePill·ViewerChip·CleaningStatusCard·ReassuranceBanner)을
// 한 번에 그려 Figma 문구가 그대로 나오는지 확인한다. 글자, aria-label, role(status·note) 로 각각 찾는다.
describe('라이브 배지·칩·카드', () => {
  it('Figma 문구 그대로 렌더된다', () => {
    render(
      <>
        <LiveBadge />
        <RealtimePill />
        <ViewerChip count={1} />
        <CleaningStatusCard title="거실 청소 중" description="깨끗한 공간을 만들어가고 있어요!" />
        <ReassuranceBanner title="안심하고 지켜보세요!" description="항상 최선을 다해 청소하고 있어요 💙" />
      </>,
    );
    expect(screen.getByText('LIVE')).toBeInTheDocument();
    expect(screen.getByText('실시간')).toBeInTheDocument();
    expect(screen.getByLabelText('시청자 1명')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('거실 청소 중');
    expect(screen.getByRole('note')).toHaveTextContent('안심하고 지켜보세요!');
  });
});
