#!/usr/bin/env python3
"""고객 앱 홈 혜택 배너 처음 3장 올리기 (배너·공지 관리 4회차).

서버로 옮기기 전 앱에 고정돼 있던 이미지·문구 그대로(packages/ui/src/assets/home-banner-*.png).
매니저 앱 어드민 로그인과 같은 길을 탄다: 비밀번호 로그인(aal1) → 인증앱 6자리(서버 POST /auth/mfa/verify, aal2) → POST /admin/banners.
비밀번호·토큰은 화면과 파일에 남기지 않는다. 이미 올렸는지 확인하지 않으니 한 번만 실행한다(지울 때는 매니저 앱 배너 관리).
사용: python3 scripts/seed-home-banners.py          (개발 서버 기본값)
      API_URL=https://<api>/api/v1 python3 scripts/seed-home-banners.py
"""
import getpass
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
API_URL = os.environ.get("API_URL", "https://ansimclean-api-test-rbtzr3o4cq-du.a.run.app/api/v1")
ASSETS = os.path.join(HERE, "..", "packages", "ui", "src", "assets")
# 예전 apps/customer/src/screens/home/HomePromotions.jsx 의 BANNERS 와 같은 순서·문구
BANNERS = [
    ("home-banner-coupon.png", "첫 청소 15,000원 쿠폰팩", "즉시 지급 · 신규 고객 웰컴"),
    ("home-banner-daily.png", "청소는 맡기고, 일상은 여유롭게", "우리 집에 맞는 청소를 만나보세요"),
    ("home-banner-gift.png", "소중한 사람에게 깨끗한 하루", "깨끗함을 넘어, 안심을 전해요"),
]


def send(req):
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try:
            message = json.loads(body).get("error", {}).get("message", body[:200])
        except ValueError:
            message = body[:200]
        sys.exit(f"실패: {req.get_method()} {req.full_url.replace(API_URL, '')} → HTTP {e.code} {message}")


def post_json(path, body, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    return send(urllib.request.Request(API_URL + path, data=json.dumps(body).encode(), method="POST", headers=headers))


def post_banner(token, filename, title, description):
    """multipart/form-data 로 이미지 + 문구 (매니저 앱 createAdminBanner 와 같은 모양)"""
    boundary = "----ansimclean" + uuid.uuid4().hex
    with open(os.path.join(ASSETS, filename), "rb") as f:
        image = f.read()
    parts = []
    for name, value in (("title", title), ("description", description)):
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: image/png\r\n\r\n'.encode() + image + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    req = urllib.request.Request(API_URL + "/admin/banners", data=b"".join(parts), method="POST",
                                 headers={"Authorization": "Bearer " + token, "Content-Type": "multipart/form-data; boundary=" + boundary})
    return send(req)


def main():
    phone = input("어드민 휴대폰 번호 (010 뒤 8자리): ").strip()
    password = getpass.getpass("비밀번호 (화면에 안 보임): ")
    login = post_json("/auth/login", {"phone": phone, "password": password, "role": "manager"})
    password = None
    if login.get("user", {}).get("role") != "admin":
        sys.exit("어드민 계정이 아니에요")
    code = input("인증앱 6자리: ").strip()
    mfa = post_json("/auth/mfa/verify", {"code": code}, login["accessToken"])
    token = mfa["accessToken"]
    print("어드민 MFA 로그인 완료.")
    for filename, title, description in BANNERS:
        banner = post_banner(token, filename, title, description)
        print(f"올림: {banner['title']} ({banner['stateLabel']} · {banner['periodLabel']})")
    print("완료 — 고객 앱 홈을 다시 열면 이 순서로 보여요.")


if __name__ == "__main__":
    main()
