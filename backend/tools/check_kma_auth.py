from __future__ import annotations

import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote, unquote, urlencode
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from xml.etree import ElementTree

KMA_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst"
KST = timezone(timedelta(hours=9))


def load_env_key() -> str:
    env_path = Path(__file__).resolve().parents[1] / ".env"
    if not env_path.exists():
        return ""
    for line in env_path.read_text(encoding="utf-8-sig").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        name, value = stripped.split("=", 1)
        if name.strip() == "KMA_API_KEY":
            return value.strip().strip('"').strip("'")
    return ""


def base_params() -> dict[str, str]:
    base_dt = datetime.now(KST) - timedelta(hours=1)
    return {
        "numOfRows": "10",
        "pageNo": "1",
        "dataType": "JSON",
        "base_date": base_dt.strftime("%Y%m%d"),
        "base_time": f"{base_dt.hour:02d}00",
        "nx": "63",
        "ny": "110",
    }


def request_url(url: str) -> tuple[int, str]:
    request = Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urlopen(request, timeout=15) as response:
            body = response.read().decode("utf-8", errors="replace")
            return response.status, body
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        return exc.code, body
    except UnicodeEncodeError as exc:
        return 0, "URL contains non-ASCII characters; raw URL method cannot be sent safely."
    except URLError as exc:
        return 0, str(exc.reason)


def call_params(key: str, params: dict[str, str]) -> tuple[int, str]:
    query = urlencode({"serviceKey": key, **params})
    return request_url(f"{KMA_URL}?{query}")


def call_url_key(key: str, params: dict[str, str]) -> tuple[int, str]:
    query = urlencode(params)
    return request_url(f"{KMA_URL}?serviceKey={key}&{query}")


def parse_result(body: str) -> tuple[str, str]:
    try:
        data = json.loads(body)
        header = data.get("response", {}).get("header", {})
        return str(header.get("resultCode", "")), str(header.get("resultMsg", ""))
    except Exception:
        pass

    try:
        root = ElementTree.fromstring(body)
        code = root.findtext(".//resultCode") or ""
        message = root.findtext(".//resultMsg") or ""
        return code, message
    except Exception:
        return "", ""


def mask(text: str, keys: list[str]) -> str:
    masked = text
    for key in keys:
        if key:
            masked = masked.replace(key, "[KEY]")
    masked = re.sub(r"serviceKey=[^&\s]+", "serviceKey=[KEY]", masked)
    return masked[:300].replace("\r", " ").replace("\n", " ")


def main() -> int:
    raw_key = load_env_key()
    decoded_key = unquote(raw_key)
    encoded_key = quote(decoded_key, safe="")
    params = base_params()
    key_variants = [raw_key, decoded_key, encoded_key]

    print("KMA auth check started")
    print(f"key_loaded={bool(raw_key)}")
    print(f"key_length={len(raw_key)}")

    methods = [
        ("A_params_raw", lambda: call_params(raw_key, params)),
        ("B_params_unquote", lambda: call_params(decoded_key, params)),
        ("C_url_raw", lambda: call_url_key(raw_key, params)),
        ("D_url_encoded", lambda: call_url_key(encoded_key, params)),
    ]

    success_method = None
    for name, caller in methods:
        status, body = caller()
        result_code, result_msg = parse_result(body)
        print("")
        print(f"method={name}")
        print(f"http_status={status}")
        print(f"resultCode={result_code}")
        print(f"resultMsg={result_msg}")
        print(f"body_preview={mask(body, key_variants)}")
        if status == 200 and result_code == "00" and result_msg == "NORMAL_SERVICE" and success_method is None:
            success_method = name

    print("")
    print(f"SUCCESS_METHOD={success_method or 'None'}")
    if success_method is None:
        print("NEXT_CHECK=공공데이터포털에서 승인 상태, 서비스명, Decoding/Encoding 키, 활용기간을 확인하세요.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

