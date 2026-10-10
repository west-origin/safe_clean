# CLAUDE.md — 안심클린 작업 지침

이 파일은 어떤 모델·어떤 세션이 이어받아도 같은 품질로 작업하기 위한 규칙이다.
읽지 않고 시작하지 마라. 규칙과 충돌하는 요청이 오면 먼저 규칙을 인용하고 확인을 받아라.

---

## 0. 세션 시작 절차 (순서대로, 생략 금지)

1. `PROGRESS.md` 를 읽는다. 체크된 항목은 **완료** 다. 다시 만들지 마라. 이어서 할 지점을 한 줄로 보고한다.
2. `PLAN.md` 를 읽는다. 승인된 결정, 단계 순서, 화면 묶음(회차)이 있다.
3. 원본 스펙 `~/Desktop/figma-to-react.md` 를 읽는다. 없으면 `PLAN.md` 로 대신한다.
4. 현재 상태가 초록인지 확인하고 시작한다.
   ```bash
   npm install && npm test && npm run check:tokens && npm run lint
   cd backend && ./mvnw -q verify && cd ..    # backend/ 가 있고 백엔드 컴퓨터일 때만 (JDK 21 + Docker)
   ```
5. 작업 중인 회차(PLAN.md §3 표)의 항목만 한다. 다음 회차로 자동 진행하지 마라.
6. **백엔드 단계(B0~B8)는 `BACKEND_PLAN.md` 가 유일한 사양이다.** 시작 전에 §3(규칙)·§5(API 계약)·§6(도메인 규칙)·§15(검토 이력)를 읽고, 부록 A 체크리스트를 따른다. 현재 회차는 `PROGRESS.md` "백엔드" 절.

---

## 1. 바꿀 수 없는 결정 (사용자 승인 2026-09-18)

| 항목 | 결정 | 금지 |
|---|---|---|
| 언어 | **JavaScript + JSX** (`.js`, `.jsx`). 타입 힌트는 JSDoc 주석 | `.ts`, `.tsx` 파일 생성 |
| 스타일 | **CSS Modules**(`*.module.css`) + **CSS 변수**(토큰) | Tailwind, styled-components, emotion, 인라인 style 로 색·폰트 지정 |
| 플랫폼 | **React 웹(Vite)** → **Capacitor** 로 iOS/Android 앱 | React Native, Swift/Kotlin 코드 작성 |
| 라이브 | **LiveKit** (매니저 송출 + 서버 녹화). 화면은 `useLiveSession()` 훅만 안다 | 화면 컴포넌트에서 WebRTC API 직접 호출 |
| 서버 | **Java 21 + Spring Boot 4.0.x** (`backend/`, 백엔드 컴퓨터에서 작업) + **Supabase**(Postgres·Auth·Storage) + **LiveKit Cloud** — 2026-09-19 변경, 사양은 `BACKEND_PLAN.md`. 앱은 Spring API 만 부른다 | Node 서버, 앱에서 Supabase 직접 호출, 앱에 anon key 넣기 |
| 컴포넌트 문서 | 앱 안의 **갤러리 페이지** (`/dev/gallery`, DEV 전용) | Storybook |
| 새 패키지 | 꼭 필요할 때만. 추가 시 `PLAN.md` 에 이유 한 줄 기록 | "편해서" 추가 |

**모든 코드의 기준:** 유지보수 담당자는 HTML/CSS/JS/React/RN 만 아는 개발자다.
"이 사람이 읽고 고칠 수 있는가?" 가 아니면 더 단순하게 다시 써라. 영리한 추상화보다 반복이 낫다.

---

## 2. 절대 규칙 (스펙에서 온 것 + 이 프로젝트에서 정한 것)

1. **하드코딩 금지.** hex/rgb 색, px 폰트 크기, 임의 radius 를 코드에 쓰지 마라. 색·간격·radius 는 `var(--…)`, 글꼴은 `.text-<style>` 클래스. `npm run check:tokens` 가 실패하면 보고할 수 없다.
2. **추측 금지.** Figma 에 없는 정보(문구, 색, 상태, 데이터 형태)는 만들지 마라. 필요하면 코드에 `// TODO(figma): …` 를 남기고 `PROGRESS.md` 의 "판단이 필요한 항목" 에 적어라.
3. **순서 고정.** 토큰 → 타이포 → 프리미티브 → 화면. 화면은 `@ansimclean/ui` 의 프리미티브를 import 해서 **조립만** 한다. 화면 폴더의 CSS 에 색·폰트 선언을 두지 마라.
4. **Auto Layout → flexbox.** `position: absolute` 는 Figma 에서 실제로 절대 위치인 경우(아이콘 벡터 그룹, full-bleed 일러스트, 오버레이)만.
5. **회차마다 멈추고 보고.** 단계 3은 3회(Button → Field → 나머지), 단계 4는 13회(M1~M6, C1~C7). 보고 형식은 §6.
6. **`PROGRESS.md` 는 항목 단위로 즉시 갱신.** 회차 끝에 몰아서 하지 마라.
7. **항목마다 커밋.** 형식은 §7.
8. **Figma 파일을 바꾸지 마라.** `use_figma` 는 읽기 전용 스크립트에만 쓴다. 노드 생성·수정·삭제 금지.
9. **393px 기준 모바일 우선.** 중단점을 만들지 마라. 레이아웃은 flex 로 폭을 따라가고(폰 360~430 은 가로를 꽉 채운다), 컨테이너 상한 `--screen-max-width`(480px)를 넘는 태블릿·PC 에서만 가운데 정렬한다. 절대 위치 요소는 393 기준 `left: Npx` 대신 가운데(50%)·오른쪽 기준으로 둔다. (2026-09-21 변경 — 그 전엔 393 고정)
10. **문구는 Figma 그대로.** 한국어 문구를 바꾸거나 다듬지 마라.

---

## 3. Figma 읽는 법

| 항목 | 값 |
|---|---|
| fileKey | `oByA7xoimyX0pPaS7aJpuo` |
| 고객용 페이지 | `Page 1` (`0:1`) — 섹션: OnboardingAuth, MainTabs, HomeCleaning, OfficeCleaning, ApplianceCleaning, MoveInCleaning, LiveService, Design System |
| 매니저 페이지 | `청소매니저 앱` (`2108:2207`) — 화면 22개 + 컴포넌트 11개 |
| 최신 라이브 화면 | `Service/LiveLandscape` `2165:2222` (852×480), `Service/LivePortrait` `2167:2238` |
| 구조 덤프 | `design/figma-tokens.json` (변수 32 + 텍스트 스타일 23). 매니저 페이지 XML 은 `get_metadata(2108:2207)` 로 다시 받을 수 있다 |

### 도구 선택
- **화면·컴포넌트 구현할 때:** `figma:figma-design-to-code` 스킬을 먼저 로드하고 `get_design_context(nodeId)` 를 **노드 단위**로 호출한다. 페이지 전체를 요청하지 마라.
- **시각 대조:** `get_screenshot(nodeId)` 로 구현 결과와 나란히 비교한다. 화면 회차 보고에는 반드시 Figma 스크린샷과 브라우저 스크린샷을 비교한 결과를 적는다.
- **구조 파악:** `get_metadata(nodeId)`. 단, **Page 1 전체(`0:1`)는 응답이 너무 커서 실패한다.** 섹션이나 프레임 ID 로 좁혀서 호출하라.
- **목록·속성·프로토타입 연결:** `figma:figma-use` 스킬을 로드한 뒤 `use_figma` 에 **읽기 전용** 스크립트. 반환값(`return`)만 보인다.
- **이름 기준으로 찾아라.** 노드 ID 는 참고용이다. 이름이 스펙과 다르면 실제 파일을 따르고 차이를 보고한다.

### 자주 쓰는 읽기 스크립트 (use_figma)
```js
// 섹션 안의 화면 프레임 나열
const page = figma.root.children[0]; // Page 1. 매니저는 find(p => p.name === '청소매니저 앱') 후 await figma.setCurrentPageAsync(page)
const sec = page.children.find(n => n.type === 'SECTION' && n.name === 'MainTabs');
return sec.children.map(c => ({ id: c.id, name: c.name, w: c.width, h: c.height }));
```
```js
// 프로토타입 연결(라우팅 추출): 어떤 노드를 누르면 어느 프레임으로 가는지
const frame = await figma.getNodeByIdAsync('2115:2222');
const out = [];
for (const n of frame.findAll(n => 'reactions' in n && n.reactions.length)) {
  for (const r of n.reactions) {
    const dest = r.action && r.action.destinationId ? await figma.getNodeByIdAsync(r.action.destinationId) : null;
    out.push({ from: n.name, trigger: r.trigger && r.trigger.type, action: r.action && r.action.type, to: dest && dest.name, navigation: r.action && r.action.navigation });
  }
}
return out;
```
```js
// 컴포넌트 property 정의 (variant → props 매핑 근거)
const set = await figma.getNodeByIdAsync('2171:3080'); // Field/Text
return Object.entries(set.componentPropertyDefinitions).map(([k, v]) => ({ k, type: v.type, options: v.variantOptions, def: v.defaultValue }));
```

### 이 파일의 함정 (스펙 그대로)
- `(구버전 · 사용 안 함)` 프레임 2개 → 변환 금지. 최신은 위 표의 두 노드.
- `_Keyboard/iOS (do not export · OS native)` → 구현 금지.
- `(원본 이미지 · 참고용)` 레이어 → 무시.
- `VideoSurface (placeholder · 코드에서 <video>로 구현)` → `<video>` 엘리먼트. 포스터 이미지 넣지 마라.
- `(에셋 필요)` 3개(`BrandLogo`, `Mascot`, `Illustration`) → 플레이스홀더 컴포넌트 + TODO.
- `(교체필요: …)` 아이콘 15개 → 임시 에셋 + TODO. 단계 3 에서 실제 목록을 뽑아 `PROGRESS.md` "에셋 TODO" 에 기록.
- 3D 아이콘·일러스트는 **래스터**. SVG 로 바꾸지 마라. WebP 1x/2x/3x `srcset`. `packages/ui/src/assets/`.
- 벡터 아이콘만 SVG → React 컴포넌트, `fill="currentColor"`. `packages/ui/src/icons/`.
- 고객용 앱은 Auto Layout 94%. 절대 위치처럼 보이는 것을 함부로 flex 로 바꾸지 마라. 일부 자식은 의도적으로 프레임 밖으로 넘친다(overflow 주의).
- 예약 플로우 9화면은 4개 카테고리에 복제돼 있다. **하나의 파라미터화된 플로우**로 만들되, 단계 4 C5 에서 4개 섹션의 같은 이름 프레임을 `get_metadata` 로 diff 해서 정말 같은지 확인하고 결과를 보고하라.

---

## 4. 코드 규칙

### 폴더
```
packages/ui/src/
  tokens/         생성물만 (tokens.css, typography.css, tokens.js). 직접 수정 금지 → npm run tokens
  tokens/extra.css  ① Figma 에 없어서 코드에서 정한 값 (--touch-target, z-index, 모션) ② Figma 에 변수 없이 직접 쓰인 원본색 (--raw-*). 여기 말고는 새 변수 만들지 마라. 색은 ② 구역에만, 출처 주석 필수
  fonts.css / base.css / styles.css
  components/<Name>/<Name>.jsx, <Name>.module.css, <Name>.test.jsx, index.js
  icons/          SVG 아이콘 컴포넌트
  assets/         래스터 (webp)
  dev/            갤러리(Gallery.jsx)·견본. 두 앱이 /dev/gallery 로 마운트. 프로덕션에서 import 금지
  index.js        모든 컴포넌트 재export
apps/<customer|manager>/src/
  routes.jsx      라우팅 (프로토타입 연결에서 추출)
  screens/<flow>/<ScreenName>.jsx (+ .module.css 는 레이아웃만)
  api/            데이터 어댑터. 처음엔 mock. 화면은 이 함수들만 호출
  live/           useLiveSession() (단계 5)
```

### 컴포넌트 작성
- **파일 하나에 컴포넌트 하나.** 함수 컴포넌트 + hooks 만. class 컴포넌트, HOC, render props 금지.
- **Figma variant → prop 1:1.** 값은 Figma 값을 camelCase 로: `Status=InProgress` → `status="inProgress"`, `SettlementPending` → `"settlementPending"`. BOOLEAN property → boolean prop(`showCallBadge`), TEXT property → string prop(`label`, `title`, `expiry`).
- **상태 매핑 (Figma → 코드)**

  | Figma | 코드 |
  |---|---|
  | Pressed | `:active` (CSS) |
  | Focused | `:focus-visible` (CSS) |
  | Disabled | `disabled` 속성 (+ `aria-disabled` 는 button 이 아닐 때). chevron 숨김 |
  | Loading | `loading` prop → `aria-busy="true"`, 스피너 + 라벨 교체, chevron 제거 |
  | Error | `aria-invalid="true"` + 헬퍼 텍스트 `<p id>` 를 `aria-describedby` 로 연결. **색만으로 표시 금지** |
  | Selected / Checked | `aria-pressed` / `aria-checked` / `role="radio"` + `data-state` 로 스타일링 |
  | Active (탭) | `aria-current="page"` |
  | hover | **없음.** Figma 에 정의되지 않았다. 만들지 마라 |

- **터치 타겟:** 탭 가능한 모든 요소 `min-height: var(--touch-target)` (44px). 시각 크기가 작아도 히트 영역은 44px.
- **CSS Modules 규칙:** 클래스명 camelCase. 색·radius·gap·padding 은 토큰만. Figma 값이 토큰에 없으면(예: gap 5px) 원본값을 쓰고 `/* Figma 원본값 */` 주석 + PROGRESS.md 판단 항목. Figma stroke 는 INSIDE 이므로 크기가 고정된 요소는 `border` 대신 `box-shadow: inset 0 0 0 1px` 로 그린다. 시각 높이가 44 보다 작은 탭 요소는 `::before` 로 터치 영역만 넓힌다 (GiftBanner CTA, LivePillControl 참고). `width/height/border-width` 의 px 는 허용(예: `border: 1px solid var(--color-border-default)`). `font-size`/`font-weight`/`line-height` 는 CSS 에 쓰지 말고 `.text-*` 클래스를 JSX 에서 붙여라.
- **텍스트 스타일 적용:** `className={`text-body-default ${styles.label}`}` 처럼 전역 `.text-*` + 모듈 클래스를 같이 쓴다.
- **접근성:** 버튼은 `<button type="button">`, 이동은 `<a>`/`<Link>`. 아이콘 전용 버튼은 `aria-label`. 바텀시트는 `role="dialog" aria-modal="true"` + 포커스 이동 + ESC/배경 탭으로 닫기. 이미지는 `alt`(장식이면 `alt=""`).
- **JSDoc:** 컴포넌트 위에 props 를 한 줄씩 설명한다. 유지보수자가 에디터 힌트로 본다.
  ```js
  /**
   * @param {object} props
   * @param {'primary'|'secondary'|'destructive'} [props.variant='primary']
   * @param {boolean} [props.loading] Loading 상태: 스피너 + 라벨 교체, chevron 제거
   */
  ```
- **테스트:** 컴포넌트마다 `<Name>.test.jsx` 최소 1개. 확인할 것: 모든 variant 가 렌더된다, `disabled`/`loading` 이 속성으로 드러난다, Error 는 텍스트가 DOM 에 있다, 탭 요소 클래스에 `--touch-target` 이 쓰였다. Testing Library 의 `getByRole` 우선.
- **갤러리:** 새 컴포넌트마다 `packages/ui/src/dev/Gallery.jsx` 에 `<Section>`/`<Row>` 로 **Figma variant 전부**를 나열한 항목을 추가한다. 보고서의 "variant ↔ props 누락 목록" 은 갤러리와 Figma 를 대조해 쓴다.

### 화면 작성 (단계 4)
- 화면은 프리미티브 조립 + 레이아웃 CSS(flex, gap, padding = 토큰) 만. 새로운 시각 요소가 필요하면 **먼저 `packages/ui` 에 컴포넌트로 만들고** 화면에서 import 한다.
- 데이터는 `api/*.js` 의 함수로만 받는다. mock 은 Figma 화면에 보이는 값 그대로. 스키마는 `api/README.md` 에 표로 기록한다(판단 항목).
- 라우트 경로는 Figma 프레임 이름을 kebab-case 로: `Manager/Job/InProgress` → `/job/in-progress`. 프로토타입 연결을 `routes.jsx` 상단 주석에 표로 남긴다.
- 빈 상태(`*/Empty`)는 별도 라우트가 아니라 데이터가 비었을 때의 조건부 렌더링.
- `StatusBar` 는 OS 가 그리는 영역이다. 컴포넌트는 만들되 화면에서는 safe-area(`env(safe-area-inset-top)`) 여백으로 처리하고, 시계·배터리는 그리지 마라.
- 라이브 화면의 `<video>` 는 `playsInline muted autoPlay` 를 기본으로, 실제 스트림 연결은 단계 5.

---

## 5. 보고 전 필수 검증

```bash
npm run tokens && git diff --exit-code packages/ui/src/tokens   # 생성물이 최신인지
npm test
npm run check:tokens
npm run lint
npm run build
```
전부 통과해야 보고할 수 있다. 실패하면 고치고, 못 고치면 실패 출력을 그대로 보고에 넣는다.

화면 회차는 추가로: 개발 서버(`.claude/launch.json` 의 `customer`/`manager`)를 띄워 393px 폭으로 스크린샷을 찍고 Figma `get_screenshot` 과 비교한다. 포트 5173 이 다른 프로젝트에 물려 있으면 그 프로세스를 죽이지 말고 5174 나 다른 포트를 써라.

---

## 6. 보고 형식 (회차마다 이 순서로)

1. **한 줄 결론:** 무엇을 끝냈고 다음 회차는 무엇인지.
2. **만든 것:** 파일 경로와 한 줄 설명(표 또는 목록).
3. **Figma variant ↔ props 매핑 표** 와 **누락 목록** (없으면 "누락 없음").
4. **검증 결과 표** (§5 명령 5개 + 화면이면 스크린샷 비교).
5. **완료 기준 체크리스트** (스펙 "완료 기준" 9개, 해당 없는 항목은 "해당 없음").
6. **판단한 항목** (Figma 에 없어서 정한 것) — `PROGRESS.md` 에도 같은 내용.
7. **에셋 TODO** (있을 때).
8. **커밋 목록** (`git log --oneline` 의 이번 회차 부분).

보고 후 **멈춘다.** "계속할까요?" 를 묻고 다음 회차를 시작하지 않는다.

---

## 7. 커밋 규칙

- 항목 하나 = 커밋 하나. 형식: `<type>(<scope>): <무엇> (단계 N)` — 한국어.
  - type: `feat` `fix` `chore` `docs` `test` `refactor`
  - scope: `tokens` `typography` `ui` `customer` `manager` `live` `backend` `capacitor`
  - 예: `feat(ui): Button primary/secondary/destructive + loading (단계 3-a)`
- 본문에 무엇을 왜 했는지 2~4줄. 마지막 줄:
  `Co-Authored-By: <모델 이름> <noreply@anthropic.com>`
- 커밋 전에 §5 검증. 생성물(`tokens/`)은 항상 같은 커밋에 포함.
- `PROGRESS.md` 갱신은 해당 항목 커밋에 같이 넣는다.
- 사용자가 요청하지 않으면 push 하지 않는다. (아직 원격 저장소 없음)

---

## 8. 단계별 세부 지침

### 단계 3 — 프리미티브
- **3-a Button:** `Button/Primary`(`2171:2396`, Default|Pressed|Disabled|Loading), `Button/Secondary`(`2171:2401`), `Button/Destructive`(`2171:2406`) 를 `get_design_context` 로 각각 읽는다. `<Button variant loading disabled onClick>`. Primary 에는 Label TEXT property 가 없고 Secondary/Destructive 에는 있다 → 모두 `children` 으로 라벨을 받는다. chevron 유무는 Figma 를 따른다. (완료) `tokens/extra.css`, 갤러리, react-router 라우트는 이 회차에서 만들었다.
- **3-b TextField:** `Field/Text`(`2171:3080`, Default|Focused|Filled|Error|Disabled + Label/Placeholder TEXT). `<TextField label placeholder value onChange error helperText disabled>`. Error = 테두리 색 + 헬퍼 텍스트 + `aria-invalid`.
- **3-c 나머지 (완료, 3-c-1 매니저 / 3-c-2 고객용·라이브):** 고객용 `StatusBar` `TabBar`(Home|Repair|Reservations|Settings) `RadioOption` `Chip` `NoticeChip`(Default|Selected + Icon 슬롯) `StepProgress`(1~8) `ServiceCategoryList` `GiftBanner` `NoticeIcon` 18개 `InfoIcon` 4개 / 라이브 `CircleControl` `PillControl` `LabeledControl` (오버레이형과 하단바형은 **배경이 달라 분리**. 합치지 마라) / 매니저 `ManagerTabBar` `StatusBadge` `CameraStatusItem` `ChecklistItem` `CallCard` `JobCard` `ScheduleCard` `ListItem` `NavBar` / 공통 `Sheet` `Skeleton`. 많으면 3-c 를 두 번으로 나눠 보고해도 된다.
- 아이콘: `get_design_context` 가 주는 SVG(또는 `use_figma` 의 `node.exportAsync({format:'SVG'})`) 를 `icons/<Name>.jsx` 로 저장하고 `fill`/`stroke` 를 `currentColor` 로 치환. 래스터는 `get_design_context` 의 PNG URL 을 받아 PIL 로 WebP 1x/2x/3x 변환 (`assets/<name>@1x.webp`). `use_figma` 의 PNG base64 반환은 20KB 에서 잘리므로 쓰지 마라. `(교체필요: …)` 이름은 파일 상단 `// TODO(asset): 교체필요 …` 로 남기고 `PROGRESS.md` 에 목록.

### 단계 4 — 화면
- 회차 표는 `PLAN.md` §3. 매니저(M1~M6) 먼저, 고객용(C1~C7) 다음.
- 회차 시작 시 그 회차 프레임들의 프로토타입 연결을 §3 스크립트로 뽑아 `routes.jsx` 에 반영한다.
- 각 화면 파일 상단에 Figma 노드 ID 와 이름을 주석으로 남긴다: `// Figma: Manager/Home (2115:2222)`.
- (완료) `ScreenLayout`(navBar/tabBar/stickyCta 슬롯, Content 패딩 20/24/32, gap lg=24|md=20) 을 모든 화면이 쓴다. 하단 영역은 sticky.
- 화면 안 강조 카드는 `Card tone=default|live|brand|subtle`, 상태 점은 `Dot`, 빈 상태는 `EmptyState icon=smile|call`. 새 tone/icon 이 필요하면 ui 에 추가하고 PROGRESS 에 기록.
- 데이터: `apps/manager/src/api/manager.js` 의 함수 + `mock.js`. 새 화면마다 `api/README.md` 에 스키마 표를 추가. `?scenario=empty` 로 빈 상태.
- 탭 이동은 `useManagerTabs()`. 라우트 표는 `routes.jsx` 상단 주석. (매니저 22화면 완료 — Placeholder 제거됨)
- 고객용 앱(C1~C7)도 같은 구조로: `apps/customer/src/{api,screens,routes.jsx}`, 탭은 `TabBar`(Home|Repair|Reservations|Settings), 예약 플로우 9화면은 카테고리 파라미터(`/booking/:category/...`) 하나로. 시작 전에 Page 1 프로토타입 58개를 §3 스크립트로 뽑는다. (완료 — 고객용 29화면, Placeholder 제거됨. 예약 초안은 `screens/booking/bookingDraft.js`, 라이브 훅 자리는 `live/useLiveSession.js`)

### 단계 5 — 라이브 (별도 승인 후)
- **구조는 완료(2026-09-19).** `apps/*/src/live/liveClient.js`(앱에 하나뿐인 클라이언트 객체, 모든 연결 코드 자리)와 `useLiveSession.js`(훅)가 있다. 서버 쪽(LiveKit 토큰·Ingress·Egress)은 `backend/` 의 `live` 패키지(단계 B5, `BACKEND_PLAN.md` §8). 각 파일 상단의 `TODO(stage5)` 체크리스트만 채우면 된다. 화면 변경 범위는 계획 §9.2.
- `livekit-client` 만 추가. `liveClient.js` 가 연결·트랙·상태(`idle|connecting|live|paused|ended|error`)를 관리. 화면은 훅의 반환값만 쓴다.
- 토큰 발급·Ingress·Egress 는 Spring `backend/` 의 `live` 패키지 (Node `server/` 는 B1 에서 삭제됨). 비밀키는 `backend/.env` (`.env.example` 만 커밋).
- 매니저: 후면 카메라, 마이크, Pause = 트랙 `mute`, 화면 꺼짐 방지(Capacitor Keep Awake). (2026-10-10 다시 이 방식 — 영상 입력은 `apps/manager/src/live/sources/` 만 알고, publish 는 `liveClient.js`·`roomConnector.js` 만 안다. 카메라·마이크 권한은 Manifest·Info.plist 에 둔다(제거 금지, 사용자 지시). 지금은 영상만 송출 — 결정 §13-23)
- 고객: 시청, Mute = 오디오 트랙 토글, Capture = `<video>` → canvas → 이미지 저장, Back15/Forward15 = 녹화본(HLS/MP4) 탐색.

### 단계 6 — Capacitor (완료 2026-09-19)
- `apps/*/ios`, `apps/*/android` 는 Capacitor 가 만든 네이티브 프로젝트다. **네이티브 코드 수정 금지.** 손대는 파일은 `apps/*/capacitor.config.json`, iOS `ios/App/App/Info.plist` 의 권한 문구, Android `android/app/src/main/AndroidManifest.xml` 의 권한뿐.
- 웹 코드를 바꾼 뒤: `npm run cap:sync` (빌드 + 두 앱 네이티브에 복사). 열기: `npm run cap:ios -w apps/customer` / `cap:android`.
- Android 빌드는 시스템 Java(26) 대신 Android Studio 의 JDK 를 쓴다: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. `android/local.properties`(sdk.dir) 는 git 에 없으니 없으면 `sdk.dir=$HOME/Library/Android/sdk` 로 만든다.
- 폰트는 `packages/ui/src/fonts.css` 의 @font-face 6개(korean/latin × 400/500/700, woff2)만. @fontsource 의 `@import` 로 되돌리면 앱 용량이 13MB 늘어난다.
- 앱 아이콘·스플래시는 로고 에셋이 오면 `@capacitor/assets` 로 생성한다 (지금은 기본값).

---

## 9. 하지 말 것

- TypeScript 파일, Tailwind, Storybook, 상태 관리 라이브러리(필요성이 증명되기 전), CSS-in-JS.
- Figma 파일 수정. Figma 에 없는 문구·색·상태 지어내기.
- 화면에서 `packages/ui` 를 우회해 div 에 직접 스타일링.
- 한 회차 이상을 한 번에 진행. 보고 없이 다음 단계 시작.
- 생성물(`tokens/*.css`, `tokens.js`) 손 편집.
- 다른 프로젝트의 프로세스 종료(포트 충돌 시 다른 포트 사용).
- 사용자 승인 없는 push, 패키지 대량 추가, 프로젝트 구조 변경.

---

## 10. 점검 이후 규칙 (2026-09-19)

- **데이터를 받는 화면은 `useAsync` 로.** `const { data, error, reload, setData } = useAsync(() => getX(id), [id]);` 다음 줄에 `if (error) return <ScreenError onRetry={reload} navBar/tabBar />; if (!data) return <ScreenLoading … />;`. 화면 안에서 `useEffect + .then(setX)` 를 새로 쓰지 마라. 낙관적 갱신은 `setData`, 실패하면 되돌린다(Settings 참고).
- **버튼 동작(생성·취소·일시정지 …)은 `useAction`.** `const save = useAction(async () => { … });` → `<Button loading={save.loading} onClick={save.run}>` + `save.error` 면 `role="alert"` 문구. 훅은 early return 보다 위에. 화면 이동은 요청이 성공한 뒤에만(특히 라이브: `await live.pause()` 가 끝나야 일시정지 화면).
- **날짜 문자열은 `fromDateKey`/`toDateKey`** (`@ansimclean/ui`). `new Date('YYYY-MM-DD')` 금지 (시간대 버그).
- **Figma 에 없는 화면을 만들 때**: 파일 상단에 `(코드에서 정함)` 과 근거(어느 버튼에서 오는지)를 적고, 문구는 `api/mock.js` 에 모으고, PROGRESS.md "Figma 에 없어 코드에서 정한 화면" 에 추가한다. 기존 ui 프리미티브만 조립한다.
- **라우트는 `lazy: page(() => import(...))`** 로 추가한다. 로그인이 필요한 화면은 두 앱 모두 `RequireAuth` children 안에 (고객용 `/signup`·`/auth/*`·`/terms/:id`, 매니저 `/auth/*` 만 공개). 매니저 로그인·회원가입은 Figma 에 없어 코드에서 정했다(`apps/manager/src/screens/auth/`, 문구는 `mock.js` AUTH_COPY).
- **서버는 `backend/`(Spring)뿐** — 규칙은 §11. (Node `server/` 는 B1 에서 삭제됐다.)
- **DEV 확인용 쿼리**: `?scenario=empty`(빈 상태), `?scenario=error`(ScreenError). 프로덕션 빌드에서는 동작하지 않는다.
- **브라우저·카메라 API 는 `live/liveClient.js` 에서만** (카메라 테스트도 여기). 화면은 `useLiveSession()` 반환값만 쓴다.

---

## 11. 백엔드 규칙 (2026-09-19, `BACKEND_PLAN.md` §3 과 같다 — 바꾸면 둘 다 바꾼다)

폴더·패키지 구조는 `BACKEND_PLAN.md` §2, API 계약은 §5(`backend/docs/api.md`), 도메인 규칙은 §6(`backend/docs/domain.md`).

1. **경계 고정.** Controller(입출력) → Service(규칙·트랜잭션) → Repository(DB). 역방향 참조 금지. 다른 기능의 Repository 를 직접 쓰지 않는다(그 기능의 Service 를 부른다). **외부 효과(푸시·LiveKit·문자·Storage)는 트랜잭션 안에서 호출하지 않는다 — `outbox_events` 행으로 남기고 `OutboxDispatcher` 가 보낸다.** 실패할 수 있는 외부 효과는 반드시 재조정 스케줄러가 있다(계획 §2.5).
2. **응답 모양은 `backend/docs/api.md` 가 유일한 기준.** 필드 이름은 앱 `mock.js` 의 키와 글자 하나까지 같다. 바꾸려면 `api.md` → 백엔드 → `apps/*/src/api/*.js` 를 한 커밋에서 같이 바꾼다. 서버 응답과 앱 함수 계약이 다르면 어댑터 함수 안에서 변환한다(계획 §9.0 표).
3. **오류는 `ApiException` 만 던진다.** HTTP 상태·코드는 `ErrorCode` enum 에서만(계획 §5.0). `message` 는 화면에 그대로 보여도 되는 한국어. 스택트레이스·SQL 이 응답에 나가면 안 된다.
4. **DB 스키마는 Flyway SQL 로만 바꾼다.** `spring.jpa.hibernate.ddl-auto=validate` 고정. 커밋된 마이그레이션 파일은 절대 수정하지 않는다. 되돌리기도 새 버전으로.
5. **비밀값은 환경변수로만.** `application*.yml` 에 값 금지(`${VAR}` 참조만). `backend/.env.example` 에 이름만. `.env` 는 커밋 금지. 커밋 전 `git diff | grep -i secret`.
6. **상태 전이는 엔티티 메서드로.** `booking.start()` 처럼 허용된 전이만 메서드로 두고, 잘못된 전이는 `ApiException(INVALID_STATE)`. 서비스에서 `setStatus` 금지. 전이 표는 계획 §6.1.
7. **시간·금액·표시 문자열.** 시각은 `Instant`(UTC) 저장, 표시는 `KoreanFormat`(Asia/Seoul) 에서만. 금액은 `long` 원. 예약 생성 시 금액·수수료·소요 시간을 예약 행에 고정하고 오퍼·정산은 그 값에서만 계산한다.
8. **권한은 두 겹.** `SecurityConfig` 경로 접두사(`/customer/**` → `ROLE_CUSTOMER`, `/manager/**` → `ROLE_MANAGER`) + Service 소유권 검사(남의 리소스는 `NOT_FOUND` — 존재를 노출하지 않는다).
9. **쓰기 요청은 멱등.** 예약 생성·콜 수락·작업 시작/완료·문의는 `Idempotency-Key` 헤더 + `Idempotent.run`(계획 §6.9). 앱은 `crypto.randomUUID()` 로 키를 만들어 재시도 때 같은 키를 보낸다(§9.4).
10. **테스트는 기능마다 3층.** Service 단위(Mockito) / Controller 슬라이스(`@WebMvcTest`, JSON 모양·권한) / 통합(`@SpringBootTest` + Testcontainers Postgres, 핵심 흐름 `*IT`). 새 엔드포인트는 셋 다 없으면 보고 불가. Docker 가 없어 `-DskipITs` 로 건너뛰면 PROGRESS 에 "IT 미실행" 을 적는다.
11. **로그에 개인정보 금지.** 전화번호·주소·토큰은 마스킹. 요청 로그는 `method path status ms userId` 만.
12. **라이브(매니저 스마트폰).** 영상은 매니저 앱이 휴대폰 후면 카메라로 LiveKit 방에 직접 publish 한다(`LIVE_SOURCE=app`, 2026-10-10 사용자 결정 — 그 전엔 바디캠 → Ingress). 바디캠(Ingress) 코드는 `LIVE_SOURCE=ingress` 로 남아 있지만 쓰지 않는다. 앱 송출 참가자도 identity `camera-<managerId>` 라 일시정지·재조정기·녹화는 같은 코드를 쓴다(계획 §8.0). 일시정지는 `live/pause`(영상 차단, 확인 후 응답) → `jobs/pause`(작업 상태) 순서이고 서버가 선행 조건으로 강제한다(계획 §8.2.1). **영상이 나가는데 상태가 '일시정지' 인 순간은 없다.**
13. **한 항목 = 한 커밋** (`feat(backend): … (단계 B3)`), 회차마다 계획 §14 형식으로 보고 후 정지.
14. **검증 명령 (보고 전 필수. 백엔드 컴퓨터에서)**
    ```bash
    cd backend && ./mvnw -q verify
    ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=local &
    curl -s localhost:8080/actuator/health    # {"status":"UP"}
    cd .. && npm test && npm run lint && npm run check:tokens && npm run build
    ```

