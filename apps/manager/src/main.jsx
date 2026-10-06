// 매니저 앱의 시작점. index.html 의 script 태그가 이 파일을 불러온다.
// 공용 스타일(리셋·폰트·토큰) → 앱 전용 스타일 순서로 CSS 를 싣고, index.html 의 <div id="root"> 에 App 을 그린다.
import React from 'react';
import ReactDOM from 'react-dom/client';
import '@ansimclean/ui/styles.css';
import './index.css';
import App from './App.jsx';

// StrictMode: 개발 모드에서만 컴포넌트·effect 를 한 번 더 실행해 정리(cleanup) 누락 같은 버그를 드러낸다. 프로덕션 빌드에서는 아무 일도 하지 않는다
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
