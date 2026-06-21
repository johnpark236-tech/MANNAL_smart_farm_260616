# Render 무료 Web Service 배포 안내

이 문서는 smart_farm FastAPI backend를 Render에 배포하는 절차입니다. Android 앱 변경은 다음 작업에서 별도로 진행합니다.

## 1. GitHub 저장소 연결

1. https://render.com 에 GitHub 계정으로 로그인합니다.
2. New + 에서 Web Service를 선택합니다.
3. johnpark236-tech/MANNAL_smart_farm_260616 저장소를 연결합니다.

## 2. Web Service 설정

```text
Name: mannal-smart-farm-backend
Runtime: Python
Root Directory: backend
Build Command: pip install -r requirements.txt
Start Command: uvicorn app.main:app --host 0.0.0.0 --port $PORT
Instance Type: Free
Auto-Deploy: Yes
```

backend/render.yaml을 Blueprint로 사용할 때도 위 설정과 같은지 확인합니다.

## 3. 환경변수 입력

Render Dashboard의 Environment에서 다음 값을 직접 입력합니다.

```text
DATABASE_URL=sqlite:///./market_dashboard.db
KAMIS_API_KEY=실제 KAMIS 인증키
KAMIS_USER_ID=실제 KAMIS 사용자 ID
KMA_API_KEY=실제 기상청 Decoding 인증키
USE_DUMMY_DATA=false
```

.env 파일은 GitHub에 올리지 않습니다. 실제 API 키는 Render Environment에만 입력하며 코드나 로그에 출력하지 않습니다.

## 4. 배포 후 확인

배포 주소가 https://mannal-smart-farm-backend.onrender.com 이라면 다음 주소를 확인합니다.

```text
https://mannal-smart-farm-backend.onrender.com/docs
https://mannal-smart-farm-backend.onrender.com/api/weather/farm
https://mannal-smart-farm-backend.onrender.com/api/prices/kamis
```

## 5. Android 연결은 다음 작업에서 진행

Render 배포가 정상 동작한 뒤 Android의 BACKEND_BASE_URL을 아래처럼 변경해야 합니다.

```java
// 기존
private static final String BACKEND_BASE_URL = "http://172.30.1.51:8000";

// 다음 작업에서 변경
private static final String BACKEND_BASE_URL = "https://mannal-smart-farm-backend.onrender.com";
```

이번 작업에서는 Android 파일을 수정하지 않습니다.

## 6. 무료 플랜 주의사항

무료 Web Service는 일정 시간 요청이 없으면 sleep 상태가 될 수 있어 첫 요청이 느릴 수 있습니다. 로컬 SQLite와 cache 파일은 재배포 또는 재시작 때 유지되지 않을 수 있으므로 지속 운영에는 영구 디스크나 별도 관리형 데이터베이스를 검토합니다.
## SQLite 배포 의존성

현재 Render 무료 배포는 SQLite DATABASE_URL 기준이므로 psycopg 의존성은 사용하지 않습니다. PostgreSQL로 전환할 때만 psycopg를 다시 추가합니다.
