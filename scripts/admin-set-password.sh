#!/usr/bin/env bash
# 어드민(또는 매니저) 계정 비밀번호 정하기 — 매니저 앱 '비밀번호를 잊으셨나요?' 와 같은 서버 API 를 터미널에서 부른다.
# 1) 그 계정 휴대폰으로 인증번호 문자 → 2) 인증번호 확인 → 3) 새 비밀번호 저장.
# 비밀번호는 화면에 보이지 않게 입력받고 어디에도 저장하지 않는다.
# 사용: API_URL=https://<api>/api/v1 bash scripts/admin-set-password.sh   (API_URL 을 비우면 개발 서버)
set -euo pipefail
API_URL="${API_URL:-https://ansimclean-api-test-rbtzr3o4cq-du.a.run.app/api/v1}"

read -r -p "휴대폰 번호 (010 뒤 8자리, 예: 12345678): " PHONE
curl -sS -f -X POST "$API_URL/auth/password/otp" -H 'Content-Type: application/json' \
  -d "{\"phone\":\"$PHONE\",\"role\":\"manager\"}" > /dev/null || { echo "인증번호를 보내지 못했어요 (번호·서버 확인)"; exit 1; }
echo "인증번호 문자를 보냈어요."

read -r -p "문자로 받은 6자리: " CODE
TOKEN=$(curl -sS -f -X POST "$API_URL/auth/phone/verify" -H 'Content-Type: application/json' \
  -d "{\"phone\":\"$PHONE\",\"code\":\"$CODE\",\"role\":\"manager\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["verificationToken"])') \
  || { echo "인증번호가 맞지 않아요"; exit 1; }

read -r -s -p "새 비밀번호 (8자 이상, 화면에 안 보임): " PASSWORD; echo
read -r -s -p "새 비밀번호 한 번 더: " PASSWORD2; echo
[ "$PASSWORD" = "$PASSWORD2" ] || { echo "두 비밀번호가 달라요"; exit 1; }
BODY=$(TOKEN="$TOKEN" PASSWORD="$PASSWORD" python3 -c 'import json,os; print(json.dumps({"verificationToken": os.environ["TOKEN"], "password": os.environ["PASSWORD"]}))')
unset PASSWORD PASSWORD2
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API_URL/auth/password/reset" -H 'Content-Type: application/json' -d "$BODY")
unset BODY
if [ "$STATUS" = "200" ]; then echo "비밀번호를 정했어요. 이제 매니저 앱(관리자)에서 이 번호와 비밀번호로 로그인할 수 있어요."; else echo "비밀번호를 바꾸지 못했어요 (HTTP $STATUS)"; exit 1; fi
