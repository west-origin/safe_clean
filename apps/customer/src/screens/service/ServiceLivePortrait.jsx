// 실시간 화면과 기존 녹화본 재생 화면을 구분한다.
import { useSearchParams } from 'react-router';
import LiveWatchScreen from './LiveWatchScreen.jsx';
import ServiceReplayPortrait from './ServiceReplayPortrait.jsx';

export default function ServiceLivePortrait() {
  const [params] = useSearchParams();
  return params.get('replay') === '1' ? <ServiceReplayPortrait /> : <LiveWatchScreen />;
}
