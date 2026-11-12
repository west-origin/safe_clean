#!/usr/bin/env node
// 개발 전용: Android Studio 에뮬레이터에 '가상 SMS' 를 넣는다 (실제 문자 발송 없음).
// 개발 Supabase 는 테스트 번호(scripts/dev-test-phones.json)에 문자를 보내지 않고 고정 인증번호를 쓴다.
// 이 스크립트가 그 인증번호를 운영 문자와 같은 문구로 에뮬레이터 문자함에 넣어, 앱에서 '문자 받기 → 입력' 을 그대로 해 볼 수 있다.
//
//   npm run dev:sms -- 01000000001                  # 테스트 번호의 고정 인증번호를 에뮬레이터로
//   npm run dev:sms -- 01000000001 --serial emulator-5556
//
// 안전장치: 에뮬레이터(serial 이 emulator-*)에만 보낸다. 실제 기기·운영과는 무관하다. 테스트 번호가 아니면 거절한다.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
export const TEST_PHONES = JSON.parse(readFileSync(join(here, 'dev-test-phones.json'), 'utf8')).phones;
/** 서버 Send SMS Hook(SmsHookService)이 보내는 문구와 같다 */
export const message = (code) => `[안심클린] 인증번호 ${code} (3분 안에 입력해주세요)`;
/** 보내는 사람으로 보일 번호 (가짜) */
export const SENDER = '01000000000';

/** '010-0000-0001'·'00000001'·'+82 10-0000-0001' → '01000000001' */
export function normalizePhone(raw) {
  let d = String(raw ?? '').replace(/\D/g, '');
  if (d.startsWith('82')) d = `0${d.slice(2)}`;
  if (d.length === 8) d = `010${d}`;
  return d;
}

/** 테스트 번호의 고정 인증번호. 없으면 null */
export const codeFor = (phone) => TEST_PHONES[normalizePhone(phone)] ?? null;

/** `adb devices` 출력에서 켜진 에뮬레이터 serial 목록 */
export function emulators(adbDevicesOutput) {
  return adbDevicesOutput.split('\n')
    .map((line) => line.trim().split(/\s+/))
    .filter(([serial, state]) => serial?.startsWith('emulator-') && state === 'device')
    .map(([serial]) => serial);
}

function findAdb() {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? join(homedir(), 'Library/Android/sdk');
  const candidate = join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
  return existsSync(candidate) ? candidate : 'adb';
}

function main(argv) {
  const args = [...argv];
  const serialAt = args.indexOf('--serial');
  const wanted = serialAt >= 0 ? args.splice(serialAt, 2)[1] : null;
  const phone = args[0];
  const code = phone && codeFor(phone);
  if (!code) {
    console.error(`테스트 번호를 넣어주세요: ${Object.keys(TEST_PHONES).join(', ')} (scripts/dev-test-phones.json)`);
    process.exit(1);
  }
  const adb = findAdb();
  const running = emulators(execFileSync(adb, ['devices'], { encoding: 'utf8' }));
  const serial = wanted ?? running[0];
  if (!serial || !running.includes(serial)) {
    console.error(wanted ? `${wanted} 은(는) 켜진 에뮬레이터가 아니에요 (실제 기기에는 보내지 않습니다).` : '켜진 Android 에뮬레이터가 없어요. Android Studio 에서 AVD 를 먼저 켜주세요.');
    process.exit(1);
  }
  execFileSync(adb, ['-s', serial, 'emu', 'sms', 'send', SENDER, message(code)], { stdio: 'inherit' });
  console.log(`${serial} 에 가상 SMS 를 넣었어요: ${normalizePhone(phone)} → ${message(code)}`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main(process.argv.slice(2));
