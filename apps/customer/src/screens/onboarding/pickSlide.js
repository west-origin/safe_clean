// 온보딩 화면(Onboarding.jsx)이 처음 그릴 때 한 번 부르는 무작위 장 번호 함수. 테스트(Onboarding.test.jsx)도 직접 부른다.
/** 접속할 때마다 온보딩 슬라이드 한 장을 무작위로 (0 ≤ n < count). 테스트에서 고정하려고 컴포넌트 밖에 둔다 */
export const pickSlide = (count) => Math.floor(Math.random() * count);
