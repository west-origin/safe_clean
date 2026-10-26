# Mac에서 개발 앱 빌드

이 저장소는 고객 앱과 매니저 앱을 React/Vite로 빌드하고 Capacitor 8로 iOS/Android에 넣는다.
최근 수정한 백엔드는 개발 서버에 배포되어 있으므로 앱 빌드에 로컬 백엔드 실행이나 서버 비밀키가 필요하지 않다.

## 준비

- Node.js 22 이상
- iOS: Xcode 26 이상. 처음 실행해 추가 구성 요소 설치를 완료한다.
- Android: Android Studio 2025.2.1 이상, SDK Manager에서 Android SDK Platform 36 설치.

공식 요구 사항: https://capacitorjs.com/docs/getting-started/environment-setup

```bash
git clone https://github.com/west-origin/safe_clean.git
cd safe_clean
npm ci
```

이미 받은 저장소라면 자신의 변경 사항을 정리한 후 `git pull --ff-only`로 갱신한다.
다른 OS의 `node_modules`, `dist`, Android `build`, `.gradle`, `local.properties`를 복사하지 않는다.

## 개발 서버 설정

두 앱의 `.env.staging`에 공개 개발 API 주소가 포함되어 있다. 아래 개발 빌드 명령은 이 파일을 사용한다.
`.env.local` 또는 `.env.staging.local`이 이미 있으면 같은 이름의 설정을 덮어쓸 수 있으므로 API 주소를 확인한다.
셸에 설정한 `VITE_API_URL`도 파일보다 우선한다.

```dotenv
VITE_API_URL=https://ansimclean-api-test-rbtzr3o4cq-du.a.run.app/api/v1
VITE_API_MOCK=0
```

Supabase service role, LiveKit secret, DB 비밀번호는 앱에 넣지 않는다.

## iOS

저장소 최상위 폴더에서 실행한다. iOS 프로젝트가 이미 있으므로 `cap add ios`는 필요하지 않다.

```bash
npm run build:development -w apps/manager
npm exec -w apps/manager -- cap sync ios
npm exec -w apps/manager -- cap open ios
```

고객 앱은 다음 명령을 쓴다.

```bash
npm run build:development -w apps/customer
npm exec -w apps/customer -- cap sync ios
npm exec -w apps/customer -- cap open ios
```

Xcode에서 App 타깃의 Signing & Capabilities → Team을 선택하고, 연결한 iPhone을 실행 대상으로 선택한 뒤 Run을 누른다.
현재 프로젝트는 Swift Package Manager를 사용하므로 기본 빌드에 CocoaPods 설치는 필요하지 않다.
매니저 라이브 송출과 카메라는 실제 기기에서 확인한다. 현재 세션에서는 Windows의 Android 빌드를 확인했으며 iOS 빌드는 Mac에서 확인해야 한다.
TestFlight 배포는 Apple Developer 계정의 배포 서명을 설정한 후 Product → Archive에서 진행한다.

기존 `cap:ios`, `cap:android`, `cap:sync` 스크립트는 일반 모드로 빌드한다. 개발 서버를 이용할 때는 위의 `build:development` 명령을 사용한다.

## Android APK

```bash
npm run build:development -w apps/manager
npm exec -w apps/manager -- cap sync android
npm exec -w apps/manager -- cap open android
```

Android Studio의 Gradle 동기화를 완료하고 Build 메뉴에서 APK를 생성한다.
고객 앱은 명령의 `apps/manager`를 `apps/customer`로 바꾼다.

터미널에서 빌드하려면 JDK 21과 Android SDK 경로를 설정한다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME="$HOME/Library/Android/sdk"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > apps/manager/android/local.properties
cd apps/manager/android
./gradlew assembleDebug
```

Android Studio의 Gradle JDK로 JDK 21을 선택해도 된다. CLI에서는 설치한 JDK 21 경로가 필요하다.
생성 파일: `apps/manager/android/app/build/outputs/apk/debug/app-debug.apk`.
Mac의 디버그 서명은 Windows에서 설치한 APK와 다를 수 있다. 서명 충돌 시 기존 앱을 삭제하고 설치한다(로컬 로그인 정보가 삭제된다).

## 비밀 설정과 결과물

개인 `.env`, 인증서, 서명 키, 로컬 SDK 설정과 APK/빌드 로그는 Git에 포함하지 않는다.
이 파일들은 앱 소스 빌드에 필요하지 않으며, 스토어 배포용 서명이나 푸시 인증 설정은 별도로 구성한다.
