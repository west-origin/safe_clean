// 컴포넌트 갤러리 (Storybook 대신). 두 앱에서 /dev/gallery 로 열린다. DEV 전용.
// 규칙: 새 컴포넌트를 만들면 여기에 Figma variant 전부를 나열한다. 보고서의 "variant ↔ props 누락" 은 이 페이지와 Figma 를 대조해 쓴다.
// 붙이는 곳: 두 앱의 routes.jsx 가 import.meta.env.DEV 일 때만 '@ansimclean/ui/dev' 에서 가져와 라우트로 더한다.
// 페이지 순서: Button → TextField → 매니저 앱 묶음 → 고객 앱·라이브 묶음 → 수리 예약 묶음 → 아이콘 → 타이포.
// 여기서 넘기는 onClick={() => {}} 같은 빈 함수는 "누를 수 있는 모양" 만 보이려는 것이다
// (예: SettlementCard·ScheduleCard 는 onClick 이 있어야 카드 전체가 버튼으로 그려진다).
// 이 파일은 서버를 부르거나 다른 화면으로 이동하지 않는다. useState 는 눌러 보기용 임시 상태뿐이다.
import { useState } from 'react';
import Button from '../components/Button/index.js';
import StepQuestion from '../components/StepQuestion/index.js';
import OptionGroup from '../components/OptionGroup/index.js';
import SelectionSummary from '../components/SelectionSummary/index.js';
import PhotoPicker from '../components/PhotoPicker/index.js';
import InfoNote from '../components/InfoNote/index.js';
import SelectedAddressCard from '../components/SelectedAddressCard/index.js';
import ToggleChip from '../components/ToggleChip/index.js';
import WeekCalendar from '../components/WeekCalendar/index.js';
import ConfirmSummaryRow from '../components/ConfirmSummaryRow/index.js';
import SuccessHeader from '../components/SuccessHeader/index.js';
import StackedInfoCard from '../components/StackedInfoCard/index.js';
import TextField from '../components/TextField/index.js';
import Sheet from '../components/Sheet/index.js';
import Skeleton from '../components/Skeleton/index.js';
import ErrorState from '../components/ErrorState/index.js';
import NavBar from '../components/NavBar/index.js';
import ManagerTabBar from '../components/ManagerTabBar/index.js';
import StatusBadge from '../components/StatusBadge/index.js';
import { STATUS } from '../components/StatusBadge/status.js';
import ChecklistItem from '../components/ChecklistItem/index.js';
import CameraStatusItem from '../components/CameraStatusItem/index.js';
import ListItem from '../components/ListItem/index.js';
import CallCard from '../components/CallCard/index.js';
import JobCard from '../components/JobCard/index.js';
import ScheduleCard from '../components/ScheduleCard/index.js';
import StatusBar from '../components/StatusBar/index.js';
import TabBar from '../components/TabBar/index.js';
import RadioOption from '../components/RadioOption/index.js';
import Chip from '../components/Chip/index.js';
import NoticeChip from '../components/NoticeChip/index.js';
import { NOTICE_ICONS } from '../icons/noticeIcons.js';
import StepProgress from '../components/StepProgress/index.js';
import ServiceCategoryList from '../components/ServiceCategoryList/index.js';
import GiftBanner from '../components/GiftBanner/index.js';
import Checkbox from '../components/Checkbox/index.js';
import TermRow from '../components/TermRow/index.js';
import StepDots from '../components/StepDots/index.js';
import PhoneField from '../components/PhoneField/index.js';
import BackButton from '../components/BackButton/index.js';
import NoteCard from '../components/NoteCard/index.js';
import SearchField from '../components/SearchField/index.js';
import ImageOption from '../components/ImageOption/index.js';
import SlideCounter from '../components/SlideCounter/index.js';
import CarouselDots from '../components/CarouselDots/index.js';
import UnderlineTabs from '../components/UnderlineTabs/index.js';
import ReservationCard from '../components/ReservationCard/index.js';
import Toggle from '../components/Toggle/index.js';
import MenuRow from '../components/MenuRow/index.js';
import MenuGroup from '../components/MenuGroup/index.js';
import ProfileCard from '../components/ProfileCard/index.js';
import RepairCategoryList from '../components/RepairCategoryList/index.js';
import AddressResultList from '../components/AddressResultList/index.js';
import FaqItem from '../components/FaqItem/index.js';
import StepNavBar from '../components/StepNavBar/index.js';
import SelectField from '../components/SelectField/index.js';
import PromoBanner from '../components/PromoBanner/index.js';
import Calendar from '../components/Calendar/index.js';
import TimeSlotGrid from '../components/TimeSlotGrid/index.js';
import OptionItem from '../components/OptionItem/index.js';
import TextArea from '../components/TextArea/index.js';
import TipCard from '../components/TipCard/index.js';
import SummaryItem from '../components/SummaryItem/index.js';
import BookingInfoCard from '../components/BookingInfoCard/index.js';
import LiveBadge from '../components/LiveBadge/index.js';
import RealtimePill from '../components/RealtimePill/index.js';
import ViewerChip from '../components/ViewerChip/index.js';
import ManagerIdentity from '../components/ManagerIdentity/index.js';
import VideoSurface from '../components/VideoSurface/index.js';
import CleaningStatusCard from '../components/CleaningStatusCard/index.js';
import ReassuranceBanner from '../components/ReassuranceBanner/index.js';
import LiveProgress from '../components/LiveProgress/index.js';
import IconButton from '../components/IconButton/index.js';
import { ASSETS } from '../assets/index.js';
import LiveCircleControl from '../components/LiveCircleControl/index.js';
import LivePillControl from '../components/LivePillControl/index.js';
import LiveLabeledControl from '../components/LiveLabeledControl/index.js';
import * as Icons from '../icons/index.js';

// Button 이 쓰는 두 아이콘을 IconGallery 첫 줄에서 따로 보여 주려고 꺼낸다.
// import 문은 실행 전에 먼저 처리되므로(hoisting) 이 줄이 import 들 사이에 있어도 동작한다.
const { ChevronRight, Spinner } = Icons;
import EmptyState from '../components/EmptyState/index.js';
import ScreenLoading from '../components/ScreenLoading/index.js';
import Card from '../components/Card/index.js';
import Dot from '../components/Dot/index.js';
import SegmentedControl from '../components/SegmentedControl/index.js';
import SettlementCard from '../components/SettlementCard/index.js';
import ProgressBar from '../components/ProgressBar/index.js';
import ChecklistProgressCard from '../components/ChecklistProgressCard/index.js';
import ChecklistRequests from '../components/ChecklistRequests/index.js';
import ActionError from '../components/ActionError/index.js';
import LoadingBar from '../components/LoadingBar/index.js';
import { showToast } from '../utils/toast.js';
import TypographySpecimen from './TypographySpecimen.jsx';
import styles from './Gallery.module.css';

/**
 * 갤러리의 한 구획 (컴포넌트 하나 또는 몇 개 묶음). 위 선으로 구획을 나눈다.
 * @param {object} props
 * @param {string} props.title 구획 제목 (보통 컴포넌트 이름)
 * @param {string} [props.figma] 머리에 "Figma: …" 로 보일 안내 (노드 이름·ID, 또는 Figma 에 없다는 설명). 없으면 표시하지 않는다
 * @param {import('react').ReactNode} props.children 보통 Row 여러 개
 */
function Section({ title, figma, children }) {
  return (
    <section className={styles.section}>
      <header className={styles.sectionHeader}>
        <h2 className="text-heading-s">{title}</h2>
        {figma && <span className={`text-caption ${styles.figma}`}>Figma: {figma}</span>}
      </header>
      {children}
    </section>
  );
}

/**
 * 구획 안의 한 줄: 위에 어떤 variant·상태인지 적은 라벨, 아래에 실제 컴포넌트.
 * @param {object} props
 * @param {string} props.label 이 줄이 보여 주는 variant·상태 설명
 * @param {import('react').ReactNode} props.children 그 상태로 렌더한 컴포넌트
 */
function Row({ label, children }) {
  return (
    <div className={styles.row}>
      <span className={`text-label-s ${styles.rowLabel}`}>{label}</span>
      <div className={styles.rowBody}>{children}</div>
    </div>
  );
}

// Figma 에 있는 Button 3종(primary·secondary·destructive)과 그 상태 전부.
// outline·outlineGray(인스턴스 오버라이드)는 따로 줄이 없다. outline 은 ErrorState 의 '다시 시도' 버튼에서 보인다.
// Pressed 는 prop 이 아니라 CSS :active 라서 Default 와 같은 버튼을 눌러 보며 확인한다.
function ButtonGallery() {
  // '결제하기' 줄 전용: 누르면 2초 동안 loading 을 켰다가 끈다 (서버 호출 없이 모양 전환만 흉내 낸다).
  const [loading, setLoading] = useState(false);
  return (
    <Section title="Button" figma="Button/Primary 2171:2396 · Button/Secondary 2171:2401 · Button/Destructive 2171:2406">
      <Row label="Primary · Default">
        <Button>다음</Button>
      </Row>
      <Row label="Primary · Pressed (:active — 눌러서 확인)">
        <Button>다음</Button>
      </Row>
      <Row label="Primary · Disabled">
        <Button disabled>다음</Button>
      </Row>
      <Row label="Primary · Loading">
        <Button loading>다음</Button>
      </Row>
      <Row label="Primary · chevron 없음 (인스턴스 오버라이드)">
        <Button chevron={false}>확인</Button>
      </Row>
      <Row label="Primary · 클릭 → 2초 Loading (동작 확인)">
        <Button
          loading={loading}
          onClick={() => {
            setLoading(true);
            setTimeout(() => setLoading(false), 2000);
          }}
        >
          결제하기
        </Button>
      </Row>
      <Row label="Secondary · Default / Pressed(:active) / Disabled">
        <div className={styles.inline}>
          <Button variant="secondary">거절</Button>
          <Button variant="secondary" disabled>
            거절
          </Button>
        </div>
      </Row>
      <Row label="Destructive · Default / Pressed(:active) / Disabled">
        <div className={styles.inline}>
          <Button variant="destructive">취소하기</Button>
          <Button variant="destructive" disabled>
            취소하기
          </Button>
        </div>
      </Row>
      <Row label="나란히 (부모 flex 로 폭 조절)">
        <div className={styles.inline}>
          <Button variant="secondary">거절</Button>
          <Button>수락</Button>
        </div>
      </Row>
    </Section>
  );
}

// TextField 의 Figma 상태(Default·Focused·Filled·Error·Disabled)와 컴포넌트에 없어 코드에서 더한 Success·Hint,
// 그리고 직접 입력해 보는 제어 예시.
// Focused 도 prop 이 아니라 CSS(:focus-within) 라서 입력칸을 눌러 확인한다.
function TextFieldGallery() {
  const [value, setValue] = useState('');
  const [phone, setPhone] = useState('');
  // 전화번호 예시의 검사: 01 로 시작하고 뒤에 숫자 8~9개(합 10~11자리)가 아니면 오류 문구를 준다.
  // 아직 아무것도 안 쳤을 때(빈 값)는 오류를 띄우지 않는다. 오류가 없으면 빈 문자열이라 Error 모양이 꺼진다.
  const phoneError = phone.length > 0 && !/^01\d{8,9}$/.test(phone) ? '올바른 값을 입력해주세요' : '';
  return (
    <Section title="TextField" figma="Field/Text 2171:3080">
      <Row label="Default">
        <TextField label="라벨" placeholder="내용을 입력해주세요" />
      </Row>
      <Row label="Focused (:focus-within — 탭해서 확인)">
        <TextField label="라벨" placeholder="내용을 입력해주세요" />
      </Row>
      <Row label="Filled">
        <TextField label="라벨" value="입력된 내용" readOnly />
      </Row>
      <Row label="Error (테두리 + 헬퍼 텍스트 + aria-invalid)">
        <TextField label="라벨" value="입력된 내용" readOnly error="올바른 값을 입력해주세요" />
      </Row>
      <Row label="Disabled">
        <TextField label="라벨" placeholder="내용을 입력해주세요" disabled />
      </Row>
      <Row label="Success (Figma 컴포넌트에는 없음 — Auth/ProfileInfo-Phone 헬퍼)">
        <TextField label="비밀번호" type="password" value="sample-pass" readOnly success="사용가능한 비밀번호입니다!" />
      </Row>
      <Row label="Hint (코드에서 정함)">
        <TextField label="비밀번호" type="password" placeholder="비밀번호를 입력해주세요" hint="8자 이상 입력해주세요" />
      </Row>
      <Row label="제어 컴포넌트 (입력해 보기)">
        <TextField
          label="이름"
          placeholder="내용을 입력해주세요"
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </Row>
      <Row label="type=tel + 검증 (01 로 시작하는 10~11자리가 아니면 Error)">
        <TextField
          label="휴대폰 번호"
          placeholder="숫자만 입력"
          type="tel"
          inputMode="numeric"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          error={phoneError}
        />
      </Row>
    </Section>
  );
}

// 매니저 앱 컴포넌트 묶음 (NavBar ~ Skeleton) 과 두 앱이 같이 쓰는 화면 상태(로딩·빈 상태·오류·토스트).
function ManagerGallery() {
  const [tab, setTab] = useState('home'); // ManagerTabBar 첫 줄에서 누른 탭
  const [checked, setChecked] = useState(true); // 첫 번째 ChecklistItem 의 체크 여부 (나머지 둘은 고정)
  // Sheet 열림 여부. '시트 열기' 버튼과 CallCard 의 거절 버튼이 둘 다 이 값을 켠다.
  const [sheetOpen, setSheetOpen] = useState(false);
  return (
    <>
      <Section title="NavBar" figma="Manager/NavBar 2121:2264">
        <Row label="Title + 뒤로가기">
          <NavBar title="화면 제목" onBack={() => {}} />
        </Row>
        <Row label="뒤로가기 없음">
          <NavBar title="홈" />
        </Row>
      </Section>

      <Section title="ManagerTabBar" figma="Manager/TabBar 2109:2312">
        <Row label="Active=Home|Call|Schedule|Settlement|MY (탭해서 전환) · Show Call Badge · Call Badge Count">
          <ManagerTabBar active={tab} onSelect={setTab} showCallBadge callBadgeCount="2" />
        </Row>
        <Row label="배지 없음">
          <ManagerTabBar active="call" />
        </Row>
      </Section>

      <Section title="StatusBadge" figma="Manager/StatusBadge 2110:2235">
        <Row label="Status 7종">
          <div className={styles.wrapRow}>
            {Object.keys(STATUS).map((key) => (
              <StatusBadge key={key} status={key} />
            ))}
          </div>
        </Row>
      </Section>

      <Section title="ChecklistItem" figma="Manager/ChecklistItem 2114:2238">
        <Row label="Checked / Unchecked (탭해서 전환) / Disabled">
          <ChecklistItem label="바닥 청소" checked={checked} onChange={setChecked} />
          <ChecklistItem label="바닥 청소" checked={false} onChange={() => {}} />
          <ChecklistItem label="바닥 청소" checked={false} disabled />
        </Row>
      </Section>

      <Section title="CameraStatusItem" figma="Manager/CameraStatusItem 2147:3005">
        <Row label="Status=OK / Issue">
          <CameraStatusItem status="ok" label="카메라" value="정상" />
          <CameraStatusItem status="issue" label="카메라" value="연결 안 됨" />
        </Row>
      </Section>

      <Section title="ListItem" figma="Manager/ListItem 2114:2239">
        <Row label="Label">
          <ListItem label="매니저 정보" onClick={() => {}} />
          <ListItem label="장비 관리" onClick={() => {}} />
        </Row>
      </Section>

      <Section title="ScheduleCard" figma="Manager/ScheduleCard 2112:2252">
        <Row label="기본">
          <ScheduleCard time="10:00 - 13:00" status="scheduled" customer="김○○ 고객 · 강남구 역삼동" service="아파트 기본청소" onClick={() => {}} />
        </Row>
      </Section>

      <Section title="JobCard" figma="Manager/JobCard 2112:2207">
        <Row label="CTA Label">
          <JobCard
            time="10:00"
            status="scheduled"
            title="강남구 역삼동 · 아파트 기본청소"
            rows={[
              { label: '예상 소요시간', value: '3시간' },
              { label: '예상 정산', value: '85,000원' },
            ]}
            ctaLabel="업무 상세보기"
          />
        </Row>
      </Section>

      <Section title="CallCard" figma="Manager/CallCard 2112:2226">
        <Row label="Expiry · Accept Label · Reject Label">
          <CallCard
            expiry="4:32 후 만료"
            title="9월 18일 목요일 오후 2:00"
            location="서울 강남구 역삼동 · 아파트 기본청소"
            pay="85,000원"
            eta="현재 위치 기준 이동 약 25분"
            note="특이사항: 반려묘 1마리"
            onAccept={() => {}}
            onReject={() => setSheetOpen(true)}
          />
        </Row>
      </Section>

      <Section title="Sheet" figma="Manager/Call/RejectSheet 2122:2395 (껍데기만)">
        <Row label="열기 (ESC · 배경 탭 · 핸들 탭으로 닫힘). CallCard 의 거절도 연다">
          <Button variant="secondary" onClick={() => setSheetOpen(true)}>
            시트 열기
          </Button>
        </Row>
        <Sheet open={sheetOpen} onClose={() => setSheetOpen(false)} title="이 청소 요청을 거절하시겠어요?">
          <p className="text-body-default">본문 영역. RadioOption 은 3-c-2 에서 추가된다.</p>
          <Button onClick={() => setSheetOpen(false)}>거절하기</Button>
        </Sheet>
      </Section>

      <Section title="화면 상태 — 로딩 · 빈 상태 · 오류 (같은 골격인지 나란히)" figma="EmptyState 만 Figma(Manager/Home/Empty·Call/Empty), 나머지는 코드에서 판단">
        <Row label="ScreenLoading (제목 + 카드 3)">
          <ScreenLoading />
        </Row>
        <Row label="EmptyState smile / call / calendar">
          <EmptyState icon="smile" title="오늘 예정된 업무가 없어요" description="지금은 쉬어가셔도 좋아요." />
          <EmptyState icon="call" title="대기 중인 청소 요청이 없어요" description="새로운 요청이 들어오면 알림으로 알려드릴게요." />
          <EmptyState icon="calendar" title="예정된 예약이 없어요" />
        </Row>
        <Row label="ErrorState — 기본 / NETWORK / 404(다시 시도 없음) / 서버 문구">
          {/* error 는 useAsync 가 주는 오류와 같은 모양({ code, status, message }). 문구는 describeError 가 고른다. */}
          {/* 404 는 onRetry 를 넘겨도 '다시 시도' 버튼이 숨는다. NETWORK 가 아니면 message 가 있을 때 그것이 설명 문구가 된다. */}
          <ErrorState onRetry={() => {}} />
          <ErrorState error={{ code: 'NETWORK', status: 0 }} onRetry={() => {}} />
          <ErrorState error={{ code: 'NOT_FOUND', status: 404, message: '삭제된 공지예요' }} onRetry={() => {}} />
          <ErrorState error={{ code: 'INVALID_STATE', status: 409, message: '끝난 작업이에요' }} onRetry={() => {}} />
        </Row>
        <Row label="ActionError (버튼 동작 실패 한 줄)">
          <ActionError error={{ message: '저장하지 못했어요. 다시 시도해주세요.' }} />
        </Row>
        <Row label="LoadingBar (상단 진행 막대 — 화면 전환 중)">
          {/* LoadingBar 의 CSS 는 position: fixed 라서 띠가 이 상자 안이 아니라 브라우저 화면 맨 위에 나타난다. */}
          <div className={styles.skeletonCard} style={{ position: 'relative', height: 24 }}>
            <LoadingBar active />
          </div>
        </Row>
        <Row label="Toast">
          {/* 토스트는 두 앱 RouteShell 의 ToastHost 가 그린다. 갤러리 라우트도 RouteShell 아래라 여기서 누르면 뜬다. */}
          <Button variant="secondary" onClick={() => showToast('저장했어요')}>
            토스트 띄우기
          </Button>
        </Row>
      </Section>

      <Section title="Card · Dot · ProgressBar · SegmentedControl · SettlementCard" figma="Card tone 은 화면들의 카드 배경 묶음 (코드에서 판단)">
        <Row label="Card tone: default / live / brand / subtle / muted / danger / warning / warningMuted">
          {['default', 'live', 'brand', 'subtle', 'muted', 'danger', 'warning', 'warningMuted'].map((tone) => (
            <Card key={tone} tone={tone} gap="xs">
              <span className="text-title-small">{tone}</span>
              <span className="text-body-s">카드 본문</span>
            </Card>
          ))}
        </Row>
        <Row label="Dot live / success">
          <Dot tone="live" />
          <Dot tone="success" />
        </Row>
        <Row label="ProgressBar 3/8">
          <ProgressBar value={3} max={8} label="청소 체크리스트" />
        </Row>
        <Row label="SegmentedControl">
          <SegmentedControl options={[{ key: 'today', label: '오늘' }, { key: 'week', label: '이번 주' }, { key: 'calendar', label: '달력' }]} value="today" ariaLabel="기간" />
        </Row>
        <Row label="SettlementCard complete / settlementPending">
          <SettlementCard date="9월 14일" status="complete" title="강남구 기본청소" amount="120,000원" onClick={() => {}} />
          <SettlementCard date="9월 17일" status="settlementPending" title="해운대구 입주청소" amount="185,000원" />
        </Row>
      </Section>

      <Section title="ErrorState" figma="없음 (코드에서 판단 — api 실패 시. ScreenError 가 화면 전체에 씀)">
        <Row label="기본 + 다시 시도">
          <ErrorState onRetry={() => {}} />
        </Row>
      </Section>
      <Section title="Skeleton" figma="없음 (코드에서 판단)">
        <Row label="카드 로딩 예시">
          <div className={styles.skeletonCard}>
            <Skeleton width={80} height={28} radius="pill" />
            <Skeleton height={22} />
            <Skeleton width="60%" height={16} />
          </div>
        </Row>
      </Section>
    </>
  );
}

// NoticeChip 줄에서 아이콘 이름(NOTICE_ICONS 의 키)마다 붙여 보여 줄 한국어 이름표. 이 파일에서만 쓴다.
const NOTICE_LABELS = {
  pet: '반려동물', baby: '아기', smoke: '흡연', lock: '잠금', stairs: '계단', elevator: '엘리베이터', tools: '공구', warning: '주의', dots: '기타',
  box: '상자', building: '건물', parking: '주차', droplet: '물', clock: '시간', shield: '보안', desk: '책상', server: '서버', ac: '에어컨',
};

// 아래 *Demo 들은 제어 컴포넌트(값을 부모가 들고 있는 컴포넌트)를 직접 눌러 보도록 자기 상태를 가진 작은 견본이다.

// Checkbox 굵은 글씨(전체 동의) 를 눌러 켜고 끈다.
function AgreeDemo() {
  const [checked, setChecked] = useState(false);
  return <Checkbox label="전체 동의하기" checked={checked} bold onChange={setChecked} />;
}

// PhoneField 에 입력해 본다. 한 글자라도 쓰면 verifyActive 가 켜져 인증 버튼이 brand 채움 모양으로 바뀐다.
function PhoneDemo() {
  const [value, setValue] = useState('');
  return <PhoneField label="휴대폰 번호" value={value} onChange={(e) => setValue(e.target.value)} verify verifyActive={value.length > 0} onVerify={() => {}} />;
}

// UnderlineTabs 3개(예정·완료·취소) 중 누른 탭이 활성이 된다.
function TabsDemo() {
  const [value, setValue] = useState('upcoming');
  return <UnderlineTabs options={[{ key: 'upcoming', label: '예정 2' }, { key: 'done', label: '완료' }, { key: 'cancelled', label: '취소' }]} value={value} onChange={setValue} ariaLabel="예약 상태" />;
}

// Toggle On(눌러서 전환 가능) 과 Off(고정) 를 나란히 둔다.
function ToggleDemo() {
  const [on, setOn] = useState(true);
  return (
    <>
      <Toggle checked={on} onChange={setOn} label="푸시 알림" />
      <Toggle checked={false} onChange={() => {}} label="마케팅 정보 수신" />
    </>
  );
}

// Calendar + TimeSlotGrid 를 Figma 예시 값(2025년 9월 16일, 11:00)으로 연다.
function DateTimeDemo() {
  const [date, setDate] = useState('2025-09-16'); // 'YYYY-MM-DD' 날짜 키
  const [time, setTime] = useState('11:00');
  // new Date(2025, 8, 1) 의 월은 0부터 세서 8 = 9월이다.
  // isDisabled 를 넘기지 않으면 기본 규칙이 오늘 이전 날짜도 막는다. 여기서는 토·일만 막아 지난 날짜인 예시도 고를 수 있다.
  return (
    <>
      <Calendar value={date} onChange={setDate} initialMonth={new Date(2025, 8, 1)} isDisabled={(d) => d.getDay() === 0 || d.getDay() === 6} />
      <TimeSlotGrid slots={{ am: ['09:00', '10:00', '11:00', '12:00'], pm: ['13:00', '14:00', '15:00', '16:00', '17:00', '18:00'] }} value={time} onChange={setTime} />
    </>
  );
}

// FaqItem 두 개. 한 번에 하나만 펼친다.
function FaqDemo() {
  // 펼친 항목의 순번 (처음엔 0 = 첫 항목). -1 이면 모두 접힘. 펼친 항목을 다시 누르면 -1 로 접는다.
  const [open, setOpen] = useState(0);
  const items = [{ q: '예약을 변경할 수 있나요?', a: '내 예약 탭에서 예약 변경을 누르면 날짜와 시간을 바꿀 수 있어요.' }, { q: '실시간 영상은 어디서 보나요?', a: '청소가 시작되면 내 예약 카드에 실시간 영상 보기 버튼이 나타나요.' }];
  return (
    <div style={{ width: '100%' }}>
      {items.map((it, i) => (
        <FaqItem key={it.q} question={it.q} answer={it.a} open={open === i} onToggle={() => setOpen(open === i ? -1 : i)} />
      ))}
    </div>
  );
}

// 고객 앱 컴포넌트 묶음 (StatusBar ~ GiftBanner) 과 라이브 화면 컴포넌트 (LiveBadge ~ Live 컨트롤).
function CustomerGallery() {
  const [tab, setTab] = useState('home'); // TabBar 에서 누른 탭
  const [radio, setRadio] = useState('a'); // RadioOption 두 개 중 고른 것
  const [notice, setNotice] = useState({ pet: true }); // NoticeChip: { 아이콘 이름: 켜짐 여부 }
  const [step, setStep] = useState(3); // StepProgress 단계 (이전·다음 버튼이 1~8 안에서 움직인다)
  return (
    <>
      <Section title="StatusBar" figma="StatusBar 2009:24 — OS 영역, 높이만 확보 (시계·배터리 안 그림)">
        <Row label="44px (safe-area 가 더 크면 그만큼)">
          <div className={styles.outline}>
            <StatusBar />
          </div>
        </Row>
      </Section>

      <Section title="TabBar (고객용)" figma="TabBar 2045:1714">
        <Row label="Active=Home|Repair|Reservations|Settings (탭해서 전환)">
          <TabBar active={tab} onSelect={setTab} />
        </Row>
      </Section>

      <Section title="RadioOption" figma="RadioOption 2060:1750">
        <Row label="State=Selected / Default (탭해서 전환)">
          {/* RadioOption 은 role="radio" 한 칸이라 묶음(role="radiogroup")과 이름은 부르는 쪽이 감싸서 준다. */}
          <div role="radiogroup" aria-label="예시" className={styles.stack}>
            <RadioOption label="거리가 너무 멀어요" selected={radio === 'a'} onSelect={() => setRadio('a')} />
            <RadioOption label="시간이 맞지 않아요" selected={radio === 'b'} onSelect={() => setRadio('b')} />
          </div>
        </Row>
      </Section>

      <Section title="Chip" figma="Chip 2060:1801 (읽기 전용)">
        <Row label="라벨 길이에 따라 폭 자동">
          <div className={styles.wrapRow}>
            <Chip>라벨</Chip>
            <Chip>주 1회</Chip>
            <Chip>아파트 기본청소</Chip>
          </div>
        </Row>
      </Section>

      <Section title="NoticeChip" figma="NoticeChip 2031:583 — State=Default|Selected + Icon(INSTANCE_SWAP)">
        <Row label="NoticeIcon 18개 전부 (탭해서 전환)">
          {/* NoticeChip 은 누르면 onToggle(반대값) 만 부른다. 그 값을 notice 객체의 해당 아이콘 키에 넣어 켜고 끈다. */}
          <div className={styles.grid2}>
            {Object.keys(NOTICE_ICONS).map((name) => (
              <NoticeChip
                key={name}
                icon={name}
                label={NOTICE_LABELS[name]}
                selected={Boolean(notice[name])}
                onToggle={(v) => setNotice((prev) => ({ ...prev, [name]: v }))}
              />
            ))}
          </div>
        </Row>
      </Section>

      <Section title="StepProgress" figma="StepProgress 2081:1907 — Step=1~8">
        <Row label={`Step=${step} (버튼으로 이동)`}>
          <StepProgress step={step} />
          <div className={styles.inline}>
            <Button variant="secondary" onClick={() => setStep((s) => Math.max(1, s - 1))}>
              이전
            </Button>
            <Button variant="secondary" onClick={() => setStep((s) => Math.min(8, s + 1))}>
              다음
            </Button>
          </div>
        </Row>
      </Section>

      <Section title="ServiceCategoryList" figma="ServiceCategoryList 2024:257 (3D 래스터 WebP 1x/2x/3x)">
        <Row label="카테고리 4종">
          {/* bleed: 음수 좌우 여백으로 페이지 안쪽 여백을 무시하고 폭을 꽉 채워 보여 준다 (GiftBanner 첫 줄도 같다). */}
          <div className={styles.bleed}>
            <ServiceCategoryList onSelect={() => {}} />
          </div>
        </Row>
      </Section>

      <Section title="Checkbox (원형)" figma="Auth/TermsConsent › Checkbox 2075:1859 (Checked) · 2075:1862 (Unchecked)">
        <Row label="Unchecked / Checked (Body/S)">
          <Checkbox label="(필수) 서비스 이용약관" checked={false} onChange={() => {}} />
          <Checkbox label="(필수) 서비스 이용약관" checked onChange={() => {}} />
        </Row>
        <Row label="bold (AgreeAllRow, Title/Small)">
          <Checkbox label="전체 동의하기" checked bold onChange={() => {}} />
        </Row>
        <Row label="Disabled">
          <Checkbox label="(선택) 마케팅 정보 수신 동의" checked={false} disabled />
        </Row>
        <Row label="reverse + muted + labelStyle=labelM (ProfileHousing › CheckRow 2064:1814)">
          <Checkbox label="기억이 안나요" checked={false} onChange={() => {}} reverse muted labelStyle="labelM" />
        </Row>
        <Row label="제어 (탭해 보기)">
          <AgreeDemo />
        </Row>
      </Section>
      <Section title="TermRow" figma="Auth/TermsConsent › TermRow 2063:1807 (Checkbox + 보기>)">
        <Row label="한 줄 / 두 줄 (Figma 줄바꿈·공백 그대로)">
          <TermRow label="(필수) 개인정보 수집 및 이용 동의" checked onChange={() => {}} onView={() => {}} />
          <TermRow label={'(선택) 서비스 개선을 위한 \n          사용자 경험 수집 동의'} checked={false} onChange={() => {}} onView={() => {}} />
        </Row>
      </Section>
      <Section title="StepDots" figma="Auth/TermsConsent › ProgressWrap 2063:1800 — 점 4개 + 01/04 카운터">
        <Row label="step 1~4">
          <StepDots step={1} />
          <StepDots step={2} />
          <StepDots step={3} />
          <StepDots step={4} />
        </Row>
      </Section>
      <Section title="PhoneField" figma="Auth/LoginPhone › Field/Phone 2062:1814 · Auth/ProfileInfo-* › Field/PhoneVerify 2061:1822">
        <Row label="Field/Phone (labelStyle=heading, 인증 버튼 없음)">
          <PhoneField label="전화번호를 입력해주세요" labelStyle="heading" value="" onChange={() => {}} />
        </Row>
        <Row label="Field/PhoneVerify (ProfileInfo-Phone 모양)">
          <PhoneField label="휴대폰 번호" value="" onChange={() => {}} verify />
        </Row>
        <Row label="Field/PhoneVerify verifyActive (ProfileInfo-Social 모양)">
          <PhoneField label="휴대폰 번호" value="" onChange={() => {}} verify verifyActive />
        </Row>
        <Row label="Disabled">
          <PhoneField label="휴대폰 번호" value="" verify disabled />
        </Row>
        <Row label="제어 (입력해 보기 — 번호 가리기 토글 포함)">
          <PhoneDemo />
        </Row>
      </Section>
      <Section title="NoteCard" figma="Auth/TermsConsent › PrivacyNoteCard 2063:1812 (3D 아이콘 + 안내문)">
        <Row label="Default">
          <NoteCard icon={ASSETS.termsNoteIcon}>{'수집된 개인정보는 안전하게 보호되며, \n서비스 제공 목적 외로 사용되지 않습니다.'}</NoteCard>
        </Row>
        <Row label="tone=brand (ProfileHousing › InfoBox 2064:1813)">
          <NoteCard tone="brand" icon={ASSETS.housingInfoIcon}>{'입주청소를 원하시는 경우 \n이사 가는 부동산의 형태를 선택해주세요'}</NoteCard>
        </Row>
      </Section>
      <Section title="SearchField" figma="Auth/ProfileAddress › Field/RoadAddress 2062:1802 (돋보기 + Label/M)">
        <Row label="Default / Disabled">
          <SearchField label="도로명주소" placeholder="도로명주소를 입력해주세요." value="" onChange={() => {}} />
          <SearchField label="도로명주소" placeholder="도로명주소를 입력해주세요." disabled />
        </Row>
      </Section>
      <Section title="ImageOption" figma="Auth/ProfileHousing › HousingTypeItem 2064:1804 (sm) · PetItem 2064:1823 (lg) — Selected 는 코드에서 정함">
        <Row label="sm: Default ×3 + Selected">
          <ImageOption image={ASSETS.housingApartment} label="아파트·빌라" selected={false} />
          <ImageOption image={ASSETS.housingStudio} label="원룸·오피스텔" selected={false} />
          <ImageOption image={ASSETS.housingHouse} label="단독주택" selected={false} />
          <ImageOption image={ASSETS.housingOther} label={'기타\n(사무실 등)'} selected />
        </Row>
        <Row label="lg: Default + Selected / Disabled">
          <ImageOption image={ASSETS.petYes} label="있음" selected={false} size="lg" />
          <ImageOption image={ASSETS.petNo} label="없음" selected size="lg" />
          <ImageOption image={ASSETS.petNo} label="없음" selected={false} size="lg" disabled />
        </Row>
      </Section>
      <Section title="UnderlineTabs" figma="Reservations › SegmentTabs 2047:1695 — Active/Default">
        <Row label="예정 2 활성">
          <TabsDemo />
        </Row>
      </Section>
      <Section title="ReservationCard" figma="Reservations › ReservationCard 2047:1706 (방문 예정) · 2047:1727 (매니저 배정 중)">
        <Row label="status=scheduled">
          <ReservationCard title="가정/원룸 청소" status="scheduled" rows={[{ label: '일정', value: '2026년 11월 1일 (일) 오전 9시' }, { label: '주소', value: '부산광역시 중앙대로 2127-23' }, { label: '매니저', value: '김미영 매니저' }]} />
        </Row>
        <Row label="status=inProgress (실시간 영상 보기) / done / cancelled — 코드에서 정함">
          <ReservationCard title="가정/원룸 청소" status="inProgress" rows={[{ label: '일정', value: '오늘 오전 9시' }, { label: '매니저', value: '김미영 매니저' }]} onWatch={() => {}} />
          <ReservationCard title="가전청소" status="done" rows={[{ label: '일정', value: '2026년 8월 3일 (월) 오후 2시' }]} />
          <ReservationCard title="사무실 청소" status="cancelled" rows={[{ label: '일정', value: '2026년 7월 20일 (월) 오전 10시' }]} />
        </Row>
        <Row label="status=assigning">
          <ReservationCard title="가전청소" status="assigning" rows={[{ label: '일정', value: '2026년 11월 14일 (토) 오후 2시' }, { label: '주소', value: '부산광역시 중앙대로 2127-23' }, { label: '매니저', value: '배정 후 알려드려요' }]} />
        </Row>
      </Section>
      <Section title="Toggle · MenuRow · MenuGroup · ProfileCard" figma="Settings 2048:1709 — Toggle/On·Off, MenuRow(chevron·value·toggle), ProfileCard">
        <Row label="Toggle On / Off">
          <ToggleDemo />
        </Row>
        <Row label="ProfileCard (Avatar 교체필요)">
          <ProfileCard name="김하늘 님" phone="010-1234-5678" onClick={() => {}} />
        </Row>
        <Row label="MenuGroup + MenuRow 3종">
          <MenuGroup title="계정">
            <MenuRow label="내 정보 수정" onClick={() => {}} />
            <MenuRow label="푸시 알림" checked onToggle={() => {}} />
            <MenuRow label="버전 정보" value="v1.0.0" />
          </MenuGroup>
        </Row>
      </Section>
      <Section title="AddressResultList · FaqItem" figma="AddressSearch MapPreview 이미지에서 옮김 · FAQ 는 Figma 없음">
        <Row label="주소 검색 결과">
          <AddressResultList results={[{ id: '1', main: '서울특별시 강남구 테헤란로 123', sub: '(역삼동, ABC빌딩)' }, { id: '2', main: '서울특별시 강남구 테헤란로 123-1', sub: '(역삼동)' }]} />
        </Row>
        <Row label="FaqItem 열림/닫힘">
          <FaqDemo />
        </Row>
      </Section>
      <Section title="RepairCategoryList" figma="Repair › RepairCategoryGrid 2073:1771 (3D 래스터 8종)">
        <Row label="8개">
          <RepairCategoryList />
        </Row>
      </Section>
      <Section title="StepNavBar" figma="Booking/* › NavBar 2082:1835 (bar) · AddressSearch › SheetHeader 2082:1887 (sheet)">
        <Row label="bar step=1 / sheet step=4">
          <StepNavBar step={1} onBack={() => {}} />
          <StepNavBar step={4} variant="sheet" onBack={() => {}} />
        </Row>
      </Section>
      <Section title="SelectField" figma="Booking/Frequency › DateTimeField 2055:1737 (calendar) · Step2 › OptionSelectField 2056:1737 (chevron)">
        <Row label="placeholder / value">
          <SelectField placeholder="날짜/시간을 선택해주세요" icon="calendar" onClick={() => {}} />
          <SelectField placeholder="옵션을 선택해주세요" value="기본 청소 + 냉장고" onClick={() => {}} />
        </Row>
      </Section>
      <Section title="SearchField filled" figma="Booking/AddressSearch › SearchField 2057:1739">
        <Row label="filled (라벨 없음, placeholder 없음)">
          <SearchField filled ariaLabel="주소 검색" value="" onChange={() => {}} />
        </Row>
      </Section>
      <Section title="PromoBanner" figma="Booking/Frequency › Banner/RecurringPromo 2169:2259 · Office/Frequency › PromoBanner 2044:1665 (자리표시 문구는 쓰지 않는다)">
        <Row label="recurring (가정/원룸)">
          <PromoBanner />
        </Row>
        <Row label="benefits (Office/Appliance/MoveIn — 배너 이미지 없음)">
          <PromoBanner variant="benefits" />
        </Row>
      </Section>
      <Section title="Calendar · TimeSlotGrid" figma="Booking/DateTime › CalendarPanel 2058:1743 · TimePanel 2058:1742">
        <Row label="2025년 9월 16일 · 11:00 선택 (Figma 예시)">
          <DateTimeDemo />
        </Row>
      </Section>
      <Section title="OptionItem" figma="Booking/Options › OptionItem 2058:1812 (무료) · 2058:1819 (유료) — Selected 는 코드에서 정함">
        <Row label="무료 Default / Selected · 유료 Default / Selected / Disabled">
          <OptionItem image={ASSETS.bookingOptionIcons.home[0]} label="설거지" selected={false} />
          <OptionItem image={ASSETS.bookingOptionIcons.home[1]} label="세탁" selected />
          <OptionItem image={ASSETS.bookingOptionIcons.home[3]} label={'반려동물\n배변 청소'} price="+3,000원" selected={false} />
          <OptionItem image={ASSETS.bookingOptionIcons.home[4]} label={'냉장고 \n정리 및 청소'} price="+10,000원" selected />
          <OptionItem image={ASSETS.bookingOptionIcons.home[2]} label="침구교체" selected={false} disabled />
        </Row>
      </Section>
      <Section title="TextArea · TipCard" figma="Booking/NotesRequests › InputArea 2023:240 · TipCard 2023:245">
        <Row label="TextArea">
          <TextArea ariaLabel="요청사항" placeholder="여기에 작성해주세요" value="" onChange={() => {}} />
        </Row>
        <Row label="TipCard">
          <TipCard icon={ASSETS.requestTipIcon} title="이렇게 요청해보세요!">
            {'-특히 신경 써줬으면 하는 공간이나 가구가 있다면 알려주세요.\n-청소 전 확인이 필요한 사항이 있다면 미리 알려주세요.\n-기타 특별한 요청사항이 있다면 자세히 적어주세요.'}
          </TipCard>
        </Row>
      </Section>
      <Section title="SummaryItem" figma="Booking/Confirm › SummaryRow/* 2039:1657 …">
        <Row label="값 / 칩 / 요청사항 상자">
          <SummaryItem icon={ASSETS.confirmIcons.address} title="주소" value="부산광역시 중앙대로 2127-23" subValue="금정아파트 802동 203호" onEdit={() => {}} />
          <SummaryItem icon={ASSETS.confirmIcons.options} title="선택 옵션" onEdit={() => {}}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
              <Chip>반려동물 배변 청소</Chip>
              <Chip>세탁</Chip>
            </div>
          </SummaryItem>
          <SummaryItem icon={ASSETS.confirmIcons.requests} title="요청사항" note="창문 꼼꼼히 부탁드려요" onEdit={() => {}} />
        </Row>
      </Section>
      <Section title="BookingInfoCard" figma="Booking/Complete › BookingInfoCard 2098:2191">
        <Row label="행 4개">
          <BookingInfoCard rows={[{ icon: 'calendar', label: '예약일', value: '{예약일자}' }, { icon: 'clock', label: '예약시간', value: '{예약시간}' }, { icon: 'pin', label: '서비스 주소', value: '{주소}' }, { icon: 'person', label: '담당 클리너', value: '{클리너명}' }]} />
        </Row>
      </Section>
      <Section title="SlideCounter · CarouselDots" figma="Home/Standard-A › SlideCounter 84:233 · CarouselDots 2103:2212">
        <Row label="02/02">
          <SlideCounter current={2} total={2} />
        </Row>
        <Row label="index 0 / 1">
          <CarouselDots index={0} count={2} />
          <CarouselDots index={1} count={2} />
        </Row>
      </Section>
      <Section title="BackButton" figma="Auth/* › Button/Back 2075:1809 (떠 있는 24px 화살표, 터치 44)">
        <Row label="Default">
          <BackButton onClick={() => {}} />
        </Row>
      </Section>
      <Section title="GiftBanner" figma="GiftBanner 2024:264 (3D 래스터 WebP)">
        <Row label="393×160">
          <div className={styles.bleed}>
            <GiftBanner onCta={() => {}} />
          </div>
        </Row>
        <Row label="variant=repair (Repair 2024:328 오버라이드)">
          <GiftBanner variant="repair" onCta={() => {}} />
        </Row>
      </Section>

      <Section title="라이브 배지·칩" figma="Service/LivePortrait › LiveBadge 2167:2266 · RealtimePill 2167:2259 · ViewerChip 2167:2269">
        <Row label="LiveBadge / RealtimePill / ViewerChip (검정 위)">
          {/* ViewerChip 은 영상 위에 올라가는 칩이라 영상 배경색(--raw-video-bg) 상자 위에 놓고 본다. */}
          <LiveBadge />
          <RealtimePill />
          <span style={{ background: 'var(--raw-video-bg)', padding: 8, borderRadius: 8 }}>
            <ViewerChip count={1} />
          </span>
        </Row>
      </Section>
      <Section title="ManagerIdentity" figma="Service/LivePortrait › ManagerRow 2167:2254 (light) · LiveLandscape › ManagerIdentity 2165:2226 (onVideo)">
        <Row label="light + RealtimePill">
          <ManagerIdentity avatar={ASSETS.managerAvatar} name="김미영 매니저" description="경력 7년 · 정리정돈부터 꼼꼼한 생활청소까지" trailing={<RealtimePill />} />
        </Row>
        <Row label="onVideo">
          <div style={{ background: 'var(--raw-video-bg)', padding: 16, borderRadius: 8, width: '100%' }}>
            <ManagerIdentity variant="onVideo" avatar={ASSETS.managerAvatar} name="김미영 매니저" description="경력 7년 · 정리정돈부터 꼼꼼한 생활청소까지" />
          </div>
        </Row>
      </Section>
      <Section title="VideoSurface · CleaningStatusCard · IconButton" figma="Service/LivePortrait › VideoCard 2167:2262 · CleaningStatusCard 2167:2274 · Control/Fullscreen 2167:2281">
        <Row label="portrait (오버레이 포함)">
          {/* VideoSurface 는 position: relative 상자라 안의 absolute 오버레이가 영상 모서리 기준으로 놓인다. */}
          {/* 아래 인라인 좌표는 견본용으로 여기서만 쓴다. 실제 라이브 화면의 배치는 ServiceLivePortrait.module.css 에 있다. */}
          {/* ref 로 스트림을 붙이지 않았으므로 영상 자리는 VideoSurface 바탕색(검정)으로 보인다. */}
          <VideoSurface>
            <div style={{ position: 'absolute', top: 12, left: 12, display: 'flex', gap: 2 }}>
              <LiveBadge />
              <ViewerChip count={1} />
            </div>
            <div style={{ position: 'absolute', left: 12, bottom: 14 }}>
              <CleaningStatusCard title="거실 청소 중" description="깨끗한 공간을 만들어가고 있어요!" />
            </div>
            <div style={{ position: 'absolute', right: 12, bottom: 80 }}>
              <IconButton tone="onVideo" icon={<Icons.FullscreenIcon size={22} />} label="전체 화면" />
            </div>
          </VideoSurface>
        </Row>
        <Row label="IconButton dark: 뒤로 / 설정">
          <IconButton icon={<Icons.LiveBackIcon />} label="뒤로가기" />
          <IconButton icon={<Icons.SettingsIcon />} label="설정" />
        </Row>
      </Section>
      <Section title="ChecklistProgressCard · ChecklistRequests" figma="PDF: ProgressOverlay 6003:2260 카드만 최신 화면에 추가">
        <Row label="9개 중 4개 · 요청 없음">
          <ChecklistProgressCard
            copy={{ title: '청소 체크리스트', basis: '매니저 완료 체크 기준', requestsTitle: '고객 요청사항', requestsStatus: '완료 여부 미확인', requestsEmpty: '등록된 요청사항 정보가 제공되지 않았습니다.', requestsNote: '청소 진행률과 별도로 완료 상태를 표시합니다.' }}
            checklist={{ done: 4, total: 9, percent: 44, requests: [], groups: [
              { group: '거실', done: 3, total: 3, items: ['바닥 청소', '먼지 제거', '테이블 정리'].map((label) => ({ id: label, label, done: true })) },
              { group: '주방', done: 1, total: 3, items: ['싱크대', '가스레인지', '조리대'].map((label, index) => ({ id: label, label, done: index === 0 })) },
              { group: '욕실', done: 0, total: 3, items: ['세면대', '변기', '바닥'].map((label) => ({ id: label, label, done: false })) },
            ] }}
          />
        </Row>
        <Row label="다시보기 요청사항">
          <ChecklistRequests copy={{ requestsTitle: '고객 요청사항', requestsStatus: '완료 여부 미확인', requestsEmpty: '등록된 요청사항 정보가 제공되지 않았습니다.', requestsNote: '청소 진행률과 별도로 완료 상태를 표시합니다.' }} requests={['반려동물이 있어요', '문을 닫아주세요']} />
        </Row>
        <Row label="다시보기 구간 · 밝은 배경">
          <LiveProgress tone="onLight" ratio={319 / 630} timeLabel="5:19 / 10:30" chapterTitle="주방 · 싱크대" chapters={[
            { start: 0, end: 165 / 630, title: '거실 · 바닥 청소' },
            { start: 165 / 630, end: 190 / 630, title: '거실 · 먼지 제거' },
            { start: 190 / 630, end: 245 / 630, title: '거실 · 테이블 정리' },
            { start: 245 / 630, end: 320 / 630, title: '주방 · 싱크대' },
            { start: 320 / 630, end: 1, title: '' },
          ]} />
        </Row>
      </Section>
      <Section title="ReassuranceBanner · LiveProgress" figma="Service/LivePortrait › ReassuranceBanner 2167:2299 (Mascot 에셋 필요) · LiveLandscape › Progress 2165:2256~2259">
        <Row label="ReassuranceBanner">
          <ReassuranceBanner title="안심하고 지켜보세요!" description="항상 최선을 다해 청소하고 있어요 💙" />
        </Row>
        <Row label="LiveProgress 5:20 / 10:30">
          {/* ratio 410 / 804: Figma LiveLandscape 예시에서 트랙 폭 804 중 410 만큼 채운 진행 비율(약 0.51)이다. */}
          <div style={{ background: 'var(--raw-video-bg)', padding: 16, borderRadius: 8, width: '100%' }}>
            <LiveProgress ratio={410 / 804} timeLabel="5:20 / 10:30" />
          </div>
        </Row>
        {/* 세로 녹화본 화면처럼 흰 배경 위에 둔다. Figma 에 없는 tone 이다 (코드에서 정함) */}
        <Row label="LiveProgress tone=onLight (밝은 배경 · 코드에서 정함)">
          <LiveProgress tone="onLight" ratio={410 / 804} timeLabel="5:20 / 10:30" onSeek={() => {}} />
        </Row>
      </Section>
      <Section title="Live 컨트롤 — 오버레이형" figma="Live/CircleControl 2160:2218 · Live/PillControl 2160:2227 (영상 위, 반투명 검정)">
        <Row label="CircleControl Type=Pause|Back15|Forward15 · PillControl Type=Capture|Exit">
          {/* 오버레이형은 영상 위에 뜨는 반투명 배경이라 영상 대신 회색 상자(videoMock) 위에 놓아 보인다. */}
          <div className={styles.videoMock}>
            <div className={styles.wrapRow}>
              <LiveCircleControl type="back15" />
              <LiveCircleControl type="pause" />
              <LiveCircleControl type="forward15" />
            </div>
            <div className={styles.wrapRow}>
              <LivePillControl type="capture" />
              <LivePillControl type="exit" />
            </div>
          </div>
        </Row>
        {/* 세로 녹화본 화면처럼 흰 배경 위에 둔다. 모양은 같고 키보드 포커스 테두리만 파랑이다 (Tab 키로 확인) */}
        <Row label="CircleControl tone=onLight (밝은 배경 · 코드에서 정함)">
          <div className={styles.wrapRow}>
            <LiveCircleControl type="back15" tone="onLight" />
            <LiveCircleControl type="play" tone="onLight" />
            <LiveCircleControl type="forward15" tone="onLight" />
          </div>
        </Row>
      </Section>

      <Section title="Live 컨트롤 — 하단바형" figma="Live/LabeledControl 2164:2257 (흰 배경, 라벨 포함)">
        <Row label="Type=Capture|Exit|Mute">
          <div className={styles.spread}>
            <LiveLabeledControl type="capture" />
            <LiveLabeledControl type="exit" />
            <LiveLabeledControl type="mute" />
          </div>
        </Row>
      </Section>
    </>
  );
}

// icons/index.js 가 내보내는 아이콘 전부. 개수는 내보낸 수로 자동 계산한다.
// 아이콘 색은 currentColor(부모 글자색)라 iconBox 의 흰 글자색(--color-text-inverse)을 따라 흰색으로 그려진다.
// 예외: 색을 토큰으로 고정한 부분(예: CompleteCheckIcon·VerifiedIcon 의 체크 표시)은 글자색을 따르지 않는다.
function IconGallery() {
  // import * 로 받은 모듈 객체의 키는 이름 순(알파벳)으로 정렬돼 나온다. 마우스를 올리면 title 로 이름이 보인다.
  const names = Object.keys(Icons);
  return (
    <Section title="Icons" figma={`${names.length}개 · currentColor`}>
      <Row label="ChevronRight / Spinner (Button)">
        <div className={styles.wrapRow}>
          <div className={styles.iconBox}>
            <ChevronRight />
          </div>
          <div className={styles.iconBox}>
            <Spinner />
          </div>
        </div>
      </Row>
      <Row label="전체 (배경 위 흰색)">
        <div className={styles.wrapRow}>
          {names.map((name) => {
            const Icon = Icons[name];
            return (
              <div key={name} className={styles.iconBox} title={name}>
                <Icon />
              </div>
            );
          })}
        </div>
      </Row>
    </Section>
  );
}

// 고객 앱 수리 예약 화면들(screens/repair/booking)이 쓰는 컴포넌트를 한 번씩 그린다. 상태는 눌러 보기용.
/** 수리 예약 플로우 (Figma rGCjx3zWMBk18fXgNZ9UYX) — variant 전부 */
function RepairGallery() {
  const [appliance, setAppliance] = useState('fridge'); // OptionGroup bordered 에서 고른 가전
  const [chips, setChips] = useState(['parking']); // ToggleChip 에서 켜진 칩 키 목록
  const [date, setDate] = useState('2026-09-23'); // WeekCalendar 에서 고른 날짜 'YYYY-MM-DD'
  const [time, setTime] = useState('11:00');
  // 칩 키가 목록에 있으면 빼고, 없으면 더한다 (눌러서 켜고 끄기).
  const toggle = (k) => setChips((c) => (c.includes(k) ? c.filter((x) => x !== k) : [...c, k]));
  return (
    <>
      <Section title="StepQuestion" figma="질문 1:502">
        <StepQuestion service="가전수리 · 냉장고" title="어떤 증상이 있나요?" description="가장 가까운 증상 한 가지를 선택해주세요." />
      </Section>
      <Section title="OptionGroup" figma="가전 종류 1:506 (bordered) · 고장 증상 1:528 (plain)">
        <Row label="bordered"><OptionGroup ariaLabel="가전 종류" options={[{ key: 'fridge', label: '냉장고' }, { key: 'washer', label: '세탁기 · 건조기' }]} value={appliance} onChange={setAppliance} /></Row>
        <Row label="plain"><OptionGroup bordered={false} ariaLabel="고장 증상" options={[{ key: 'a', label: '냉장이 잘 안 돼요' }, { key: 'b', label: '기타 증상이에요' }]} value="a" /></Row>
      </Section>
      <Section title="SelectionSummary" figma="요약 카드 1:468">
        <SelectionSummary badge="3/4 단계" rows={[{ label: '서비스', value: '가전수리' }, { label: '가전', value: '냉장고' }, { label: '사진', value: '1장 첨부됨' }]} />
      </Section>
      <Section title="PhotoPicker" figma="사진 첨부 1:483">
        <PhotoPicker photos={[]} helper="제품 전체와 문제가 보이는 부분을 함께 찍으면 더 정확히 안내할 수 있어요." />
      </Section>
      <Section title="InfoNote" figma="방문 안내 1:450 · 선택 일정 요약 1:419 · 안전 안내 1:351 · 비용 안내 1:321">
        <Row label="brand"><InfoNote glyph="ⓘ">정확한 방문을 위해 동·호수와 공동현관 정보를 확인해주세요.</InfoNote></Row>
        <Row label="brand inline"><InfoNote glyph="📅" layout="inline">9월 23일(수) 오전 11시</InfoNote></Row>
        <Row label="warning"><InfoNote tone="warning" glyph="!">냉장고 주변의 물건을 미리 정리하면 점검이 더 빠르게 진행돼요.</InfoNote></Row>
        <Row label="warning titled"><InfoNote tone="warning" title="수리비는 현장 진단 후 확정돼요">출장 및 진단 후 필요한 부품과 작업 범위를 안내드리며, 고객님 동의 후 수리를 진행해요.</InfoNote></Row>
      </Section>
      <Section title="SelectedAddressCard" figma="주소 카드 1:439">
        <SelectedAddressCard roadAddress="서울특별시 강남구 테헤란로 123" detailAddress="604동 1002호" />
      </Section>
      <Section title="ToggleChip" figma="상황 선택 1:342 (Default · Selected)">
        <Row label="chips">
          {[['parking', '주차 가능해요'], ['elevator', '엘리베이터 있어요'], ['pet', '반려동물이 있어요'], ['intercom', '공동현관 호출 필요']].map(([k, l]) => (
            <ToggleChip key={k} label={l} selected={chips.includes(k)} onToggle={() => toggle(k)} />
          ))}
        </Row>
      </Section>
      <Section title="WeekCalendar · TimeSlotGrid flat" figma="달력 1:370 · 시간 선택 1:398">
        {/* isDisabled={() => false}: 기본 규칙(오늘 이전·토·일 막기)을 끄고 모든 날짜를 눌러 볼 수 있게 한다. */}
        <WeekCalendar value={date} onChange={setDate} isDisabled={() => false} />
        <TimeSlotGrid layout="flat" slots={{ am: ['09:00', '10:00', '11:00'], pm: ['13:00', '14:00', '15:00', '16:00', '17:00', '18:00'] }} value={time} onChange={setTime} />
      </Section>
      <Section title="ConfirmSummaryRow" figma="예약 요약 항목 1:286">
        <ConfirmSummaryRow icon="🧰" label="서비스" value="가전수리 · 냉장고" sub="냉장이 잘 안 돼요" onEdit={() => {}} />
      </Section>
      <Section title="SuccessHeader · StackedInfoCard" figma="완료 피드백 1:239 · 예약 정보 카드 1:247">
        <SuccessHeader title="예약이 완료되었어요!" description="방문 일정과 주소를 확인해 주세요. 기사 배정 완료 시 알림으로 바로 알려드릴게요." />
        <StackedInfoCard rows={[{ icon: 'calendar', label: '방문 일정', value: '9월 23일(수)' }, { icon: 'clock', label: '방문 시간', value: '오전 11시' }, { icon: 'pin', label: '서비스 주소', value: '테헤란로 123 · 604동 1002호' }, { icon: 'person', label: '기사 배정', children: <StatusBadge status="new" label="신규" /> }]} />
      </Section>
    </>
  );
}

// 갤러리 페이지 본체. 위의 묶음들을 순서대로 한 페이지에 늘어놓고, 마지막에 텍스트 스타일 견본을 둔다.
export default function Gallery() {
  return (
    <main className={styles.page}>
      <h1 className="text-heading-m">컴포넌트 갤러리</h1>
      <p className={`text-body-s ${styles.intro}`}>
        Figma variant 를 전부 나열한다. 화면 폭은 393px 기준.
      </p>
      <ButtonGallery />
      <TextFieldGallery />
      <ManagerGallery />
      <CustomerGallery />
      <RepairGallery />
      <IconGallery />
      <Section title="Typography">
        <TypographySpecimen />
      </Section>
    </main>
  );
}
