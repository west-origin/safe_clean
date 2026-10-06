// 개발용 컴포넌트 (갤러리/견본). 프로덕션 화면에서 import 하지 않는다.
// packages/ui/package.json 의 exports "./dev" 가 이 파일이다. 두 앱의 routes.jsx 가 import.meta.env.DEV 일 때만
// await import('@ansimclean/ui/dev') 로 Gallery 를 불러 /dev/gallery 경로에 붙인다.
// 공용 진입점(packages/ui/src/index.js)은 이 파일을 내보내지 않는다 — 갤러리 코드가 앱 번들에 섞이지 않게.
export { default as Gallery } from './Gallery.jsx';
export { default as TypographySpecimen } from './TypographySpecimen.jsx';
