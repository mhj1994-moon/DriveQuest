# DriveQuest (드라이브퀘스트)

목적지 퀘스트와 주행 후 피드백으로 안전·에코 운전 습관을 돕는 Pleos Connect 차량용 운전 에이전트 프로토타입입니다.

## 실행 방법

1. [서명된 release APK](apk/DriveQuest_release.apk)를 내려받아 Pleos Connect 에뮬레이터에 설치합니다. 소스에서 실행하려면 Android Studio로 프로젝트를 열고 Gradle 동기화 후 `demo` 빌드 변형을 선택합니다.
2. 차량을 정차하고 기어를 P에 둔 상태에서 DriveQuest를 엽니다. 요청되는 위치·차량 속도 권한을 허용합니다.
3. 에뮬레이터의 내비게이션 앱에서 목적지를 설정하고 경로 안내를 시작합니다.
4. DriveQuest에서 목적지를 확인하고, 정차 상태에서 **퀘스트 확인하기 → 퀘스트 시작**을 선택합니다. 종료도 정차 상태에서 진행합니다.
5. 기어를 D로 바꾸거나 차량이 움직이면, 앱이 버튼과 스크롤이 없는 **주행 중 퀘스트 진행 화면**으로 바뀝니다. 이 화면에서는 속도·남은 거리·퀘스트 관찰 상태만 표시하고, 홈·보상·결과 화면 조작은 정차 후에만 할 수 있습니다.

## 주행 중 화면 처리

- 앱에 `distractionOptimized`(DO)를 선언해 주행 중에도 퀘스트 진행 화면이 보이도록 했습니다. 이 선언이 없으면 Pleos는 주행 중 앱 화면을 가립니다.
- 앱은 AAOS `CarUxRestrictionsManager`의 주행 제한 상태를 따릅니다. 제한 상태이거나 상태를 아직 모를 때는 조작 UI를 숨기고 읽기 전용 요약만 보여줍니다. 제한 상태가 지정하는 글자 수·항목 수 제한도 반영합니다.
- 운영체제의 주행 제한 설정이나 허용 목록은 변경하지 않습니다.
- DO 선언은 기술적 선언일 뿐 Pleos App Market의 카테고리·주행 중 사용 승인이 아닙니다. 승인 여부는 확인되지 않았고, 주행 중 화면 구성은 운전자 주의 분산 기준에 맞춰 계속 조정할 예정입니다.

## 테스트 환경 및 필요한 설정

- Pleos Connect 에뮬레이터의 차량 서비스와 내비게이션 서비스가 필요합니다. 일반 휴대전화에서는 차량 데이터 연동을 확인할 수 없습니다.
- Android Studio 소스 빌드에는 Gradle 의존성 다운로드 및 Pleos Maven 저장소 접근이 필요합니다. 프로젝트 설정은 AGP 9.5.1, Gradle 9.6.0, Java 25 toolchain, compileSdk 37, minSdk 28, targetSdk 37입니다.
- 사용한 Pleos SDK: Vehicle 2.3.2, NaviHelper 3.0.5. 차량 속도는 AAOS Car API, 위치는 Android 위치 API를 사용합니다. Virtual Console의 HTTP 주소를 앱 데이터 공급원으로 사용하지 않습니다.
- SDK에서 지원하지 않거나 수신되지 않은 값은 미평가·수신 대기로 표시합니다. 현재 에뮬레이터에서는 주행에 따른 누적계 증가가 확인되지 않았습니다.
- release APK의 서명, 설치 및 홈 화면 실행을 확인했습니다. 경로 이동부터 퀘스트 종료·보상까지의 연속 실행과 실차 동작은 아직 검증되지 않았습니다.
- 빌드 변형: `release`(제출용, `com.example.drivequest`)와 `demo`(디버깅 가능한 프로토타입, `com.example.drivequest.demo`) 모두 같은 DO 선언과 주행 제한 처리를 사용합니다.

## Pleos Connect 에뮬레이터 버전과 화면 규격

- Pleos Connect Emulator **2.1.0**, Android **14 / API 34**
- 전체 화면 **2560 × 1440px**, **160dpi**. 앱 영역 크기는 분할 창 상태에 따라 달라집니다.

## GitHub 저장소 및 APK 파일 위치

- 저장소: [https://github.com/mhj1994-moon/DriveQuest](https://github.com/mhj1994-moon/DriveQuest)
- 서명된 release APK: [apk/DriveQuest_release.apk](https://github.com/mhj1994-moon/DriveQuest/blob/main/apk/DriveQuest_release.apk)
- 저장소 내 APK 경로: `apk/DriveQuest_release.apk`
