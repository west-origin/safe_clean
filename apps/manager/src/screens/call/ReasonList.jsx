// 시트 안의 사유 목록 (거절·중단 공용, 코드에서 정함 — 검증 검토): 사유는 시트를 열 때만 받고, 실패해도 화면 전체가 아니라 시트 안에서만 다시 시도한다.
// 예전엔 진행 중 화면·콜 상세가 /catalog/reasons 한 번의 지연·실패에 통째로 가려졌다.
// 쓰는 곳 예: 콜 거절 시트(CallList·CallDetail, getRejectReasons), 작업 중단 시트(JobInProgress, getPauseReasons).
import { RadioOption, ErrorState, Skeleton, useAsync } from '@ansimclean/ui';
import styles from './ReasonList.module.css';

/**
 * @param {object} props
 * @param {boolean} props.open 시트가 열려 있을 때만 조회한다
 * @param {() => Promise<string[]>} props.load 사유 목록 api 함수 (getRejectReasons / getPauseReasons)
 * @param {string} props.ariaLabel radiogroup 이름
 * @param {string|null} props.value 고른 사유 (없으면 첫 사유 — Figma: 첫 사유가 Selected)
 * @param {(reason: string) => void} props.onChange
 * @param {(reasons: string[]) => void} [props.onLoaded] 목록이 오면 (기본 선택을 부모가 알게)
 * 받는 중이면 Skeleton, 실패하면 시트 안 ErrorState(다시 시도), 받으면 RadioOption 목록을 그린다.
 * Sheet 는 닫히면 안의 내용을 그리지 않으므로, 시트를 열 때마다 새로 그려져 목록을 다시 받는다.
 */
export default function ReasonList({ open, load, ariaLabel, value, onChange, onLoaded }) {
  // open 이 false 면 요청하지 않는다(null → 로딩 모양). 받으면 onLoaded 로 부모에게 먼저 알리고 목록을 data 로 쓴다.
  // (onLoaded?.(r), r) 는 쉼표 연산자: 앞을 실행하고 뒤의 r 을 돌려준다
  const { data, error, reload } = useAsync(() => (open ? load().then((r) => (onLoaded?.(r), r)) : null), [open]);
  if (error) return <ErrorState error={error} title="사유를 불러오지 못했어요" onRetry={reload} />;
  if (!data) return <Skeleton height={132} className={styles.skeleton} />; // 사유 3줄 높이
  const selected = value ?? data[0];
  // role="radiogroup" + RadioOption(radio): 화면 읽기 도구가 '여러 개 중 하나 고르기' 로 읽는다. ariaLabel 이 그 묶음의 이름
  return (
    <div role="radiogroup" aria-label={ariaLabel} className={styles.reasons}>
      {data.map((r) => (
        <RadioOption key={r} label={r} selected={selected === r} onSelect={() => onChange(r)} />
      ))}
    </div>
  );
}
