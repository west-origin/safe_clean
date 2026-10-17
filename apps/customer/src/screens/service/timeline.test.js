import { describe, it, expect } from 'vitest';
import { buildChapters, chapterTitleAt } from './timeline.js';

const mark = (offsetSeconds, label = '바닥 청소') => ({ group: '거실', label, offsetSeconds });
describe('다시보기 구간', () => {
  it('완료 시각에서 구간이 끝나고 정각부터 다음 구간이다', () => {
    const chapters = buildChapters([mark(165), mark(320, '싱크대')], 630);
    expect(chapters.map((c) => c.title)).toEqual(['거실 · 바닥 청소', '거실 · 싱크대', '']);
    expect(chapterTitleAt(chapters, 319 / 630)).toBe('거실 · 싱크대');
    expect(chapterTitleAt(chapters, 320 / 630)).toBe('');
    expect(chapterTitleAt(chapters, 1)).toBe('');
  });
  it('너무 짧거나 동시에 끝난 작업은 합치며 제목 개수를 중복 집계하지 않는다', () => {
    const chapters = buildChapters([mark(5, 'A'), mark(10, 'B'), mark(11, 'C'), mark(11, 'D')], 1000);
    expect(chapters).toEqual([{ start: 0, end: 0.011, title: '거실 · A 외 3' }, { start: 0.011, end: 1, title: '' }]);
  });
  it('동시 완료는 한 구간으로 묶고 입력이 뒤섞여도 시간 순이다', () => {
    const chapters = buildChapters([mark(200, 'B'), mark(100, 'A'), mark(200, 'C')], 1000);
    expect(chapters.map((c) => c.title)).toEqual(['거실 · A', '거실 · B 외 1', '']);
    expect(buildChapters([mark(100), mark(110)], 1000)).toHaveLength(3); // 정확히 1%면 독립 구간
  });
  it('기준이 없거나 유효하지 않은 시각은 구간을 만들지 않는다', () => {
    expect(buildChapters([mark(null), mark(NaN), mark(-1), mark(0)], 100)).toEqual([]);
    expect(buildChapters([mark(10)], 0)).toEqual([]);
    expect(buildChapters([mark(200)], 100).at(-1).end).toBe(1);
  });
});
