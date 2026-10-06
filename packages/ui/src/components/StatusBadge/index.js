// StatusBadge 폴더의 입구. 컴포넌트와 상태 표(STATUS)를 함께 내보내고, packages/ui/src/index.js 가 둘 다 @ansimclean/ui 로 내보낸다.
// STATUS 는 상태 목록을 돌며 배지를 전부 그릴 때 쓴다 (예: 갤러리는 status.js 에서 직접 가져온다).
export { default } from './StatusBadge.jsx';
export { STATUS } from './status.js';
