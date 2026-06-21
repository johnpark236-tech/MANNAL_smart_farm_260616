# 금일 농산물 시세 확인 EXE

상추, 고구마 등 농산물 시세를 조회하는 Windows용 Python UI입니다.
EXE를 실행하면 로컬 웹 서버가 시작되고 기본 브라우저에 조회 화면이 열립니다.

## 실행

```bat
dist\AgriPriceChecker.exe
```

PC 접속 주소:

```text
http://127.0.0.1:14124/
```

스마트폰 접속 주소는 실행 화면 상단에 표시됩니다.
예시는 다음과 같습니다.

```text
http://192.168.0.10:14124/
```

스마트폰에서 보려면 PC와 스마트폰이 같은 Wi-Fi에 연결되어 있어야 합니다.
Windows 방화벽 알림이 나오면 이 앱의 네트워크 접근을 허용해야 합니다.

## EXE 만들기

```bat
build_exe.bat
```

빌드 후 실행 파일은 `dist\AgriPriceChecker.exe`에 생성됩니다.

## 데이터 조회 방식

API 인증키 없이 경기도농업기술원 가격정보 공개 화면을 조회합니다.

기본 공개 출처:

```text
https://nongup.gg.go.kr/data/62
```
