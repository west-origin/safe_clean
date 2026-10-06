// 오류 상태. Figma 에 없음 — EmptyState 와 같은 골격(84 원 + 제목 + 설명)에 "다시 시도" 버튼을 더했다 (PROGRESS.md 판단 항목).
// 데이터를 못 불러왔을 때 그 자리에 보여 준다. 화면 전체 오류는 ScreenError 가 이것을 감싸 쓰고,
// 화면 일부(예: 매니저 Schedule 달력 아래 달별 일정, JobDetail 의 사유 목록)는 직접 쓴다.
// 기본 문구와 재시도 여부는 utils/describeError.js 가 오류 종류(네트워크·404·그 외)로 정한다.
import AlertIcon from '../../icons/AlertIcon.jsx';
import Button from '../Button/Button.jsx';
import { cx } from '../../utils/cx.js';
import { describeError } from '../../utils/describeError.js';
import styles from './ErrorState.module.css';

/**
 * @param {object} props
 * @param {{ code?: string, status?: number, message?: string }|null} [props.error] useAsync 의 error — 있으면 종류별 문구·재시도 여부를 정한다 (title/description 을 주면 그것이 우선)
 * @param {string} [props.title] 기본 '잠시 문제가 생겼어요'
 * @param {string} [props.description] 기본 '네트워크 상태를 확인하고 다시 시도해주세요.'
 * @param {() => void} [props.onRetry] 있으면 "다시 시도" 버튼 (404 는 의미가 없어 숨긴다)
 * @param {string} [props.retryLabel='다시 시도']
 * @param {string} [props.className]
 */
// 오류 안내 + (재시도할 수 있으면) "다시 시도" 버튼. 서버 호출은 하지 않고 onRetry 만 부른다.
export default function ErrorState({ error, title, description, onRetry, retryLabel = '다시 시도', className }) {
  // 화면이 넘긴 title/description 이 먼저이고, 없으면 오류 종류에 맞는 기본 문구로 채운다.
  const described = describeError(error);
  title = title ?? described.title;
  description = description ?? described.description;
  if (!described.retryable) onRetry = undefined; // 404(없는 정보)는 다시 불러도 같으니 onRetry 를 받아도 버튼을 숨긴다
  // role="alert": 나타나는 즉시 스크린리더가 읽어 준다 (빈 상태의 EmptyState 는 덜 급한 role="status").
  return (
    <div className={cx(styles.error, className)} role="alert">
      <span className={styles.circle}>
        <AlertIcon size={36} />
      </span>
      <p className={cx('text-heading-s', styles.title)}>{title}</p>
      <p className={cx('text-body-default', styles.description)}>{description}</p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry} className={styles.retry}>
          {retryLabel}
        </Button>
      )}
    </div>
  );
}
