// (코드에서 정함) 예약 한 건(서버 ReservationResponse)을 ui ReservationTicket 카드로 그린다.
// 내 예약 화면(Reservations)의 카드와 홈 '다가오는 방문' 카드가 같은 모양을 쓰도록 이 파일 하나로 모았다 (2026-11-03 사용자 요청).
// 정하는 것: 카드 이름(매장·가정·원룸), 매칭 알약, 매니저 줄, 진행 상태, 시간 두 칸.
// 구분선 아래 버튼·안내(actions·note)는 화면마다 달라서 부르는 쪽이 넘긴다.
import { useEffect, useState } from 'react';
import { ReservationTicket, ASSETS, fromDateKey } from '@ansimclean/ui';
import { RESERVATIONS_COPY as COPY } from '../../api/mock.js';

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];

/** 카드 이름: 매장·가정·원룸 (home 은 서버 homeType 으로 나눈다. 옛 예약은 '가정/원룸'), 그 밖은 서버 이름. 정기 회차면 서버 이름의 ' · 정기 2/6회' 를 이어 붙인다 */
function titleOf(r) {
  const short = r.category === 'home' ? COPY.homeTypes[r.homeType] ?? COPY.categories.home : COPY.categories[r.category] ?? r.title.split(' · ')[0];
  const series = r.title.split(' · ').slice(1).join(' · ');
  return series ? `${short} · ${series}` : short;
}

/** 진행 상태: 취소 / 청소 완료 / 청소 중 / (매니저 수락 + 방문 onTheWayMinutes 분 전부터) 이동 중 / 예약 완료. 매칭 전이면 없음(줄을 감춘다) */
function progressOf(r, now) {
  if (r.status === 'assigning') return undefined; // '예약 완료' 는 매칭 완료 뒤부터 (2026-11-03 사용자 요청)
  if (r.status === 'cancelled') return COPY.progress.cancelled;
  if (r.status === 'done') return COPY.progress.done;
  if (r.status === 'inProgress') return COPY.progress.cleaning;
  const start = fromDateKey(r.date);
  if (r.status === 'scheduled' && start && r.time) {
    const [h, m] = r.time.split(':').map(Number);
    start.setHours(h, m);
    if (now >= start.getTime() - COPY.onTheWayMinutes * 60_000) return COPY.progress.onTheWay;
  }
  return COPY.progress.booked;
}

/** '2026-11-20' + '10:00' → '11.20 (금) 10:00' */
function shortDateTime(date, time) {
  const d = fromDateKey(date);
  return d ? `${d.getMonth() + 1}.${d.getDate()} (${WEEKDAY[d.getDay()]}) ${time}` : time;
}

/** 시간 두 칸: 완료면 실제 작업 시작·완료('방문 시간'·'종료 시간', 서버에 값이 없는 칸은 뺀다), 그 밖은 예약한 시작·끝 */
function timesOf(r) {
  if (r.status === 'done') {
    return [
      { label: COPY.visited, at: r.startedAt },
      { label: COPY.finished, at: r.endedAt },
    ]
      .filter((t) => t.at)
      .map((t) => ({ label: t.label, value: shortDateTime(...t.at.split(' ')) }));
  }
  return [
    { label: COPY.start, value: shortDateTime(r.date, r.time) },
    ...(r.endTime ? [{ label: COPY.end, value: shortDateTime(r.date, r.endTime) }] : []),
  ];
}

/**
 * @param {object} props
 * @param {object} props.reservation 서버 예약 카드 한 건 (GET /customer/reservations 의 reservations[] 한 칸, 홈 upcoming)
 * @param {string} [props.note] 구분선 아래 안내 한 줄
 * @param {{ label: string, onClick: () => void, tone?: 'brand' }[]} [props.actions] 구분선 아래 버튼들 (note 가 있으면 무시)
 */
export default function ReservationTicketCard({ reservation: r, note, actions }) {
  // '이동 중' 판단에 쓰는 지금 시각 — 1분마다 다시 읽는다 (그리는 중에 Date.now() 를 부르지 않는다)
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);
  const matching = r.status === 'assigning';
  return (
    <ReservationTicket
      title={titleOf(r)}
      // 완료·취소 카드에는 알약이 없다
      badge={r.status === 'cancelled' || r.status === 'done' ? undefined : matching ? 'assigning' : 'assigned'}
      schedule={r.rows?.[0]?.value ?? ''}
      avatar={ASSETS.managerAvatar}
      // 매칭 전에는 서버 안내('배정 후 알려드려요'·'매니저를 찾지 못했어요 · …') 대신 한 가지 문구 (2026-11-03 사용자 요청)
      manager={matching ? COPY.matching : r.rows?.find((row) => row.label === '매니저')?.value ?? ''}
      progress={progressOf(r, now)}
      times={timesOf(r)}
      note={note}
      actions={actions}
    />
  );
}
