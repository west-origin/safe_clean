import { TabBar } from '@ansimclean/ui';
import { useCustomerTabs } from './useCustomerTabs.js';

/** 고객 앱 공통 푸터: 프로필과 동일한 탭 서식과 화면 이동을 공유한다. */
export default function CustomerFooter({ active }) {
  const selectTab = useCustomerTabs();
  return <TabBar active={active} onSelect={selectTab} />;
}
