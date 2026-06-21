from datetime import date, datetime, timedelta
import os
import subprocess
from typing import Any

import httpx

from app.config import get_settings


KAMIS_BASE_URL = "http://www.kamis.or.kr/service/price/xml.do"
KAMIS_SUCCESS_CODE = "000"
KAMIS_NO_DATA_CODE = "001"


class KamisApiError(RuntimeError):
    """Raised when KAMIS returns an error or an unusable response."""


class KamisClient:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.api_key = self.settings.kamis_api_key
        self.user_id = self.settings.kamis_user_id
        self.base_url = KAMIS_BASE_URL

    def fetch_agri_prices(self) -> list[dict]:
        if self.settings.use_dummy_data:
            return self._dummy_agri_prices()

        rows = self.fetch_daily_price_by_category()
        return [self._to_agri_price_row(row) for row in rows]

    def fetch_daily_price_by_category(
        self,
        *,
        regday: str | None = None,
        product_cls_code: str = "01",
        item_category_code: str = "200",
        country_code: str = "3411",
        convert_kg_yn: str = "N",
    ) -> list[dict]:
        if not self.api_key or not self.user_id:
            raise KamisApiError("KAMIS_API_KEY and KAMIS_USER_ID must be set in .env.")

        params = {
            "action": "dailyPriceByCategoryList",
            "p_cert_key": self.api_key,
            "p_cert_id": self.user_id,
            "p_returntype": "json",
            "p_product_cls_code": product_cls_code,
            "p_item_category_code": item_category_code,
            "p_country_code": country_code,
            "p_convert_kg_yn": convert_kg_yn,
        }
        if regday:
            params["p_regday"] = regday

        try:
            return self._request_daily_price_by_category(params)
        except KamisApiError as exc:
            if regday or "request parameters" not in str(exc):
                raise
            params["p_regday"] = date.today().isoformat()
            return self._request_daily_price_by_category(params)

    def _request_daily_price_by_category(self, params: dict[str, str]) -> list[dict]:
        try:
            response = httpx.get(self.base_url, params=params, timeout=10, follow_redirects=True)
            response.raise_for_status()
        except httpx.RequestError as exc:
            payload = self._request_with_powershell(params)
            return self._normalize_daily_price_response(payload)
        except httpx.HTTPStatusError as exc:
            raise KamisApiError(f"KAMIS API returned HTTP {exc.response.status_code}.") from exc

        try:
            payload = response.json()
        except ValueError as exc:
            raise KamisApiError("KAMIS API returned invalid JSON.") from exc

        return self._normalize_daily_price_response(payload)

    def _request_with_powershell(self, params: dict[str, str]) -> dict[str, Any]:
        request_url = str(httpx.URL(self.base_url, params=params))
        env = os.environ.copy()
        env["KAMIS_REQUEST_URL"] = request_url
        script = (
            "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; "
            "$r=Invoke-WebRequest -Uri $env:KAMIS_REQUEST_URL -UseBasicParsing -TimeoutSec 20; "
            "$r.Content"
        )
        try:
            result = subprocess.run(
                ["powershell", "-NoProfile", "-Command", script],
                capture_output=True,
                text=True,
                encoding="utf-8",
                timeout=25,
                env=env,
                check=False,
            )
        except (OSError, subprocess.SubprocessError) as exc:
            raise KamisApiError("Failed to connect to KAMIS API.") from exc

        if result.returncode != 0:
            raise KamisApiError("Failed to connect to KAMIS API.")

        try:
            return httpx.Response(200, content=result.stdout.encode("utf-8")).json()
        except ValueError as exc:
            raise KamisApiError("KAMIS API returned invalid JSON.") from exc

    def _normalize_daily_price_response(self, payload: dict[str, Any]) -> list[dict]:
        data = payload.get("data")
        if data is None:
            raise KamisApiError("KAMIS API response does not contain data.")

        if isinstance(data, list) and len(data) == 1 and isinstance(data[0], str):
            code = data[0]
            if code == KAMIS_NO_DATA_CODE:
                return []
            if code != KAMIS_SUCCESS_CODE:
                raise KamisApiError(self._message_for_code(code))
            return []

        if not isinstance(data, dict):
            raise KamisApiError("KAMIS API response data has an unexpected shape.")

        code = str(data.get("error_code") or data.get("code") or KAMIS_SUCCESS_CODE)
        if code == KAMIS_NO_DATA_CODE:
            return []
        if code != KAMIS_SUCCESS_CODE:
            raise KamisApiError(self._message_for_code(code))

        rows = data.get("item") or data.get("items") or data.get("list") or []
        if isinstance(rows, dict):
            rows = [rows]
        if not isinstance(rows, list):
            raise KamisApiError("KAMIS API item data has an unexpected shape.")

        normalized = [self._normalize_daily_price_item(row) for row in rows if isinstance(row, dict)]
        return [row for row in normalized if row["item_name"] or row["kind_name"]]

    def _normalize_daily_price_item(self, row: dict[str, Any]) -> dict:
        fields = [
            "item_name",
            "itemcode",
            "kind_name",
            "kindcode",
            "rank",
            "unit",
            "day1",
            "dpr1",
            "day2",
            "dpr2",
            "day3",
            "dpr3",
            "day4",
            "dpr4",
            "day5",
            "dpr5",
            "day6",
            "dpr6",
            "day7",
            "dpr7",
        ]
        normalized = {field: self._as_text(row.get(field)) for field in fields}
        normalized["itemcode"] = normalized["itemcode"] or self._as_text(row.get("item_code"))
        normalized["kindcode"] = normalized["kindcode"] or self._as_text(row.get("kind_code"))
        normalized["source"] = "KAMIS"
        return normalized

    def _to_agri_price_row(self, row: dict) -> dict:
        price_date = self._parse_kamis_date(row.get("day1")) or date.today()
        return {
            "item_name": row.get("item_name") or "",
            "category_name": "vegetables",
            "price_date": price_date,
            "market_name": "Cheonan",
            "unit": row.get("unit") or "",
            "price": self._parse_price(row.get("dpr1")),
        }

    def _message_for_code(self, code: str) -> str:
        messages = {
            "001": "KAMIS API returned no data.",
            "200": "KAMIS API rejected the request parameters.",
            "900": "KAMIS API authentication failed.",
        }
        return messages.get(code, f"KAMIS API returned error code {code}.")

    def _parse_kamis_date(self, value: Any) -> date | None:
        text = self._as_text(value)
        for fmt in ("%Y-%m-%d", "%Y.%m.%d", "%m/%d", "%m.%d"):
            try:
                parsed = datetime.strptime(text, fmt)
            except ValueError:
                continue
            if "%Y" in fmt:
                return parsed.date()
            return date(date.today().year, parsed.month, parsed.day)
        return None

    def _parse_price(self, value: Any) -> float:
        text = self._as_text(value).replace(",", "").replace("-", "").strip()
        if not text:
            return 0.0
        try:
            return float(text)
        except ValueError:
            return 0.0

    def _as_text(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()

    def _dummy_agri_prices(self) -> list[dict]:
        today = date.today()
        rows: list[dict] = []
        samples = [
            ("Apple", "Fruit", "Garak Market", "10kg", 48000),
            ("Cabbage", "Vegetables", "Garak Market", "10kg", 12500),
            ("Rice", "Grain", "Yangjae Market", "20kg", 59000),
        ]

        for day_offset in range(7, 0, -1):
            price_date = today - timedelta(days=day_offset)
            for item, category, market, unit, base_price in samples:
                rows.append(
                    {
                        "item_name": item,
                        "category_name": category,
                        "price_date": price_date,
                        "market_name": market,
                        "unit": unit,
                        "price": base_price + (7 - day_offset) * 180,
                    }
                )
        return rows


if __name__ == "__main__":
    rows = KamisClient().fetch_daily_price_by_category()
    print(f"KAMIS rows: {len(rows)}")
    if rows:
        preview = {key: rows[0].get(key) for key in ("item_name", "kind_name", "rank", "unit", "day1", "dpr1", "source")}
        print(preview)
