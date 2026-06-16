# Backend

FastAPI 기반 데이터 수집/조회 API입니다.

## 실행

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload
```

## 주요 API

- `GET /api/health`
- `GET /api/stocks`
- `GET /api/agri-prices`
- `POST /api/collect/stocks`
- `POST /api/collect/agri-prices`

`.env`의 `USE_DUMMY_DATA=true`이거나 API 키가 비어 있으면 더미 데이터가 사용됩니다.
