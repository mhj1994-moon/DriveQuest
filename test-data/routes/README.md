# 서울 GPS 재생 시험 경로

확인·작성일: 2026-11-15

- 파일: `seoul-gangnam-yeoksam.gpx`
- 출발: 강남역 부근 도로 (위도 37.497887, 경도 127.027606)
- 도착: 역삼역 부근 테헤란로 (위도 37.500569, 경도 127.036515)
- 도로 경로 출처: OSRM 공개 라우팅 서비스 / OpenStreetMap 기여자
- 원본 출처 및 확인 캐시: `.cache/pleos/seoul-gangnam-yeoksam-source.json`
- 경로 서비스 거리 약 842m, GPX 좌표의 구면 거리 누적 약 841m. 실제 운행 거리 측정값이 아니다.
- 97개 합성 위치 표본. 이동 시간은 가상 속도 30km/h 기준이며, 시작 3초·끝 10초 정지를 포함해 약 114초.
- 도로 형상은 지도 자료, 시간은 합성 데이터다. 실제 주행 기록이나 실시간 도로 안전 안내가 아니다.

## 준비된 에뮬레이터 설정

Extended Controls → Location → Routes에 GPX를 가져왔으며, 저장된 이름은 **서울 강남역 → 역삼역 · 840m**다. 지도에서 A/B 지점과 테헤란로 경로를 확인했고 배속을 Speed 1X로 설정했다. PLAY ROUTE 상태로 두었으며 재생하지 않았다. 기존 경로·사용자 파일은 삭제하지 않았다.

## 서로 구분할 데이터

1. GPX 재생은 Android 가상 위치 표본을 변경하는 기능이다.
2. DriveQuest는 GPX 파일을 직접 읽지 않고 Android LocationManager에서 위치를 받는다. 위치 권한이 필요하다. 좌표 간 거리 합산이나 속도 적분은 하지 않는다.
3. 속도는 AAOS Car API, 차량 현재 주행거리는 Vehicle SDK, 남은 거리는 NaviHelper에서 각각 수신한다. GPS 경로만 재생했다고 차량 속도·기어·누적계·현재 주행거리까지 자동으로 변한다고 가정하지 않는다. 미지원 값은 대체하지 않는다.
4. Pleos 내비 위치·잔여 거리·도착 이벤트가 GPS 재생을 따르는지는 미검증이다. 저장한 것은 Extended Controls의 GPS 경로이며 Pleos 내비 자체의 출발지/목적지를 설정하거나 도착시킨 결과가 아니다.

공식 동작 설명: https://developer.android.com/studio/run/emulator-extended-controls#location
지도 저작자 표시: https://www.openstreetmap.org/copyright
