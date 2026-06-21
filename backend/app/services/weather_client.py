from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone
from typing import Any
from urllib.parse import unquote, urlencode

import httpx
from dotenv import load_dotenv

KMA_BASE_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0"
DEFAULT_REGION_NAME = "천안"
# 천안 격자 좌표 확인 필요
DEFAULT_NX = 63
DEFAULT_NY = 110
KST = timezone(timedelta(hours=9))
VILAGE_BASE_HOURS = (2, 5, 8, 11, 14, 17, 20, 23)


class WeatherApiError(Exception):
    """Raised when KMA weather data cannot be fetched or parsed."""


def fetch_farm_weather(
    region_name: str = DEFAULT_REGION_NAME,
    nx: int = DEFAULT_NX,
    ny: int = DEFAULT_NY,
) -> dict[str, Any]:
    client = WeatherClient()
    return client.fetch_farm_weather(region_name=region_name, nx=nx, ny=ny)


class WeatherClient:
    def __init__(self) -> None:
        load_dotenv()
        self.api_key = os.getenv("KMA_API_KEY", "").strip().strip('"').strip("'")
        self.base_url = KMA_BASE_URL

    def fetch_farm_weather(
        self,
        region_name: str = DEFAULT_REGION_NAME,
        nx: int = DEFAULT_NX,
        ny: int = DEFAULT_NY,
    ) -> dict[str, Any]:
        if not self.api_key:
            raise WeatherApiError("KMA_API_KEY is not configured.")

        ncst_items = self._fetch_items("getUltraSrtNcst", *self._ultra_ncst_base_time(), nx, ny)
        vilage_items = self._fetch_items("getVilageFcst", *self._vilage_base_time(), nx, ny)

        current = self._latest_values(ncst_items)
        forecast = self._first_forecast_values(vilage_items)
        weather = self._normalize_weather(current, forecast)
        advice = self._farm_advice(weather)
        summary = self._summary(region_name, weather, advice)

        return {
            "source": "KMA",
            "mode": "real",
            "region_name": region_name,
            "nx": nx,
            "ny": ny,
            "summary": summary,
            "farm_advice": advice,
            "weather": weather,
        }

    def _fetch_items(self, operation: str, base_date: str, base_time: str, nx: int, ny: int) -> list[dict[str, Any]]:
        params = {
            "pageNo": "1",
            "numOfRows": "1000",
            "dataType": "JSON",
            "base_date": base_date,
            "base_time": base_time,
            "nx": str(nx),
            "ny": str(ny),
        }
        url = f"{self.base_url}/{operation}"
        try:
            response = self._get_with_service_key(url, params)
            response.raise_for_status()
            payload = response.json()
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code == 401:
                raise WeatherApiError(
                    "KMA API authentication failed. Check KMA_API_KEY, encoding/decoding key type, and API approval status."
                ) from exc
            raise WeatherApiError(f"KMA API returned HTTP {exc.response.status_code}.") from exc
        except httpx.RequestError as exc:
            raise WeatherApiError("Failed to connect to KMA API.") from exc
        except ValueError as exc:
            raise WeatherApiError("KMA API returned invalid JSON.") from exc

        return self._extract_items(payload)

    def _get_with_service_key(self, url: str, params: dict[str, str]) -> httpx.Response:
        decoded_key = unquote(self.api_key)
        first_response = httpx.get(url, params={"serviceKey": decoded_key, **params}, timeout=15.0)
        if first_response.status_code != 401:
            return first_response

        query = urlencode(params)
        request_url = f"{url}?serviceKey={self.api_key}&{query}"
        return httpx.get(request_url, timeout=15.0)

    def _extract_items(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        response = payload.get("response", {})
        header = response.get("header", {})
        result_code = str(header.get("resultCode", ""))
        if result_code and result_code != "00":
            result_message = header.get("resultMsg") or "KMA API returned an error."
            raise WeatherApiError(str(result_message))

        items = response.get("body", {}).get("items", {}).get("item", [])
        if isinstance(items, dict):
            return [items]
        if isinstance(items, list):
            return items
        raise WeatherApiError("KMA API response data has an unexpected shape.")

    def _ultra_ncst_base_time(self) -> tuple[str, str]:
        now = datetime.now(KST) - timedelta(minutes=50)
        minute = 0 if now.minute < 40 else 30
        return now.strftime("%Y%m%d"), f"{now.hour:02d}{minute:02d}"

    def _vilage_base_time(self) -> tuple[str, str]:
        now = datetime.now(KST) - timedelta(minutes=20)
        base_hour = None
        for hour in reversed(VILAGE_BASE_HOURS):
            if now.hour >= hour:
                base_hour = hour
                break
        if base_hour is None:
            now = now - timedelta(days=1)
            base_hour = VILAGE_BASE_HOURS[-1]
        return now.strftime("%Y%m%d"), f"{base_hour:02d}00"

    def _latest_values(self, items: list[dict[str, Any]]) -> dict[str, str]:
        values: dict[str, str] = {}
        for item in items:
            category = str(item.get("category", ""))
            value = str(item.get("obsrValue", item.get("fcstValue", "")))
            if category:
                values[category] = value
        return values

    def _first_forecast_values(self, items: list[dict[str, Any]]) -> dict[str, str]:
        by_time: dict[str, dict[str, str]] = {}
        for item in items:
            fcst_date = str(item.get("fcstDate", ""))
            fcst_time = str(item.get("fcstTime", ""))
            category = str(item.get("category", ""))
            value = str(item.get("fcstValue", ""))
            if not fcst_date or not fcst_time or not category:
                continue
            key = f"{fcst_date}{fcst_time}"
            by_time.setdefault(key, {})[category] = value
        if not by_time:
            return {}
        return by_time[sorted(by_time.keys())[0]]

    def _normalize_weather(self, current: dict[str, str], forecast: dict[str, str]) -> dict[str, str]:
        return {
            "temperature": self._pick(current, forecast, "T1H", "TMP"),
            "humidity": self._pick(current, forecast, "REH", "REH"),
            "rain_probability": forecast.get("POP", ""),
            "wind_speed": self._pick(current, forecast, "WSD", "WSD"),
            "sky": self._sky_text(forecast.get("SKY")),
            "precipitation_type": self._pty_text(self._pick(current, forecast, "PTY", "PTY")),
        }

    def _pick(self, first: dict[str, str], second: dict[str, str], first_key: str, second_key: str) -> str:
        return first.get(first_key) or second.get(second_key) or ""

    def _summary(self, region_name: str, weather: dict[str, str], advice: str) -> str:
        temp = self._text_or_unknown(weather.get("temperature"), "도")
        humidity = self._text_or_unknown(weather.get("humidity"), "퍼센트")
        rain = self._text_or_unknown(weather.get("rain_probability"), "퍼센트")
        return f"오늘 {region_name} 농사 날씨입니다. 현재 기온은 {temp}, 습도는 {humidity}, 강수확률은 {rain}입니다. {advice}"

    def _farm_advice(self, weather: dict[str, str]) -> str:
        rain_probability = self._to_float(weather.get("rain_probability"))
        humidity = self._to_float(weather.get("humidity"))
        wind_speed = self._to_float(weather.get("wind_speed"))
        temperature = self._to_float(weather.get("temperature"))

        if rain_probability is not None and rain_probability >= 60:
            return "비 가능성이 있으니 물주기를 줄이고 배수로를 확인하세요."
        if humidity is not None and humidity >= 80:
            return "습도가 높아 병충해와 곰팡이 발생에 주의하세요."
        if wind_speed is not None and wind_speed >= 7:
            return "바람이 강하니 방제와 비닐하우스 고정 상태를 확인하세요."
        if temperature is not None and temperature >= 33:
            return "고온 피해와 작업자 온열질환에 주의하세요."
        if temperature is not None and temperature <= 5:
            return "저온 피해에 주의하세요."
        return "비 가능성이 낮으면 오전 관수 작업이 가능합니다."

    def _sky_text(self, code: str | None) -> str:
        return {"1": "맑음", "3": "구름많음", "4": "흐림"}.get(str(code or ""), "")

    def _pty_text(self, code: str | None) -> str:
        return {
            "0": "없음",
            "1": "비",
            "2": "비/눈",
            "3": "눈",
            "4": "소나기",
        }.get(str(code or ""), "")

    def _text_or_unknown(self, value: str | None, unit: str) -> str:
        if value is None or str(value).strip() == "":
            return "확인 필요"
        return f"{value}{unit}"

    def _to_float(self, value: str | None) -> float | None:
        if value is None or str(value).strip() == "":
            return None
        try:
            return float(str(value).replace("mm", "").replace("강수없음", "0").strip())
        except ValueError:
            return None




