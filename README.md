# KRX + KAMIS Market Dashboard MVP

국내 주식 시세와 농산물 가격을 수집해 PostgreSQL에 저장하고, 웹 대시보드에서 표와 그래프로 보여주는 로컬 실행용 MVP입니다.

현재 범위는 데이터 수집, 저장, 조회, 대시보드 표시입니다. 자동매매, 주문, 결제, 로그인, 모바일 앱, 키움증권 API는 포함하지 않습니다.

## 기술 스택

- Backend: Python FastAPI
- Database: PostgreSQL
- ORM: SQLAlchemy
- Data processing: pandas
- Frontend: React + Vite
- Charts: Recharts
- Config: `.env`

React는 Next.js보다 초기 MVP가 단순하고 로컬 실행이 쉬워 선택했습니다. 나중에 SSR, SEO, 배포 구조가 필요해지면 Next.js로 옮길 수 있습니다.

## 폴더 구조

```text
backend/
  app/
    main.py
    config.py
    database.py
    models/
    schemas/
    routers/
    services/
    utils/
  requirements.txt
  .env.example
  README.md
frontend/
  src/
    api/
    components/
    charts/
    pages/
  package.json
  README.md
docker-compose.yml
```

## PostgreSQL 준비

Docker가 있으면 가장 쉽습니다.

```bash
docker compose up -d db
```

직접 PostgreSQL을 설치했다면 아래와 같이 DB를 만듭니다.

```sql
CREATE DATABASE market_dashboard;
```

## Backend 실행

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload
```

- API 상태: http://localhost:8000/api/health
- API 문서: http://localhost:8000/docs

## Python만으로 화면 실행

Node.js를 설치하지 않아도 FastAPI가 제공하는 기본 웹 화면을 사용할 수 있습니다.
기본 DB는 SQLite 파일(`backend/market_dashboard.db`)이라 PostgreSQL이나 Docker 없이도 시작할 수 있습니다.

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload
```

브라우저에서 아래 주소를 엽니다.

```text
http://localhost:8000
```

이 화면에서도 대시보드, 주식 시세, 농산물 가격, 수집 관리 버튼을 사용할 수 있습니다.

`localhost:8000`에서 `사이트에 연결할 수 없음`이 나오면 백엔드 서버가 아직 실행되지 않은 상태입니다. VS Code 터미널에서 `uvicorn app.main:app --reload`가 계속 켜져 있어야 합니다.

## React Frontend 실행

```bash
cd frontend
npm install
npm run dev
```

기본 주소는 http://localhost:5173 입니다.

## .env 설정

`backend/.env.example`을 `backend/.env`로 복사한 뒤 수정합니다.

```text
DATABASE_URL=postgresql://postgres:password@localhost:5432/market_dashboard
KRX_API_KEY=
KAMIS_API_KEY=
KAMIS_USER_ID=
USE_DUMMY_DATA=true
```

실제 키나 비밀번호는 코드에 쓰지 말고 `.env`에만 넣습니다. `.env`는 `.gitignore`에 포함되어 Git에 올라가지 않습니다.

## 더미 데이터 모드

처음에는 API 키가 없어도 실행되도록 `USE_DUMMY_DATA=true`가 기본입니다.

1. 백엔드를 실행합니다.
2. 프론트엔드를 실행합니다.
3. 데이터 수집 관리 화면에서 `주식 데이터 수집`, `농산물 가격 수집` 버튼을 누릅니다.
4. 대시보드, 주식 시세, 농산물 가격 화면에서 데이터가 표시됩니다.

## 실제 KRX/KAMIS API 키 적용

`backend/.env`에 키를 입력하고 `USE_DUMMY_DATA=false`로 바꿉니다.

```text
KRX_API_KEY=실제_KRX_API_KEY
KAMIS_API_KEY=실제_KAMIS_API_KEY
KAMIS_USER_ID=실제_KAMIS_USER_ID
USE_DUMMY_DATA=false
```

현재 `krx_client.py`, `kamis_client.py`에는 실제 API URL과 파라미터를 쉽게 교체할 수 있는 함수 구조가 준비되어 있습니다.

## 자주 발생하는 오류

### DB 연결 오류

`connection refused`가 나오면 PostgreSQL이 실행 중인지 확인합니다.

```bash
docker compose ps
```

### 테이블이 없다는 오류

백엔드 시작 시 SQLAlchemy가 MVP용 테이블을 자동 생성합니다. 운영 단계에서는 Alembic 마이그레이션으로 전환하는 것이 좋습니다.

### 프론트엔드에서 API 호출 실패

백엔드가 `http://localhost:8000`에서 실행 중인지 확인합니다. 다른 주소를 쓰려면 `frontend/.env`에 아래 값을 넣습니다.

```text
VITE_API_BASE_URL=http://localhost:8000
```

## 다음 개발 단계 제안

1. 실제 KRX/KAMIS 응답 스펙에 맞춘 파서 구현
2. Alembic DB 마이그레이션 추가
3. 종목/품목별 필터와 날짜 검색 추가
4. 수집 스케줄러 추가
5. 관리자 인증 추가
6. 데이터 품질 검증과 실패 로그 저장
7. 배포용 Docker 구성 확장
