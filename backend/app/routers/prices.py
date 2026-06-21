from datetime import datetime
import json
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Query

from app.services.kamis_client import KamisApiError, KamisClient

router = APIRouter()
CACHE_DIR = Path(__file__).resolve().parents[2] / "cache"
PRICE_CACHE_PATH = CACHE_DIR / "latest_prices.json"


@router.get("/prices/kamis")
def get_kamis_prices(
    category_code: str = Query("200"),
    country_code: str = Query("3411"),
    product_cls_code: str = Query("01"),
    regday: str | None = Query(None),
    convert_kg_yn: str = Query("N"),
):
    client = KamisClient()
    mode = "dummy" if client.settings.use_dummy_data else "real"

    try:
        if client.settings.use_dummy_data:
            items = _normalize_dummy_items(client.fetch_agri_prices())
        else:
            items = client.fetch_daily_price_by_category(
                regday=regday,
                product_cls_code=product_cls_code,
                item_category_code=category_code,
                country_code=country_code,
                convert_kg_yn=convert_kg_yn,
            )
    except (KamisApiError, Exception) as exc:
        cached = _load_cache(PRICE_CACHE_PATH)
        if cached:
            cached["source"] = "CACHE"
            cached["mode"] = "cached"
            cached["message"] = "현재 시세 API 연결이 불안정하여 마지막으로 확인한 시세를 표시합니다."
            return cached
        return {
            "source": "NONE",
            "mode": "error",
            "error": True,
            "message": "시세 정보를 가져오지 못했습니다.",
            "detail": _safe_detail(str(exc)),
            "count": 0,
            "items": [],
        }

    response = {
        "source": "KAMIS",
        "mode": mode,
        "category_code": category_code,
        "country_code": country_code,
        "product_cls_code": product_cls_code,
        "convert_kg_yn": convert_kg_yn,
        "regday": regday,
        "count": len(items),
        "items": items,
    }
    if mode == "real":
        _save_cache(PRICE_CACHE_PATH, response)
    return response


def _normalize_dummy_items(rows: list[dict]) -> list[dict]:
    return [
        {
            "item_name": row.get("item_name", ""),
            "itemcode": "",
            "kind_name": row.get("category_name", ""),
            "kindcode": "",
            "rank": "",
            "unit": row.get("unit", ""),
            "day1": str(row.get("price_date", "")),
            "dpr1": str(row.get("price", "")),
            "day2": "",
            "dpr2": "",
            "day3": "",
            "dpr3": "",
            "day4": "",
            "dpr4": "",
            "day5": "",
            "dpr5": "",
            "day6": "",
            "dpr6": "",
            "day7": "",
            "dpr7": "",
            "source": "KAMIS",
        }
        for row in rows
    ]


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


def _safe_detail(detail: str) -> str:
    blocked = ("KAMIS_API_KEY=", "KAMIS_USER_ID=", "p_cert_key", "p_cert_id")
    if any(token in detail for token in blocked):
        return "KAMIS request failed. Credentials were not included in the response."
    return detail
