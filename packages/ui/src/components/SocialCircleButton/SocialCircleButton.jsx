// (코드에서 정함) 소셜 로그인 동그라미 버튼 — 2026-11-03 사용자 첨부 로그인 시안의 카카오·페이스북 원 자리에 카카오·구글·네이버.
// 각 회사 로그인 버튼의 대표 모양을 단순하게 그렸다: 카카오 = 노랑 원 + 갈색 말풍선 'TALK', 구글 = 흰 원 + 4색 G, 네이버 = 초록 원 + 흰 N.
// 색은 tokens/extra.css 의 --raw-kakao-* · --raw-google-* · --raw-naver-*. 쓰는 곳: 고객 앱 첫 화면(SignUp).
import { cx } from '../../utils/cx.js';
import styles from './SocialCircleButton.module.css';

const MARKS = {
  kakao: (
    <>
      <path d="M24 13c-7.7 0-14 4.8-14 10.8 0 3.9 2.6 7.3 6.6 9.2l-1.4 5.1c-.1.5.4.8.8.6l6-4c.7.1 1.3.1 2 .1 7.7 0 14-4.8 14-10.9S31.7 13 24 13Z" fill="var(--raw-kakao-brown)" />
      <text x="24" y="26.4" textAnchor="middle" fontSize="7.6" fontWeight="800" fill="var(--raw-kakao-yellow)" fontFamily="Arial, sans-serif">TALK</text>
    </>
  ),
  google: (
    <g transform="translate(12 12) scale(.5)">
      <path fill="var(--raw-google-red)" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5Z" />
      <path fill="var(--raw-google-blue)" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65Z" />
      <path fill="var(--raw-google-yellow)" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19Z" />
      <path fill="var(--raw-google-green)" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48Z" />
    </g>
  ),
  naver: <path d="M16 15h6.2l6 8.9V15H34v18h-6.2l-6-8.9V33H16Z" fill="var(--color-surface-default)" />,
};

/**
 * @param {object} props
 * @param {'kakao'|'google'|'naver'} props.provider
 * @param {string} props.label 보조기기용 이름 (예: '카카오톡으로 로그인')
 * @param {() => void} [props.onClick]
 * @param {string} [props.className]
 */
export default function SocialCircleButton({ provider, label, onClick, className }) {
  return (
    <button type="button" aria-label={label} onClick={onClick} className={cx(styles.button, styles[provider], className)}>
      <svg width="48" height="48" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
        {MARKS[provider]}
      </svg>
    </button>
  );
}
