import { useEffect, useState } from 'react';
import styles from './KeyboardAwareFooter.module.css';

// 키보드로 뷰포트가 줄어들면 푸터 전체를 숨긴다. 키보드가 닫히면 원래 위치에 표시한다.
export default function KeyboardAwareFooter({ children, visible = true }) {
  const [keyboardOpen, setKeyboardOpen] = useState(false);
  useEffect(() => {
    const viewport = window.visualViewport;
    let width = window.innerWidth;
    let fullHeight = Math.max(window.innerHeight, viewport?.height ?? 0);
    const resize = () => {
      const height = viewport?.height ?? window.innerHeight;
      if (Math.abs(window.innerWidth - width) > 100) {
        width = window.innerWidth;
        fullHeight = window.innerHeight;
      }
      fullHeight = Math.max(fullHeight, window.innerHeight, height);
      setKeyboardOpen(fullHeight - height > 120);
    };
    window.addEventListener('resize', resize);
    viewport?.addEventListener('resize', resize);
    resize();
    return () => {
      window.removeEventListener('resize', resize);
      viewport?.removeEventListener('resize', resize);
    };
  }, []);
  return <footer className={styles.footer} hidden={!visible || keyboardOpen}>{children}</footer>;
}
