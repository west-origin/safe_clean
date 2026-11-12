// 개발용 에뮬레이터 가상 SMS 스크립트(scripts/dev-sms.js)의 순수 함수
import { describe, it, expect } from 'vitest';
import { normalizePhone, codeFor, emulators, message } from './dev-sms.js';

describe('dev-sms', () => {
  it('번호 형식을 01000000001 로 맞추고 테스트 번호의 고정 인증번호를 찾는다', () => {
    expect(normalizePhone('010-0000-0001')).toBe('01000000001');
    expect(normalizePhone('00000001')).toBe('01000000001');
    expect(normalizePhone('+82 10-0000-0001')).toBe('01000000001');
    expect(codeFor('00000001')).toBe('123456');
    expect(codeFor('01012345678')).toBeNull(); // 테스트 번호가 아니면 보내지 않는다
  });

  it('켜진 에뮬레이터만 고른다 (실제 기기·꺼진 에뮬레이터 제외)', () => {
    const out = 'List of devices attached\nemulator-5554\tdevice\nR58M123ABC\tdevice\nemulator-5556\toffline\n';
    expect(emulators(out)).toEqual(['emulator-5554']);
  });

  it('문구는 서버 Hook 과 같다', () => {
    expect(message('123456')).toBe('[안심클린] 인증번호 123456 (3분 안에 입력해주세요)');
  });
});
