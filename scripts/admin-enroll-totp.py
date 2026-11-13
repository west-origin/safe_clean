#!/usr/bin/env python3
"""어드민 인증앱(TOTP) 등록 — runbook.md "어드민 계정과 토큰" 의 3단계를 한 번에.

1) 매니저 앱과 같은 비밀번호 로그인(서버 POST /auth/login, role=manager) → aal1 토큰
2) Supabase MFA factor 만들기 → QR 이미지를 열고, 직접 넣을 수 있는 키도 보여 준다
3) 인증앱에 뜬 6자리로 challenge·verify → 등록 완료(verified)

비밀번호·토큰은 화면과 파일에 남기지 않는다. QR 이미지는 임시 파일로 열고 끝나면 지운다.
사용: python3 scripts/admin-enroll-totp.py           (개발 서버·safe_clean_test 기본값)
      API_URL=… SUPABASE_URL=… SUPABASE_PUBLISHABLE_KEY=… python3 scripts/admin-enroll-totp.py   (다른 환경)
"""
import getpass
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import base64

HERE = os.path.dirname(os.path.abspath(__file__))
API_URL = os.environ.get("API_URL", "https://ansimclean-api-test-rbtzr3o4cq-du.a.run.app/api/v1")


def staging_value(name):
    """고객 앱 개발 설정(apps/customer/.env.staging)의 공개 값 — Supabase 주소·publishable 키 (비밀키 아님)"""
    with open(os.path.join(HERE, "..", "apps", "customer", ".env.staging"), encoding="utf-8") as f:
        for line in f:
            m = re.match(r"^" + name + r"=(.*)$", line.strip())
            if m:
                return m.group(1).strip()
    return None


SUPABASE_URL = os.environ.get("SUPABASE_URL") or staging_value("VITE_SUPABASE_URL")
KEY = os.environ.get("SUPABASE_PUBLISHABLE_KEY") or staging_value("VITE_SUPABASE_PUBLISHABLE_KEY")


def call(method, url, body=None, token=None, apikey=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if apikey:
        headers["apikey"] = apikey
    req = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(), method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            text = r.read().decode()
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode()[:300]
        sys.exit(f"실패: {method} {urllib.parse.urlsplit(url).path} → HTTP {e.code} {detail}")


def main():
    phone = input("휴대폰 번호 (010 뒤 8자리): ").strip()
    password = getpass.getpass("비밀번호 (화면에 안 보임): ")
    login = call("POST", API_URL + "/auth/login", {"phone": phone, "password": password, "role": "manager"})
    password = None
    if login.get("user", {}).get("role") != "admin":
        sys.exit("어드민 계정이 아니에요")
    token = login["accessToken"]
    print("로그인했어요 (어드민, 아직 MFA 전).")

    factor = call("POST", SUPABASE_URL + "/auth/v1/factors", {"factor_type": "totp", "friendly_name": "ansimclean-admin-" + str(int(time.time()))}, token, KEY)  # 실행마다 다른 이름 — 겹치면 Supabase 가 거절한다
    factor_id = factor["id"]
    totp = factor.get("totp", {})
    qr = totp.get("qr_code", "")
    qr_file = None
    if qr.startswith("data:image/svg+xml"):
        head, data = qr.split(",", 1)
        svg = base64.b64decode(data) if ";base64" in head else urllib.parse.unquote(data).encode()
        fd, qr_file = tempfile.mkstemp(suffix=".svg")
        os.write(fd, svg)
        os.close(fd)
        subprocess.run(["open", qr_file], check=False)  # macOS: 기본 뷰어로 QR 을 연다
        print("QR 이미지를 열었어요. 휴대폰 인증앱(Google Authenticator 등)으로 찍어 주세요.")
    print("QR 을 못 찍으면 인증앱에 이 키를 직접 넣으세요:", totp.get("secret"))

    try:
        for _ in range(3):
            code = input("인증앱에 뜬 6자리: ").strip()
            challenge = call("POST", f"{SUPABASE_URL}/auth/v1/factors/{factor_id}/challenge", None, token, KEY)
            req = urllib.request.Request(f"{SUPABASE_URL}/auth/v1/factors/{factor_id}/verify",
                                         data=json.dumps({"challenge_id": challenge["id"], "code": code}).encode(), method="POST",
                                         headers={"Content-Type": "application/json", "Authorization": "Bearer " + token, "apikey": KEY})
            try:
                with urllib.request.urlopen(req, timeout=20):
                    print("등록 완료! 이제 매니저 앱에서 로그인 → 인증앱 6자리 → 관리자 홈으로 들어갈 수 있어요.")
                    return
            except urllib.error.HTTPError as e:
                print(f"6자리가 맞지 않아요 (HTTP {e.code}). 인증앱의 새 숫자로 다시 입력해 주세요.")
        sys.exit("3번 틀렸어요. 스크립트를 다시 실행하면 처음부터 등록해요(등록 안 된 factor 는 남아도 로그인에 영향 없음).")
    finally:
        if qr_file:
            os.remove(qr_file)


if __name__ == "__main__":
    main()
