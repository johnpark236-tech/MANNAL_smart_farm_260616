from datetime import datetime
import json
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Query
from fastapi.responses import JSONResponse

from app.services.weather_client import (
    DEFAULT_NX,
    DEFAULT_NY,
    DEFAULT_REGION_NAME,
    WeatherApiError,
    fetch_farm_weather,
)

router = APIRouter()
CACHE_DIR = Path(__file__).resolve().parents[2] / "cache"
WEATHER_CACHE_PATH = CACHE_DIR / "latest_weather.json"


@router.get("/weather/farm")
def get_farm_weather(
    region_name: str = Query(DEFAULT_REGION_NAME),
    nx: int = Query(DEFAULT_NX),
    ny: int = Query(DEFAULT_NY),
):
    try:
        response = fetch_farm_weather(region_name=region_name, nx=nx, ny=ny)
        if response.get("mode") == "real":
            _save_cache(WEATHER_CACHE_PATH, response)
        return response
    except WeatherApiError as exc:
        return _fallback_or_error(region_name, str(exc))
    except Exception:
        return _fallback_or_error(region_name, "Internal server error while reading KMA weather.")


def _fallback_or_error(region_name: str, detail: str):
    cached = _load_cache(WEATHER_CACHE_PATH)
    if cached:
        cached["source"] = "CACHE"
        cached["mode"] = "cached"
        cached["message"] = "현재 날씨 API 연결이 불안정하여 마지막으로 확인한 날씨를 표시합니다."
        return cached
    return {
        "source": "WEB_FALLBACK",
        "mode": "fallback",
        "region_name": region_name,
        "summary": "기상청 API 연결이 불안정합니다. 정확한 강수와 특보는 기상청 날씨누리에서 확인해 주세요.",
        "farm_advice": "정확한 작업 판단 전에는 기상청 날씨누리에서 강수와 특보를 확인하세요.",
        "weather": {},
        "detail": _safe_detail(detail),
    }


def _save_cache(path: Path, data: dict[str, Any]) -> None:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps({"cached_at": datetime.now().isoformat(timespec="seconds"), "data": data}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _load_cache(path: Path) -> dict[str, Any] | None:
    try:
        wrapper = json.loads(path.read_text(encoding="utf-8"))
        data = wrapper.get("data")
        if isinstance(data, dict):
            data = dict(data)
            data["cached_at"] = wrapper.get("cached_at", "")
            return data
    except Exception:
        return None
    return None


def _error_response(detail: str, status_code: int) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "source": "NONE",
            "mode": "error",
            "error": True,
            "message": "날씨 정보를 가져오지 못했습니다.",
            "detail": _safe_detail(detail),
            "weather": {},
        },
    )


def _safe_detail(detail: str) -> str:
    blocked = ("serviceKey=", "ServiceKey=", "authKey=")
    if any(token in detail for token in blocked):
        return "KMA request failed. Credentials were not included in the response."
    return detail
