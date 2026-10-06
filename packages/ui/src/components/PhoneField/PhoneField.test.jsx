// PhoneField 검증: 라벨·앞자리·placeholder 표시, 붙여 넣은 전체 번호를 나머지 8자리로 정리하는 규칙,
// 8자리를 넘으면 자르지 않는 것, 번호 가리기 토글, 인증 버튼(verify/verifyActive), disabled, CSS 의 폭·줄바꿈 규칙.
// getByLabelText 는 <label> 글자로 연결된 입력칸을 찾는다 — 찾히면 label 과 input 이 제대로 이어졌다는 뜻이기도 하다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import PhoneField from './PhoneField.jsx';

// CSS Module 파일을 글자 그대로 읽어 둔다 — 아래 테스트가 CSS 규칙을 문자열로 확인한다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'PhoneField.module.css'), 'utf8');

describe('PhoneField', () => {
  it('라벨이 input 과 연결되고 앞자리 "010" 과 placeholder 가 보인다 (Field/Phone)', () => {
    const onChange = vi.fn();
    render(<PhoneField label="전화번호를 입력해주세요" labelStyle="heading" value="" onChange={onChange} />);
    const input = screen.getByLabelText('전화번호를 입력해주세요');
    expect(input).toHaveAttribute('placeholder', '123456');
    expect(input).toHaveAttribute('type', 'tel');
    expect(screen.getByText('010')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '인증하기' })).toBeNull();
    fireEvent.change(input, { target: { value: '1234' } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  // 여러 입력을 차례로 넣고, onChange 로 받은 값(정리된 값)을 seen 배열에 모아 한 번에 비교한다.
  // '0100' 은 8자리 이하라 010 을 떼지 않고 그대로 남아야 한다.
  it('010 을 붙인 전체 번호·하이픈을 넣어도 앞자리 뒤 8자리만 남긴다 (직접 사용 점검 U3)', () => {
    const seen = [];
    render(<PhoneField label="휴대폰 번호" value="" onChange={(e) => seen.push(e.target.value)} />);
    const input = screen.getByLabelText('휴대폰 번호');
    for (const typed of ['010-9999-0801', '01099990801', '9999-0801', '0100', '+82 10-9999-0801', '+82 010-9999-0801']) fireEvent.change(input, { target: { value: typed } });
    expect(seen).toEqual(['99990801', '99990801', '99990801', '0100', '99990801', '99990801']); // +82 도 국내 형식으로 (자체 점검 #50)
  });

  it('8자리를 넘는 번호는 앞 8자리로 자르지 않는다 — 모르는 사람 번호로 인증 문자가 가지 않게 (자체 점검 #50)', () => {
    const seen = [];
    render(<PhoneField label="휴대폰 번호" value="" onChange={(e) => seen.push(e.target.value)} />);
    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '999908011' } });
    expect(seen).toEqual(['999908011']); // 예전: '99990801' (다른 사람 번호)
  });

  it('가리기 토글을 누르면 글자가 가려지고(type 은 tel 그대로 — 비밀번호 관리자 오인 방지) aria-pressed 가 바뀐다', () => {
    render(<PhoneField label="휴대폰 번호" value="" onChange={() => {}} />);
    const toggle = screen.getByRole('button', { name: '번호 가리기' });
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByLabelText('휴대폰 번호')).toHaveAttribute('type', 'tel');
    expect(screen.getByLabelText('휴대폰 번호').className).toMatch(/masked/);
  });

  // rerender 는 같은 자리에 props 만 바꿔 다시 그린다. CSS Modules 클래스 이름에도 원래 이름(verifyActive)이 들어 있어 toContain 으로 본다.
  it('verify 면 "인증하기" 버튼이 있고 verifyActive 는 클래스로 드러난다 (Field/PhoneVerify)', () => {
    const onVerify = vi.fn();
    const { rerender } = render(<PhoneField label="휴대폰 번호" value="" onChange={() => {}} verify onVerify={onVerify} />);
    const btn = screen.getByRole('button', { name: '인증하기' });
    expect(btn.className).not.toContain('verifyActive');
    fireEvent.click(btn);
    expect(onVerify).toHaveBeenCalledTimes(1);
    rerender(<PhoneField label="휴대폰 번호" value="" onChange={() => {}} verify verifyActive />);
    expect(screen.getByRole('button', { name: '인증하기' }).className).toContain('verifyActive');
  });

  it('disabled 면 입력·버튼이 비활성화되고 터치 영역은 --touch-target 을 쓴다', () => {
    render(<PhoneField label="휴대폰 번호" value="" disabled verify />);
    expect(screen.getByLabelText('휴대폰 번호')).toBeDisabled();
    expect(screen.getByRole('button', { name: '인증하기' })).toBeDisabled();
    expect(css).toContain('var(--touch-target)');
  });

  it('CSS 가 8자리를 다 보여 주도록 입력칸에 최소 폭을 주고, 좁으면 인증 버튼이 아랫줄로 간다', () => {
    expect(css).toMatch(/\.numberBox \{[^}]*min-width: 130px/);
    expect(css).toMatch(/\.row \{[^}]*flex-wrap: wrap/);
  });
});
