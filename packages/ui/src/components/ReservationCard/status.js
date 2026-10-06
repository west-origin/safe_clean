// Figma: Reservations › ReservationCard › StatusBadge — 방문 예정·매니저 배정 중은 Figma 그대로.
// 청소 진행 중·완료·취소는 Figma 에 없어 코드에서 정함 (완료·취소 탭용). live 톤은 LIVE 배지 색(status/live)
// 예약 상태 → 배지 문구(label)와 색 톤(tone). ReservationCard 가 status 로 찾고, tone 은 CSS 의 .badge-<tone> 클래스가 된다.
// 여기 없는 status 를 넘기면 ReservationCard 가 오류를 던진다.
export const RESERVATION_STATUS = {
  scheduled: { label: '방문 예정', tone: 'brand' },
  assigning: { label: '매니저 배정 중', tone: 'muted' },
  inProgress: { label: '청소 진행 중', tone: 'live' },
  done: { label: '완료', tone: 'muted' },
  cancelled: { label: '취소', tone: 'muted' },
};
