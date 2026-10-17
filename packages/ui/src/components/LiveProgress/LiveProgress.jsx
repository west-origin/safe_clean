// Figma: Service/LiveLandscape › Progress/Track (2165:2256) 804×6 흰 32% · Progress/Fill 빨강(--raw-danger) · Progress/Knob 16 흰색 · "5:20 / 10:30" Label/S 흰색 (트랙 아래 18)
// 녹화본 탐색용 진행 막대 (onSeek 연결은 단계 5 에서 끝났다). 실시간 시청 중에는 두 라이브 화면 모두 이 막대를 그리지 않는다.
// 고객 앱 ServiceLivePortrait·ServiceLiveLandscape 가 녹화본(replay)일 때 onSeek 과 함께 쓴다.
// tone: 영상 위(가로 화면, 기본 onVideo)면 흰색, 밝은 화면 배경 위(세로 화면, onLight)면 토큰 색 — 흰 손잡이·시간 글자가 흰 배경에 묻히지 않게 (코드에서 정함).
// onSeek 이 있으면 누르거나 키보드로 옮기는 slider, 없으면 보기만 하는 progressbar 가 된다 (예: 개발용 갤러리).
import { cx } from '../../utils/cx.js';
import styles from './LiveProgress.module.css';

/**
 * @param {object} props
 * @param {number} props.ratio 진행 비율 0~1
 * @param {string} props.timeLabel (예: "5:20 / 10:30")
 * @param {(ratio: number) => void} [props.onSeek] 옮길 위치의 비율. 키보드는 0~1 로 잘라 넘기고, 탭은 자르지 않아 0~1 을 살짝 벗어날 수 있다
 * @param {'onVideo'|'onLight'} [props.tone='onVideo'] onVideo: 영상 위(흰 손잡이·글자, Figma) · onLight: 밝은 화면 배경 위(토큰 색, 코드에서 정함)
 * @param {string} [props.className]
 * @param {{start:number,end:number,title:string}[]} [props.chapters] 다시보기 구간의 시작·끝 비율
 * @param {string} [props.chapterTitle] 현재 구간 이름. 시간 글자와 접근성 문구에 함께 표시한다.
 * 트랙·채움·손잡이·시간 글자를 그린다. ratio 가 0~1 밖이어도 그리기 전에 잘라서 쓴다.
 */
export default function LiveProgress({ ratio, timeLabel, onSeek, tone = 'onVideo', className, chapters = [], chapterTitle = '' }) {
  const pct = Math.max(0, Math.min(1, ratio)) * 100;
  const chaptered = chapters.length > 1;
  const label = chapterTitle ? `${timeLabel} · ${chapterTitle}` : timeLabel;
  // 트랙에서 누른 가로 위치를 트랙 폭에 대한 비율로 바꿔 onSeek 에 넘긴다.
  // 여기서는 0~1 로 자르지 않는다: 손잡이가 트랙 양 끝 밖으로 조금 나와 있어 0~1 을 살짝 벗어날 수 있고,
  // 고객 앱에서는 liveClient.seekRatio 가 0~1 로 자른다.
  const seek = (e) => {
    if (!onSeek) return;
    const rect = e.currentTarget.getBoundingClientRect();
    onSeek((e.clientX - rect.left) / rect.width);
  };
  // 키보드: ←/→ 5%, Home/End
  const onKeyDown = (e) => {
    if (!onSeek) return;
    const step = 0.05;
    const next = { ArrowLeft: ratio - step, ArrowRight: ratio + step, Home: 0, End: 1 }[e.key];
    if (next === undefined) return;
    e.preventDefault(); // 처리한 키만 브라우저 기본 동작(Home/End 페이지 스크롤 등)을 막는다
    onSeek(Math.max(0, Math.min(1, next)));
  };
  // 채움 폭과 손잡이 위치는 진행률에 따라 계속 바뀌는 값이라 style 로 준다 (색·모양은 CSS 에 있다).
  // aria-valuenow 는 0~100 정수, 스크린리더가 읽는 글은 aria-valuetext(시간 글자)다.
  return (
    <div className={cx(styles.progress, styles[tone], className)}>
      <div
        className={cx(styles.track, chaptered && styles.chaptered)}
        role={onSeek ? 'slider' : 'progressbar'} // onSeek 이 없으면(탐색 없음) 포커스되지 않는 진행 표시
        aria-label="재생 위치"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(pct)}
        aria-valuetext={label}
        tabIndex={onSeek ? 0 : undefined}
        onClick={onSeek ? seek : undefined}
        onKeyDown={onSeek ? onKeyDown : undefined}
      >
        {chaptered ? (
          <div className={styles.chapters} aria-hidden="true">
            {chapters.map((chapter) => (
              <div key={`${chapter.start}-${chapter.end}`} className={styles.segment} style={{ left: `${chapter.start * 100}%`, width: `${(chapter.end - chapter.start) * 100}%` }}>
                <div className={styles.segmentFill} style={{ width: `${Math.max(0, Math.min(1, (pct / 100 - chapter.start) / (chapter.end - chapter.start))) * 100}%` }} />
              </div>
            ))}
          </div>
        ) : <div className={styles.fill} style={{ width: `${pct}%` }} />}
        <div className={styles.knob} style={{ left: `${pct}%` }} />
      </div>
      <span className={cx('text-label-s', styles.time)} title={label}>{label}</span>
    </div>
  );
}
