// 예약 날짜는 한국 시간 기준으로 비교한다.
export function isVisitToday(reservation, now = new Date()) {
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(now);
  return reservation.date === today;
}

export function canEditReservation(reservation) {
  if (reservation.status === 'assigning') return true;
  return reservation.status === 'scheduled' && !isVisitToday(reservation) && (reservation.changeable ?? true);
}
