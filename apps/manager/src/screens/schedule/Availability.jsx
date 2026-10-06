// Figma: Manager/Schedule/Availability (2124:2457) + Manager/Schedule/AddUnavailable (2124:2521, 바텀시트)
// 프로토타입: Back → Schedule · + 근무 불가 시간 추가 → AddUnavailable · 저장 → Availability. 기본 근무시간 수정 → 요일별 시트 (코드에서 정함)
// 경로 /schedule/availability (주소에 ?add=1 이 있으면 근무 불가 추가 시트를 연 채로 시작).
// 오는 곳 예: 일정 화면의 '근무 일정 관리' 버튼, MY 메뉴 '근무 일정 관리'.
// 서버: GET /manager/availability, PUT /manager/availability/hours(기본 근무시간),
// POST /manager/availability/unavailable · DELETE /manager/availability/unavailable/{id}(근무 불가).
// 맡은 작업과 겹치는 휴무·근무 불가·근무시간 변경은 서버가 INVALID_STATE 로 거절하고, 그 문구가 시트 안 ActionError 에 보인다.
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, NavBar, Card, SummaryRow, Button, Sheet, TextField, RadioOption, Checkbox, cx, useAsync, useAction, useLatest, ScreenError, ScreenLoading, ActionError, showToast } from '@ansimclean/ui';
import { getAvailability, getRepeatOptions, addUnavailable, removeUnavailable, updateDefaultHours } from '../../api/manager.js';
import { REPEAT_LABELS } from '../../api/mock.js';
import { ACTION_FAILED_COPY } from '../../api/mock.js';
import styles from './Availability.module.css';

/** 근무 일정 관리 화면: 요일별 기본 근무시간 보기·수정, 근무 불가 일정 추가·삭제. 뒤로가기는 늘 일정(/schedule) */
export default function Availability() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams(); // useSearchParams: 주소의 ?이름=값 을 읽는다
  const { data, error, reload, setData } = useAsync(() => getAvailability(), []);
  const latest = useLatest(); // 저장 뒤 재조회가 그 다음 저장 결과를 되돌리지 않게 (검수 3차)
  const repeatQ = useAsync(() => getRepeatOptions(), []); // 반복 선택지 문구 — 서버가 아니라 앱 상수(mock.js REPEAT_OPTIONS)
  const repeatOptions = repeatQ.data ?? [];
  const [sheetOpen, setSheetOpen] = useState(searchParams.get('add') === '1'); // 근무 불가 추가 시트가 열려 있는가
  // 근무 불가 추가 시트의 입력값. repeat 는 화면 문구('반복 없음'·'매주'·'매월') — 보낼 때 manager.js 가 none|weekly|monthly 로 바꾼다
  const [form, setForm] = useState({ date: '', allDay: false, start: '', end: '', repeat: '반복 없음', reason: '' });
  const [hoursOpen, setHoursOpen] = useState(false); // 기본 근무시간 수정 시트가 열려 있는가
  const [hours, setHours] = useState([]); // [{ day, start, end, off }]

  // "기본 근무시간 수정" — Figma 에 연결 없음 → 요일별 시작/종료/휴무 시트 (코드에서 정함)
  const openHours = () => {
    setHours(
      data.defaultHours.map((r) => {
        return { day: r.day, start: r.off ? '' : (r.start ?? ''), end: r.off ? '' : (r.end ?? ''), off: Boolean(r.off) }; // 서버 start/end 원시값 (문구를 쪼개지 않는다)
      }),
    );
    setHoursOpen(true);
  };
  const setHour = (i, patch) => setHours((h) => h.map((row, idx) => (idx === i ? { ...row, ...patch } : row))); // i 번째 요일 행에 patch(시작·종료·휴무)를 덮어쓴다
  // 서버 저장은 useAction: 실패하면 시트를 닫지 않고 문구를 보여준다 (B4)
  const saveHours = useAction(async () => {
    // 휴무 요일은 시간 없이 보낸다. 시간은 manager.js 가 'HH:mm' 으로 맞추고, 형식이 틀리면 VALIDATION 오류 문구가 된다
    const rows = hours.map((h) => (h.off ? { day: h.day, off: true } : { day: h.day, start: h.start, end: h.end }));
    const saved = await updateDefaultHours(rows);
    latest.next(); // 이 전에 나간 재조회는 버린다
    setData((d) => ({ ...d, defaultHours: saved }));
    setHoursOpen(false);
    showToast('근무시간을 저장했어요');
  });
  const EMPTY_FORM = { date: '', allDay: false, start: '', end: '', repeat: '반복 없음', reason: '' }; // 저장 뒤 폼을 비울 때 (위 form 초기값과 같다)
  // 근무 불가 추가: 날짜는 '2026.09.18'·'2026-09-18' 등을 manager.js 가 'YYYY-MM-DD' 로 바꿔 보낸다. 실패하면 시트를 닫지 않고 문구
  const save = useAction(async () => {
    const item = await addUnavailable(form);
    // 목록은 서버 순서(날짜순)로 다시 받고, 시트를 다시 열 때 직전 값이 남지 않게 비운다
    setData((d) => ({ ...d, unavailable: [...d.unavailable.filter((u) => u.id !== item.id), item] })); // 우선 끝에 붙여 바로 보인다 (같은 id 는 한 번만)
    setForm(EMPTY_FORM);
    setSheetOpen(false);
    showToast('근무 불가 시간을 추가했어요');
    // 날짜순 목록을 다시 받는다. 그 사이 다른 저장·삭제가 있으면(latest 번호가 바뀌면) 이 응답은 버린다
    const my = latest.next();
    getAvailability().then((fresh) => latest.is(my) && setData(fresh)).catch(() => {}); // 실패해도 방금 저장한 항목은 이미 보인다
  });

  // 근무 불가 삭제 — 잘못 넣은 날짜·'매주' 를 되돌린다 (검증 검토). 실패 문구는 목록 아래
  const [removingId, setRemovingId] = useState(null); // '삭제 중…' 을 보일 카드 id
  // 모든 카드의 삭제 버튼이 remove 하나를 같이 쓴다 — 한 카드를 지우는 동안 다른 카드의 삭제도 막힌다(disabled={remove.loading})
  const remove = useAction(async (id) => {
    setRemovingId(id);
    try {
      await removeUnavailable(id);
      latest.next(); // 그 전에 나간 재조회 응답이 지운 항목을 되살리지 않게
      setData((d) => ({ ...d, unavailable: d.unavailable.filter((u) => u.id !== id) }));
      showToast('삭제했어요');
    } finally {
      setRemovingId(null);
    }
  });

  // 화면 데이터와 반복 선택지를 둘 다 받아야 그린다. '다시 시도'(reload)는 화면 데이터만 다시 받는다
  if (error || repeatQ.error) return <ScreenError error={error ?? repeatQ.error} onRetry={reload} navBar={<NavBar title="근무 일정 관리" onBack={() => navigate('/schedule')} />} />;
  if (!data || repeatQ.loading) return <ScreenLoading navBar={<NavBar title="근무 일정 관리" onBack={() => navigate('/schedule')} />} />;

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value })); // 입력칸 하나(key)의 onChange: 입력값을 form[key] 에 넣는다

  return (
    <ScreenLayout gap="xl" navBar={<NavBar title="근무 일정 관리" onBack={() => navigate('/schedule')} />}>
      <section className={styles.section}>
        <h2 className="text-title-card">기본 근무시간</h2>
        <Card padding="list" gap="none" divided>
          {data.defaultHours.map((row) => (
            <SummaryRow key={row.day} label={row.day} labelTone="primary" value={row.hours} tone={row.off ? 'secondary' : 'default'} emphasis="body" size="sm" />
          ))}
        </Card>
        <Button variant="secondary" onClick={openHours}>
          기본 근무시간 수정
        </Button>
      </section>

      <section className={styles.section}>
        <h2 className="text-title-card">근무 불가 일정</h2>
        {data.unavailable.map((item) => (
          <Card key={item.id} tone="muted" padding="xs" gap="xxs">
            <div className={styles.row}>
              {/* 반복 코드(weekly·monthly)는 REPEAT_LABELS 문구로, 모르는 값은 그대로. 반복 없음(none)은 표시하지 않는다 */}
              <span className="text-title-small">
                {item.date}
                {item.repeat && item.repeat !== 'none' && <span className={cx('text-caption-l', styles.secondary)}> · {REPEAT_LABELS[item.repeat] ?? item.repeat}</span>}
              </span>
              <span className={cx('text-body-s', styles.secondary)}>{item.label}</span>
            </div>
            <div className={styles.row}>
              <p className={cx('text-body-s', styles.secondary)}>{item.time}</p>
              {/* 누른 카드만 '삭제 중…'. aria-label 에 날짜·사유를 넣어 스크린리더가 어느 카드의 삭제인지 읽게 한다 */}
              <button type="button" className={cx('text-body-s', styles.remove)} disabled={remove.loading} onClick={() => remove.run(item.id)} aria-label={`${item.date} ${item.label} 삭제`}>
                {removingId === item.id ? '삭제 중…' : '삭제'}
              </button>
            </div>
          </Card>
        ))}
        <ActionError error={remove.error} fallback="삭제하지 못했어요. 다시 시도해주세요." />
        <Button variant="secondary" onClick={() => setSheetOpen(true)}>
          + 근무 불가 시간 추가
        </Button>
      </section>

      {/* 기본 근무시간 수정 시트: 휴무를 체크하면 그 요일 시간 칸은 비활성. 휴무가 아닌데 시간이 비면 저장 버튼 비활성 */}
      <Sheet open={hoursOpen} onClose={() => setHoursOpen(false)} title="기본 근무시간 수정" gap="lg">
        {hours.map((h, i) => (
          <div key={h.day} className={styles.hoursRow}>
            <span className="text-title-small">{h.day}</span>
            <TextField label="시작" type="time" value={h.start} onChange={(e) => setHour(i, { start: e.target.value })} disabled={h.off} />
            <TextField label="종료" type="time" value={h.end} onChange={(e) => setHour(i, { end: e.target.value })} disabled={h.off} />
            <Checkbox label="휴무" checked={h.off} onChange={(off) => setHour(i, { off })} />
          </div>
        ))}
        <ActionError error={saveHours.error} fallback={ACTION_FAILED_COPY.save} />
        <Button loading={saveHours.loading} onClick={saveHours.run} disabled={hours.some((h) => !h.off && (!h.start || !h.end))}>
          저장
        </Button>
      </Sheet>

      {/* 근무 불가 추가 시트 (Figma AddUnavailable). 날짜가 없거나, 하루 종일이 아닌데 시간이 비면 저장 버튼 비활성 */}
      <Sheet open={sheetOpen} onClose={() => setSheetOpen(false)} title="근무 불가 시간 추가" gap="lg">
        <TextField label="날짜" placeholder="2026.09.18" value={form.date} onChange={set('date')} />
        {/* Figma: RadioOption 하나를 토글로 사용. 선택 시 시간 입력은 비활성 (코드에서 정함) */}
        <RadioOption label="하루 종일" selected={form.allDay} onSelect={() => setForm((f) => ({ ...f, allDay: !f.allDay }))} />
        <div className={styles.timeRow}>
          <TextField label="시작 시간" placeholder="14:00" value={form.start} onChange={set('start')} disabled={form.allDay} />
          <TextField label="종료 시간" placeholder="18:00" value={form.end} onChange={set('end')} disabled={form.allDay} />
        </div>
        <p className="text-title-small">반복</p>
        <div role="radiogroup" aria-label="반복" className={styles.repeat}>
          {repeatOptions.map((opt) => (
            <RadioOption key={opt} label={opt} selected={form.repeat === opt} onSelect={() => setForm((f) => ({ ...f, repeat: opt }))} />
          ))}
        </div>
        <TextField label="사유 (선택)" placeholder="예: 병원 방문" value={form.reason} onChange={set('reason')} />
        <ActionError error={save.error} fallback={ACTION_FAILED_COPY.save} />
        <Button loading={save.loading} onClick={save.run} disabled={!form.date || (!form.allDay && (!form.start || !form.end))}>
          저장
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
