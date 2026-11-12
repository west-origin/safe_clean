# 앱 아이콘

Customer와 Manager의 일반 UI 아이콘은 `packages/ui/src/icons`의 기존 컴포넌트 API를 통해 `lucide-react`를 사용한다. 고객 앱의 배너·볼륨 컨트롤도 같은 라이브러리를 사용한다.

- Lucide의 24×24 viewBox, 둥근 선 끝·모서리, `strokeWidth={2}`를 사용한다.
- 표시 너비·높이는 기존 컴포넌트의 기본값과 `size`를 유지한다. 작은 꺾쇠와 눈 아이콘의 비정사각형 영역도 유지한다.
- 색은 `currentColor`와 기존 CSS 토큰을 따른다. 채운 완료·인증·선택 표시는 원과 체크를 조합해 기존 배경/전경 토큰을 유지한다.
- 장식 아이콘은 `aria-hidden`과 `focusable="false"`를 사용한다. Spinner의 로딩 안내와 회전 클래스는 유지한다.
- 인라인 SVG이므로 아이콘 폰트, CDN, 외부 이미지, SVG 스프라이트 참조가 필요 없다. Android WebView와 iOS WKWebView에서 별도 아이콘 로더 없이 렌더링한다. 실제 기기 검증은 별도로 필요하다.

## 보존한 아이콘

- `BrandMarkIcon`, `BrandWordmark`, 소셜 로그인 브랜드, 앱 아이콘 및 브랜드 이미지.
- `Service*`, `Option*`, `Answer*`, `NoticeIcon*`: 서비스 분류·추가 옵션·요청 안내에 사용되는 커스텀 그림.
- `InfoWonIcon`: 원 안 원화 기호라는 의미를 그대로 표현하는 대체가 없어 보존.
- `LiveBack15Icon`, `LiveForward15Icon`: 15초 숫자가 포함된 기존 재생 컨트롤을 보존. 다른 초 단위나 숫자 없는 회전 화살표로 바꾸지 않는다.

대표 대응: 뒤로가기 → ArrowLeft/ChevronLeft, 문의 → Headset, 정산 → ReceiptText, 예약 → CalendarDays, 매칭 → UserRoundSearch/UserRoundCheck, 경고 → TriangleAlert, 캡처 → Camera.
