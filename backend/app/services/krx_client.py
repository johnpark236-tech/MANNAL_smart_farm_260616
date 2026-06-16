from datetime import date, timedelta

import httpx

from app.config import get_settings


class KrxClient:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.api_key = self.settings.krx_api_key
        self.base_url = "https://example.krx.api/replace-with-real-endpoint"

    def fetch_stock_prices(self) -> list[dict]:
        if self.settings.use_dummy_data or not self.api_key:
            return self._dummy_stock_prices()

        return self._fetch_from_real_api()

    def _fetch_from_real_api(self) -> list[dict]:
        # 실제 KRX API 스펙에 맞춰 URL, headers, params, 응답 변환만 교체하면 됩니다.
        headers = {"Authorization": f"Bearer {self.api_key}"}
        params = {"market": "ALL"}
        response = httpx.get(self.base_url, headers=headers, params=params, timeout=20)
        response.raise_for_status()
        payload = response.json()
        return self._normalize_response(payload)

    def _normalize_response(self, payload: dict) -> list[dict]:
        # TODO: 실제 KRX 응답 필드명에 맞춰 변환하세요.
        raise NotImplementedError("실제 KRX API 응답 변환 로직을 구현하세요.")

    def _dummy_stock_prices(self) -> list[dict]:
        today = date.today()
        rows: list[dict] = []
        samples = [
            ("KOSPI", "005930", "삼성전자", 74000),
            ("KOSPI", "000660", "SK하이닉스", 212000),
            ("KOSDAQ", "035720", "카카오", 46800),
        ]

        for day_offset in range(7, 0, -1):
            trade_date = today - timedelta(days=day_offset)
            for market, code, name, base_price in samples:
                change = (7 - day_offset) * 250
                close_price = base_price + change
                rows.append(
                    {
                        "market": market,
                        "stock_code": code,
                        "stock_name": name,
                        "trade_date": trade_date,
                        "open_price": close_price - 300,
                        "high_price": close_price + 700,
                        "low_price": close_price - 900,
                        "close_price": close_price,
                        "volume": 1_000_000 + day_offset * 15_000,
                    }
                )
        return rows
