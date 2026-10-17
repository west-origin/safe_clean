// PDF 36-38쪽의 다시보기 구간. 체크는 작업의 종료 시각이며 경계 정각부터 다음 구간이다.
// 짧은 구간은 이름 배열을 보관하며 합쳐 '외 1 외 2'처럼 집계가 중복되지 않게 한다.
export const MIN_CHAPTER_RATIO = 0.01;

/**
 * @param {{group:string,label:string,offsetSeconds:number|null}[]} timeline
 * @param {number} totalSeconds
 * @returns {{start:number,end:number,title:string}[]} 마지막 체크 이후는 제목 없는 구간.
 */
export function buildChapters(timeline, totalSeconds) {
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0 || !timeline?.length) return [];
  // 같은 시각의 완료는 한 구간으로 묶는다. 입력 순서와 관계없이 시각 순으로 계산한다.
  const ends = new Map();
  for (const mark of timeline) {
    if (!Number.isFinite(mark.offsetSeconds) || mark.offsetSeconds <= 0) continue;
    const end = Math.min(1, mark.offsetSeconds / totalSeconds);
    const names = ends.get(end) ?? [];
    names.push(`${mark.group} · ${mark.label}`);
    ends.set(end, names);
  }
  const segments = [];
  let start = 0;
  let pending = [];
  let lastEnd = 0;
  for (const [end, names] of [...ends].sort(([a], [b]) => a - b)) {
    lastEnd = end;
    pending.push(...names);
    if (end - start + Number.EPSILON < MIN_CHAPTER_RATIO) continue;
    segments.push({ start, end, names: pending });
    start = end;
    pending = [];
  }
  if (pending.length && segments.length) {
    const previous = segments.at(-1);
    previous.end = lastEnd;
    previous.names.push(...pending);
    start = lastEnd;
  }
  if (!segments.length) return [];
  const chapters = segments.map(({ start: from, end, names }) => ({
    start: from, end, title: names.length > 1 ? `${names[0]} 외 ${names.length - 1}` : names[0],
  }));
  if (start < 1) chapters.push({ start, end: 1, title: '' });
  return chapters;
}

/** @param {{start:number,end:number,title:string}[]} chapters @param {number} ratio */
export function chapterTitleAt(chapters, ratio) {
  return (chapters.find((chapter) => ratio >= chapter.start && ratio < chapter.end) ?? (ratio >= 1 ? chapters.at(-1) : undefined))?.title ?? '';
}
