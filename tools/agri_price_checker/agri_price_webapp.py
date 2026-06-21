import csv
import json
import os
import re
import socket
import threading
import urllib.parse
import urllib.request
import webbrowser
from dataclasses import asdict, dataclass
from datetime import date, datetime
from html import escape, unescape
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


GG_PRICE_URL = "https://nongup.gg.go.kr/data/62"
DEFAULT_ITEMS = "청상추,고구마"
DEFAULT_PORT = 14124


def get_lan_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "PC_IP"
    finally:
        sock.close()


def phone_url(port: int) -> str:
    return f"http://{get_lan_ip()}:{port}"


@dataclass
class PriceRow:
    item: str
    market: str
    basis_date: str
    unit: str
    price: int
    change: int
    source: str

    @property
    def trend_label(self) -> str:
        if self.change > 0:
            return f"{self.change:,}원 상승"
        if self.change < 0:
            return f"{abs(self.change):,}원 하락"
        return "변동 없음"

    @property
    def interpretation(self) -> str:
        if self.change > 0:
            return "전일보다 강세"
        if self.change < 0:
            if abs(self.change) >= max(3000, self.price * 0.15):
                return "전일보다 크게 약세"
            return "전일보다 약세"
        return "전일과 보합"

    def price_per_kg(self) -> float | None:
        kg = parse_unit_kg(self.unit)
        if not kg:
            return None
        return self.price / kg

    def price_per_100g(self) -> float | None:
        per_kg = self.price_per_kg()
        if per_kg is None:
            return None
        return per_kg / 10

    def to_api_dict(self) -> dict:
        data = asdict(self)
        data.update(
            {
                "trend_label": self.trend_label,
                "interpretation": self.interpretation,
                "basis": f"{self.market} 도매, {self.unit}",
                "price_text": f"{self.price:,}원",
                "per_kg_text": format_optional_price(self.price_per_kg(), "원/kg"),
                "per_100g_text": format_optional_price(self.price_per_100g(), "원/100g"),
            }
        )
        return data


def format_optional_price(value: float | None, suffix: str) -> str:
    if value is None:
        return "-"
    return f"약 {value:,.0f}{suffix}"


def fetch_text(url: str, params: dict[str, str] | None = None, timeout: int = 15) -> str:
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 agri-price-checker/1.0",
            "Accept": "text/html,application/json,*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def parse_int(value: str) -> int:
    sign = -1 if "▼" in value or value.strip().startswith("-") else 1
    digits = re.sub(r"[^0-9]", "", value)
    if not digits:
        return 0
    return sign * int(digits)


def parse_unit_kg(unit: str) -> float | None:
    match = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*kg", unit, re.IGNORECASE)
    if match:
        return float(match.group(1))
    gram_match = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*g", unit, re.IGNORECASE)
    if gram_match:
        return float(gram_match.group(1)) / 1000
    return None


def parse_gg_price_page(html: str, wanted_items: list[str]) -> list[PriceRow]:
    text = unescape(re.sub(r"<[^>]+>", " ", html))
    text = re.sub(r"\s+", " ", text)
    rows: list[PriceRow] = []
    pattern = re.compile(
        r"(?P<market>[\w가-힣·()]+시장)\((?P<mmdd>\d{2}/\d{2})\)\s+"
        r"(?P<item>.+?)\s+"
        r"(?P<unit>(?:\d+(?:\.\d+)?\s*)?(?:kg|g|개|속|포기|망|상자|천원|월·천원)[^0-9,▲▼-]*)\s+"
        r"(?P<price>[0-9,]+)\s+"
        r"(?P<change>-?[0-9,]+[▲▼]?)"
    )
    current_year = date.today().year
    for match in pattern.finditer(text):
        item = match.group("item").strip()
        if not any(wanted in item or item in wanted for wanted in wanted_items):
            continue
        mmdd = match.group("mmdd")
        rows.append(
            PriceRow(
                item=item,
                market=match.group("market"),
                basis_date=f"{current_year}-{mmdd.replace('/', '-')}",
                unit=match.group("unit").strip(),
                price=parse_int(match.group("price")),
                change=parse_int(match.group("change")),
                source=GG_PRICE_URL,
            )
        )
    return rows


def lookup_prices(item_text: str) -> tuple[list[PriceRow], list[str]]:
    wanted_items = [item.strip() for item in item_text.split(",") if item.strip()] or ["청상추", "고구마"]
    logs = ["API 인증키 없이 경기도농업기술원 공개 가격정보 화면을 조회합니다."]
    rows = parse_gg_price_page(fetch_text(GG_PRICE_URL), wanted_items)
    if rows:
        latest = max(row.basis_date for row in rows)
        return [row for row in rows if row.basis_date == latest], logs
    logs.append("지정한 품목을 찾지 못했습니다.")
    return [], logs


def build_report(rows: list[PriceRow], logs: list[str]) -> str:
    today = date.today().strftime("%Y년 %m월 %d일")
    if not rows:
        return "\n".join(logs + ["", f"{today} 현재 조회 가능한 시세를 찾지 못했습니다."])
    latest = max(row.basis_date for row in rows)
    lines = [
        "금일 시세는 “도매/소매”와 “지역”에 따라 차이가 큽니다.",
        "우선 전국 공시자료와 공개 가격정보 기준으로 상추와 고구마의 오늘 가격을 확인해 정리하겠습니다.",
        "",
        f"{today} 현재 확인 가능한 최신 시세입니다.",
        f"오늘 공시 화면 기준 최신 거래 기준일은 {latest}입니다.",
        "",
        "품목\t기준\t금일 확인 시세\t전일 대비\t해석",
    ]
    for row in rows:
        lines.append(f"{row.item}\t{row.market} 도매, {row.unit}\t{row.price:,}원\t{row.trend_label}\t{row.interpretation}")
    lines.extend(["", "환산하면", "", "품목\tkg당 환산\t100g 환산"])
    for row in rows:
        lines.append(f"{row.item}\t{format_optional_price(row.price_per_kg(), '원/kg')}\t{format_optional_price(row.price_per_100g(), '원/100g')}")
    falling = [row.item for row in rows if row.change < 0]
    rising = [row.item for row in rows if row.change > 0]
    lines.extend(["", "판단:"])
    if falling and not rising:
        lines.append(f"오늘 기준으로는 {', '.join(falling)} 모두 전일 대비 하락세입니다. 농가 판매 관점에서는 오늘 출하보다 1~3일 더 흐름 확인이 좋아 보입니다.")
    elif rising and not falling:
        lines.append(f"오늘 기준으로는 {', '.join(rising)} 품목이 전일 대비 상승세입니다.")
    else:
        lines.append("품목별 등락이 엇갈립니다. 출하 판단은 품목별로 나누어 확인하는 편이 좋습니다.")
    lines.extend(["", "출처:"])
    for source in sorted({row.source for row in rows}):
        lines.append(source)
    return "\n".join(lines)


HTML = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>금일 농산물 시세 확인</title>
<style>
body{margin:0;background:#f5f7f4;color:#1f2a24;font-family:Malgun Gothic,Segoe UI,sans-serif}
.wrap{max-width:1100px;margin:0 auto;padding:28px 18px}
h1{font-size:26px;margin:0 0 18px}
.toolbar{display:flex;gap:10px;align-items:center;margin-bottom:14px}
input{flex:1;min-width:220px;padding:11px 12px;border:1px solid #b8c4bb;border-radius:6px;font-size:15px;background:white}
button{padding:11px 14px;border:0;border-radius:6px;background:#246b4b;color:white;font-weight:700;cursor:pointer}
button.secondary{background:#46564d}
.status{min-height:22px;color:#526258;margin:8px 0 14px}
table{width:100%;border-collapse:collapse;background:white;border:1px solid #dde4df}
th,td{border-bottom:1px solid #dde4df;padding:10px;text-align:center;font-size:14px}
th{background:#e8efe9}
pre{white-space:pre-wrap;background:white;border:1px solid #dde4df;padding:16px;line-height:1.65;min-height:260px}
.source{font-size:13px;color:#526258;margin-top:12px}
.phone-url{margin:0 0 14px;padding:12px;background:#fff;border:1px solid #dde4df;color:#1f2a24}
.phone-url strong{display:inline-block;margin-right:8px}
</style>
</head>
<body>
<main class="wrap">
<h1>금일 농산물 시세 확인</h1>
<div class="phone-url"><strong>스마트폰 접속 주소</strong><span id="phone-url">__PHONE_URL__</span></div>
<div class="toolbar">
<input id="items" value="청상추,고구마" aria-label="조회 품목">
<button onclick="lookup()">시세 조회</button>
<button class="secondary" onclick="downloadCsv()">CSV 저장</button>
</div>
<div id="status" class="status">조회 버튼을 누르면 최신 공개 시세를 확인합니다.</div>
<table>
<thead><tr><th>품목</th><th>기준</th><th>금일 확인 시세</th><th>전일 대비</th><th>해석</th><th>kg당 환산</th><th>100g 환산</th></tr></thead>
<tbody id="rows"></tbody>
</table>
<pre id="report"></pre>
<div class="source">API 인증키 없이 경기도농업기술원 공개 가격정보 화면을 조회합니다.</div>
</main>
<script>
let latestRows = [];
async function lookup(){
  const status=document.getElementById('status');
  status.textContent='조회 중입니다...';
  try{
    const res=await fetch('/api/lookup?items='+encodeURIComponent(document.getElementById('items').value));
    const data=await res.json();
    if(!res.ok) throw new Error(data.error || '조회 실패');
    latestRows=data.rows;
    document.getElementById('rows').innerHTML=data.rows.map(r=>`<tr><td>${r.item}</td><td>${r.basis}</td><td>${r.price_text}</td><td>${r.trend_label}</td><td>${r.interpretation}</td><td>${r.per_kg_text}</td><td>${r.per_100g_text}</td></tr>`).join('');
    document.getElementById('report').textContent=data.report;
    status.textContent=`${data.rows.length}개 품목 조회 완료`;
  }catch(err){status.textContent=err.message;}
}
function downloadCsv(){ window.location='/api/csv?items='+encodeURIComponent(document.getElementById('items').value); }
lookup();
</script>
</body>
</html>"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/":
            port = self.server.server_address[1]
            self.send_text(HTML.replace("__PHONE_URL__", phone_url(port)), "text/html; charset=utf-8")
            return
        if parsed.path == "/api/lookup":
            params = urllib.parse.parse_qs(parsed.query)
            self.handle_lookup(params.get("items", [DEFAULT_ITEMS])[0])
            return
        if parsed.path == "/api/csv":
            params = urllib.parse.parse_qs(parsed.query)
            self.handle_csv(params.get("items", [DEFAULT_ITEMS])[0])
            return
        self.send_error(404)

    def handle_lookup(self, items: str) -> None:
        try:
            rows, logs = lookup_prices(items)
            payload = {
                "rows": [row.to_api_dict() for row in rows],
                "logs": logs,
                "report": build_report(rows, logs),
            }
            self.send_json(payload)
        except Exception as exc:
            self.send_json({"error": str(exc)}, status=500)

    def handle_csv(self, items: str) -> None:
        rows, _logs = lookup_prices(items)
        lines = [["품목", "시장", "기준일", "단위", "가격", "전일대비", "출처"]]
        lines.extend([[r.item, r.market, r.basis_date, r.unit, r.price, r.change, r.source] for r in rows])
        body_lines = []
        for line in lines:
            escaped = []
            for value in line:
                text = str(value).replace('"', '""')
                escaped.append(f'"{text}"')
            body_lines.append(",".join(escaped))
        body = "\ufeff" + "\r\n".join(body_lines)
        filename = f"agri_prices_{datetime.now().strftime('%Y%m%d_%H%M')}.csv"
        self.send_response(200)
        self.send_header("Content-Type", "text/csv; charset=utf-8")
        self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
        self.end_headers()
        self.wfile.write(body.encode("utf-8"))

    def send_text(self, text: str, content_type: str) -> None:
        body = text.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_json(self, payload: dict, status: int = 200) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: object) -> None:
        return


def main() -> None:
    port = int(os.getenv("AGRI_PRICE_PORT", str(DEFAULT_PORT)) or str(DEFAULT_PORT))
    bind_host = os.getenv("AGRI_PRICE_HOST", "0.0.0.0")
    server = ThreadingHTTPServer((bind_host, port), Handler)
    host, port = server.server_address
    local_url = f"http://127.0.0.1:{port}"
    mobile_url = phone_url(port)
    if os.getenv("AGRI_PRICE_NO_BROWSER", "").strip() != "1":
        threading.Timer(0.4, lambda: webbrowser.open(local_url)).start()
    print(f"금일 농산물 시세 확인 UI: {local_url}")
    print(f"스마트폰 접속 주소: {mobile_url}")
    print("종료하려면 이 창을 닫거나 Ctrl+C를 누르세요.")
    server.serve_forever()


if __name__ == "__main__":
    main()
