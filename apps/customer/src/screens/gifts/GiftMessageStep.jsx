// 선물하기 3단계: 함께 보낼 메시지 (선택, 200자까지 — 서버 gifts.message 제한과 같다). 코드에서 정함 — GiftSend.jsx 가 그린다.
import { ScreenLayout, TextArea, Button } from '@ansimclean/ui';
import { GIFTS_COPY as C } from '../../api/mock.js';
import styles from './gifts.module.css';

const MAX = 200;

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.navBar
 * @param {string} props.message
 * @param {(message: string) => void} props.onChange 200자를 넘는 부분은 잘라서 부른다
 * @param {() => void} props.onNext 비워 둬도 다음으로 갈 수 있다
 */
export default function GiftMessageStep({ navBar, message, onChange, onNext }) {
  return (
    <ScreenLayout navBar={navBar} gap="md" stickyCta={<Button onClick={onNext}>{C.next}</Button>}>
      <h1 className={`text-heading-m ${styles.plain}`}>{C.steps.message}</h1>
      <div className={styles.list}>
        <TextArea
          ariaLabel={C.messageLabel}
          placeholder={C.messagePlaceholder}
          value={message}
          onChange={(e) => onChange(e.target.value.slice(0, MAX))}
        />
        <p className={`text-caption-l ${styles.counter}`} aria-live="polite">{C.messageCount(message.length)}</p>
      </div>
    </ScreenLayout>
  );
}
