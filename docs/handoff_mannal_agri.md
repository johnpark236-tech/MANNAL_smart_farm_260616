# 만날 농사 앱 인수인계 개요

작성 기준: 2026-06-17  
현재 버전: `1.4.0` / `versionCode 14`  
앱 표시명: `만날 농사`  
패키지명: `com.mannal.agri`

## 핵심 위치

- 프로젝트 루트: `C:\Users\Admin\OneDrive\문서\스마트팜`
- Android 앱: `android_app`
- 메인 소스: `android_app\src\com\mannal\agri\MainActivity.java`
- 매니페스트: `android_app\AndroidManifest.xml`
- 빌드 스크립트: `android_app\build_apk.ps1`
- APK 출력: `android_app\out\AgriPrice.apk`
- 버전 APK 규칙: `android_app\out\AgriPrice_v1.4.0.apk`

## 현재 기능

- 시세: 관심 품종 3개 저장, 다음 실행 시 로딩, 공개 시세 원문 검색 및 요약
- 날씨: 관심 지역 3개 저장, 1/2/3번 지역 조회, 농사 작업 참고 요약, TTS 읽기
- 음성 날씨: `오늘 날씨 알려줘` 명령으로 1번 관심 지역 날씨 조회 후 읽기
- 영농일지: 날짜/작목/작업/메모 저장, 검색, 길게 눌러 삭제, TXT 백업
- 음성 일지: `일지 메모`, `기록해`, `작업 기록` 등으로 영농일지 화면 전환 후 다음 음성을 저장

## 빌드

```powershell
cd C:\Users\Admin\OneDrive\문서\스마트팜\android_app
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```

## 주의

- 마이크 권한은 Android 정책상 설치/업데이트 시 미리 허용할 수 없고 앱 실행 중 사용자 허용이 필요합니다.
- 일반 APK는 시스템 수준 상시 호출어를 만들 수 없습니다. 현재 방식은 앱 실행 중 음성 대기입니다.
- 시세/날씨는 외부 웹 원문 기반이라 사이트 구조 변경에 취약합니다.
- 음성 인식과 TTS 동시 사용은 기기별 차이가 큽니다.

브라우저 검토용 상세 문서는 `docs\handoff_mannal_agri.html`을 열면 됩니다.
