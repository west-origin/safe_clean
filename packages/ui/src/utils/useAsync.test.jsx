import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useAsync } from './useAsync.js';
// useAsync 를 화면처럼 쓰는 Demo 로 로딩 → 오류 → 다시 시도 → 데이터 → setData 흐름과,
// deps(id)가 바뀌면 늦게 온 옛 응답을 버리는지를 검증한다.

// 오류면 누르면 reload 하는 버튼, 로딩 중이면 '로딩', 데이터가 오면 누를 때마다 setData 로 '!' 를 붙이는 버튼을 그린다
function Demo({ load, id }) {
  const { data, error, loading, reload, setData } = useAsync(() => load(id), [id]);
  if (error) return <button onClick={reload}>오류: {String(error.message)}</button>;
  if (loading) return <p>로딩</p>;
  return <button onClick={() => setData((d) => d + '!')}>{data}</button>;
}

describe('useAsync', () => {
  it('로딩 → 데이터, setData 로 갱신, 실패 시 error 와 reload', async () => {
    // 처음엔 실패 → 오류 버튼을 눌러 reload(이번엔 성공) → 데이터 버튼을 눌러 setData 로 화면만 바꾼다
    let fail = true;
    const load = vi.fn(async (id) => {
      if (fail) throw new Error('네트워크');
      return `값-${id}`;
    });
    render(<Demo load={load} id={1} />);
    expect(screen.getByText('로딩')).toBeInTheDocument();
    await screen.findByText('오류: 네트워크');
    fail = false;
    fireEvent.click(screen.getByRole('button'));
    await screen.findByText('값-1');
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('값-1!')).toBeInTheDocument();
  });

  it('deps 가 바뀌면 다시 부르고 옛 응답은 버린다', async () => {
    // id 별 resolve 를 모아 두고 테스트가 끝낼 순서를 정한다: id 1 → 2 로 바꾼 뒤 1 의 응답을 늦게 끝낸다
    const resolvers = {};
    const load = (id) => new Promise((resolve) => (resolvers[id] = resolve));
    const { rerender } = render(<Demo load={load} id={1} />);
    rerender(<Demo load={load} id={2} />);
    resolvers[1]('값-1'); // 늦게 온 옛 응답
    resolvers[2]('값-2');
    await waitFor(() => expect(screen.getByText('값-2')).toBeInTheDocument());
    expect(screen.queryByText('값-1')).toBeNull();
  });
});
