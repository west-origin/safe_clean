import CustomerTabHomeIcon from '../../icons/CustomerTabHomeIcon.jsx';
import CalendarIcon from '../../icons/CalendarIcon.jsx';
import CustomerTabInquiryIcon from '../../icons/CustomerTabInquiryIcon.jsx';
import CustomerTabProfileIcon from '../../icons/CustomerTabProfileIcon.jsx';

// 사용자 첨부 홈 시안(2026-10-29). 기존 수리 화면은 /repair 에 유지한다.
export const CUSTOMER_TABS = [
  { key: 'home', label: '홈', Icon: CustomerTabHomeIcon },
  { key: 'reservations', label: '내 예약', Icon: CalendarIcon },
  { key: 'inquiry', label: '문의', Icon: CustomerTabInquiryIcon },
  { key: 'settings', label: '프로필', Icon: CustomerTabProfileIcon },
];
