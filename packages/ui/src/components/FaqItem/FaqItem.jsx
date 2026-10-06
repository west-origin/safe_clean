// 자주 묻는 질문 한 항목 (펼침/접힘). Figma 에 없음 — MenuRow 골격(높이 48, 라벨 + chevron)에 답변 영역을 더했다 (코드에서 정함)
// 고객 앱 설정 › 자주 묻는 질문(Faq) 화면이 목록으로 쓴다. 어느 항목이 열렸는지는 화면이 들고 있고
// (그 화면은 한 번에 하나만 연다) 이 컴포넌트는 open 을 받아 그리고 누르면 onToggle 만 부른다.
import { useId } from 'react';
import ChevronRightTinyIcon from '../../icons/ChevronRightTinyIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './FaqItem.module.css';

/**
 * @param {object} props
 * @param {string} props.question
 * @param {string} props.answer (줄바꿈은 \n)
 * @param {boolean} props.open
 * @param {() => void} [props.onToggle]
 * @param {string} [props.className]
 */
// 질문 버튼 + (열렸을 때만) 답변 문단.
export default function FaqItem({ question, answer, open, onToggle, className }) {
  // useId: 겹치지 않는 id. 버튼의 aria-controls 가 이 id 로 답변 문단을 가리켜 스크린리더가 둘을 잇는다.
  const id = useId();
  return (
    <div className={cx(styles.item, className)}>
      <button type="button" className={styles.question} aria-expanded={open} aria-controls={id} onClick={onToggle}>
        <span className={cx('text-body-default', styles.label)}>{question}</span>
        {/* 열리면 오른쪽 화살표를 90도 돌려 아래를 보게 한다 */}
        <ChevronRightTinyIcon className={cx(styles.chevron, open && styles.chevronOpen)} />
      </button>
      {open && (
        <p id={id} className={cx('text-body-s', styles.answer)}>
          {answer}
        </p>
      )}
    </div>
  );
}
