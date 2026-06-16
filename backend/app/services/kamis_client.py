from datetime import date, timedelta

import httpx

from app.config import get_settings


class KamisClient:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.api_key = self.settings.kamis_api_key
        self.user_id = self.settings.kamis_user_id
        self.base_url = "https://example.kamis.api/replace-with-real-endpoint"

    def fetch_agri_prices(self) -> list[dict]:
        if self.settings.use_dummy_data or not self.api_key:
            return self._dummy_agri_prices()

        return self._fetch_from_real_api()

    def _fetch_from_real_api(self) -> list[dict]:
        # 실제 KAMIS API 스펙에 맞춰 URL, params, 응답 변환만 교체하면 됩니다.
        params = {
            "p_cert_key": self.api_key,
            "p_cert_id": self.user_id,
            "p_returntype": "json",
        }
        response = httpx.get(self.base_url, params=params, timeout=20)
        response.raise_for_status()
        payload = response.json()
        return self._normalize_response(payload)

    def _normalize_response(self, payload: dict) -> list[dict]:
        # TODO: 실제 KAMIS 응답 필드명에 맞춰 변환하세요.
        raise NotImplementedError("실제 KAMIS API 응답 변환 로직을 구현하세요.")

    def _dummy_agri_prices(self) -> list[dict]:
        today = date.today()
        rows: list[dict] = []
        samples = [
            ("사과", "과일", "가락시장", "10kg", 48000),
            ("배추", "채소", "가락시장", "10kg", 12500),
            ("쌀", "곡물", "양재시장", "20kg", 59000),
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
