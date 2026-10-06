import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useAction } from './useAction.js';
// useAction 검증: 진행 중 잠금(더블 탭에도 한 번만 실행), 실패 → error, 다음 성공에서 error 지움, 진행 중 reset.
// waitFor 는 안의 확인이 통과할 때까지 잠깐씩 되풀이한다.

// 훅을 실제 버튼에 연결한 시험용 화면. 진행 중이면 버튼이 잠기고 라벨이 '진행 중' 으로 바뀐다
function Demo({ fn }) {
  const { run, loading, error } = useAction(fn);
  return (
    <div>
      <button disabled={loading} onClick={() => run('a')}>
        {loading ? '진행 중' : '실행'}
      </button>
      {error && <p role="alert">{error.message}</p>}
    </div>
  );
}

describe('useAction', () => {
  it('진행 중에는 버튼이 잠기고, 실패는 error 로 돌아오며, 성공하면 error 가 지워진다', async () => {
    // resolve 를 밖으로 빼 둔 Promise — 테스트가 부르기 전까지 끝나지 않아 "진행 중" 상태를 붙잡아 확인할 수 있다
    let resolve;
    const fn = vi.fn(() => new Promise((r) => (resolve = r)));
    render(<Demo fn={fn} />);
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByRole('button', { name: '진행 중' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button')); // 더블 탭
    resolve('ok');
    await waitFor(() => expect(screen.getByRole('button', { name: '실행' })).toBeEnabled());
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('실패하면 error 를 보여주고 다음 성공에서 지운다', async () => {
    // fail 이 true 인 동안 fn 이 던진다 → 문구 확인 → false 로 바꿔 다시 눌러 성공시키면 문구가 사라져야 한다
    let fail = true;
    const fn = vi.fn(async () => {
      if (fail) throw new Error('실패했어요');
    });
    render(<Demo fn={fn} />);
    fireEvent.click(screen.getByRole('button'));
    expect(await screen.findByRole('alert')).toHaveTextContent('실패했어요');
    fail = false;
    fireEvent.click(screen.getByRole('button'));
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  it('진행 중에 reset 하면 스피너는 두고, 끝난 옛 실행의 실패 문구는 다시 뜨지 않는다 (자체 점검 #49)', async () => {
    // 실행 → (끝나기 전에) 닫기(reset) → 그 뒤에 옛 실행을 실패시킨다. 스피너는 끝날 때까지 남고, 실패 문구는 뜨지 않아야 한다
    let reject;
    const fn = vi.fn(() => new Promise((_, r) => (reject = r)));
    // Demo 에 reset 을 부르는 '닫기' 버튼을 더한 것 (시트를 닫는 상황)
    function ResetDemo() {
      const { run, loading, error, reset } = useAction(fn);
      return (
        <div>
          <button disabled={loading} onClick={() => run()}>{loading ? '진행 중' : '실행'}</button>
          <button onClick={reset}>닫기</button>
          {error && <p role="alert">{error.message}</p>}
        </div>
      );
    }
    render(<ResetDemo />);
    fireEvent.click(screen.getByRole('button', { name: '실행' }));
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));
    expect(screen.getByRole('button', { name: '진행 중' })).toBeDisabled(); // 풀려 보이는데 눌러도 반응 없는 상태가 아니다
    reject(new Error('옛 실패'));
    await waitFor(() => expect(screen.getByRole('button', { name: '실행' })).toBeEnabled());
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
