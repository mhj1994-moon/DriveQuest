# DriveQuest · 드라이브퀘스트

목적지 기반 퀘스트와 주행 후 피드백으로 안전·에코 운전 습관 형성을 돕는 Pleos 기반 차량용 운전 에이전트 프로토타입입니다.

## 실행 방법

제출용 APK는 `apk/문호진_DriveQuest_release.apk`에 있습니다. Pleos Connect 에뮬레이터에 설치하거나 소스 코드를 다음 순서로 실행할 수 있습니다.

1. Android Studio에서 프로젝트 최상위 폴더를 열고 Gradle 동기화를 진행합니다.
2. Build Variant를 `demo`로 선택하고 Pleos Connect 에뮬레이터에서 앱을 실행합니다.
3. 차량을 정차·P 상태로 두고 차량 속도와 위치 권한을 허용합니다.
4. 내비게이션 앱에서 목적지를 설정하고 경로 안내를 시작합니다.
5. DriveQuest에서 목적지를 확인한 뒤 **퀘스트 확인하기 → 퀘스트 시작**을 선택합니다. 종료는 정차 후 **주행 종료**로 진행합니다.

## 테스트 환경 및 필요한 설정

- Android 14 / API 34 기반 Pleos Connect 에뮬레이터와 차량·내비게이션 서비스가 필요합니다.
- 소스 빌드 설정: compileSdk 37, targetSdk 37, minSdk 28, AGP 9.5.1, Gradle 9.6.0.
- Gradle daemon toolchain 설정은 Java 25입니다. 해당 JDK 또는 toolchain 다운로드가 가능한 환경이 필요합니다.
- 사용 SDK: Vehicle 2.3.2, NaviHelper 3.0.5.
- 최초 동기화 시 의존성 다운로드를 위한 네트워크와 Pleos Maven 저장소 접근이 필요합니다.
- 차량 속도·위치 권한을 허용해야 관련 값을 수신할 수 있습니다. 데이터 수신은 앱이 보이는 동안 수행합니다.
- 미지원·미수신 데이터는 미평가로 표시합니다. 현재 에뮬레이터에서는 주행에 따라 누적계가 증가하지 않습니다.
- 위치 재생용 [강남역 부근 → 역삼역 부근 GPX 경로](test-data/routes/seoul-gangnam-yeoksam.gpx)를 준비했습니다. 설정과 출처는 [경로 안내](test-data/routes/README.md)를 참고하세요. Android 위치 재생과 Pleos 내비 이동은 별도이며 전체 주행 완료 흐름은 미검증입니다.

## 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

- Pleos Connect Emulator **v2.1.0** — 사용자 설치 기록 기준.
- 전체 화면: **2560 × 1440px**, **160dpi**.
- 앱은 플랫폼의 분할 window를 사용하며, 앱 표시 영역은 분할 상태에 따라 달라집니다.

## APK 파일 위치

- 서명된 제출용 release APK: `apk/문호진_DriveQuest_release.apk`
- 개발용 demo APK는 제출본이 아닙니다.
