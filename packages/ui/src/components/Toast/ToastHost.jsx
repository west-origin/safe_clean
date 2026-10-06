// 앱 루트(RouteShell)에 한 번만 둔다. showToast() 로 넣은 문구를 화면 아래에 3초 보여준다 (role=status, aria-live=polite)
// 고객 앱·매니저 앱의 RouteShell 이 <Outlet /> 아래에 둔다. 문구 저장소와 3초 타이머는 utils/toast.js 에 있다.
import { useToast, hideToast } from '../../utils/toast.js';
import { cx } from '../../utils/cx.js';
import styles from './ToastHost.module.css';

/** 지금 보여 줄 토스트({ id, message } 또는 null)를 useToast 로 구독해 그린다. 토스트를 누르면 hideToast 로 바로 닫는다. */
export default function ToastHost() {
  const toast = useToast();
  // aria-live 영역은 DOM 에 먼저 있어야 안의 변화가 읽힌다. 그래서 바깥 div 는 늘 그리고, 토스트가 있을 때만 버튼을 넣는다.
  // key={toast.id}: 새 토스트마다 key 가 바뀌어 버튼이 새로 만들어지므로, 올라오는 애니메이션이 매번 다시 돈다.
  return (
    <div className={styles.host} role="status" aria-live="polite">
      {toast && (
        <button type="button" key={toast.id} className={cx('text-body-s', styles.toast)} onClick={hideToast}>
          {toast.message}
        </button>
      )}
    </div>
  );
}
