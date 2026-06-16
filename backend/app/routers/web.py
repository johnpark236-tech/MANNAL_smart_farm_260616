from html import escape
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy import desc
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.agri_price import AgriPrice
from app.models.journal_entry import JournalEntry
from app.models.stock_price import StockPrice
from app.services.collector import collect_agri_prices, collect_stock_prices

router = APIRouter()


def money(value: float) -> str:
    return f"{float(value):,.0f}원"


def number(value: int) -> str:
    return f"{int(value):,}"


def layout(title: str, body: str, message: str = "") -> HTMLResponse:
    message_html = f'<div class="message">{escape(message)}</div>' if message else ""
    html = f"""
<!doctype html>
<html lang="ko">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{escape(title)}</title>
    <style>
      * {{ box-sizing: border-box; }}
      body {{
        margin: 0;
        background: #f6f7f9;
        color: #172033;
        font-family: "Noto Sans KR", "Malgun Gothic", Arial, sans-serif;
      }}
      header {{
        background: #fff;
        border-bottom: 1px solid #d9dee8;
        padding: 28px 32px;
      }}
      h1 {{ font-size: 34px; margin: 0 0 8px; }}
      header p {{ color: #596579; font-size: 20px; margin: 0; }}
      nav {{
        display: flex;
        gap: 8px;
        overflow-x: auto;
        padding: 18px 32px;
        background: #eef1f5;
        border-bottom: 1px solid #d9dee8;
      }}
      nav a {{
        min-width: 132px;
        border: 1px solid #b8c1d1;
        border-radius: 8px;
        background: #fff;
        color: #172033;
        font-size: 19px;
        font-weight: 700;
        padding: 14px 18px;
        text-align: center;
        text-decoration: none;
      }}
      main {{ padding: 28px 32px 44px; }}
      .grid {{
        display: grid;
        gap: 18px;
        grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      }}
      .card, .panel, .message {{
        background: #fff;
        border: 1px solid #d9dee8;
        border-radius: 8px;
        padding: 24px;
      }}
      .card h2, .panel h2 {{ font-size: 24px; margin: 0 0 16px; }}
      .card strong {{ display: block; font-size: 30px; margin-bottom: 12px; }}
      .card p {{ color: #596579; font-size: 18px; line-height: 1.5; margin: 0; }}
      .message {{
        background: #eef8f1;
        border-color: #b9dfc5;
        font-size: 19px;
        font-weight: 700;
        margin-bottom: 20px;
      }}
      .table-wrap {{
        overflow-x: auto;
        background: #fff;
        border: 1px solid #d9dee8;
        border-radius: 8px;
      }}
      table {{ width: 100%; border-collapse: collapse; min-width: 720px; }}
      th, td {{
        border-bottom: 1px solid #e4e8ef;
        font-size: 18px;
        padding: 16px;
        text-align: left;
        white-space: nowrap;
      }}
      th {{ background: #f0f3f7; color: #24324a; font-weight: 800; }}
      .buttons {{ display: flex; flex-wrap: wrap; gap: 14px; }}
      button {{
        border: 0;
        border-radius: 8px;
        color: #fff;
        cursor: pointer;
        font-size: 21px;
        font-weight: 800;
        min-height: 58px;
        padding: 16px 22px;
      }}
      .blue {{ background: #1f4e79; }}
      .green {{ background: #2f6f4e; }}
      .chart {{
        width: 100%;
        height: 260px;
        margin: 10px 0 24px;
      }}
      .journal-form {{
        display: grid;
        gap: 18px;
        max-width: 920px;
      }}
      .journal-form label {{
        display: grid;
        gap: 8px;
        font-size: 20px;
        font-weight: 800;
      }}
      .journal-form input,
      .journal-form select,
      .journal-form textarea {{
        border: 1px solid #b8c1d1;
        border-radius: 8px;
        font: inherit;
        font-size: 20px;
        padding: 14px;
        width: 100%;
      }}
      .journal-form textarea {{
        line-height: 1.6;
        resize: vertical;
      }}
      .journal-list {{
        margin-top: 24px;
      }}
      .empty {{ font-size: 19px; padding: 24px; }}
      @media (max-width: 720px) {{
        header, nav, main {{ padding-left: 18px; padding-right: 18px; }}
        h1 {{ font-size: 28px; }}
        header p, th, td {{ font-size: 17px; }}
      }}
    </style>
  </head>
  <body>
    <header>
      <h1>시장 가격 대시보드</h1>
      <p>농산물 가격과 영농 일지를 한 화면에서 확인합니다.</p>
    </header>
    <nav>
      <a href="/">대시보드</a>
      <a href="/agri-page">농산물 가격</a>
      <a href="/collect-page">수집 관리</a>
      <a href="/journal-page">일지 작성</a>
    </nav>
    <main>
      {message_html}
      {body}
    </main>
  </body>
</html>
"""
    return HTMLResponse(html)


def line_chart(points: list[tuple[str, float]], color: str) -> str:
    if len(points) < 2:
        return '<div class="empty">그래프를 표시할 데이터가 부족합니다.</div>'

    width = 900
    height = 260
    pad = 36
    values = [point[1] for point in points]
    min_value = min(values)
    max_value = max(values)
    spread = max(max_value - min_value, 1)
    step = (width - pad * 2) / (len(points) - 1)

    coords = []
    for index, (_, value) in enumerate(points):
        x = pad + step * index
        y = height - pad - ((value - min_value) / spread) * (height - pad * 2)
        coords.append((x, y))

    polyline = " ".join(f"{x:.1f},{y:.1f}" for x, y in coords)
    labels = "".join(
        f'<text x="{x:.1f}" y="{height - 8}" font-size="13" text-anchor="middle">{escape(label[5:])}</text>'
        for (x, _), (label, _) in zip(coords, points)
    )
    dots = "".join(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="5" fill="{color}" />' for x, y in coords)

    return f"""
<svg class="chart" viewBox="0 0 {width} {height}" role="img" aria-label="가격 변화 그래프">
  <line x1="{pad}" y1="{height - pad}" x2="{width - pad}" y2="{height - pad}" stroke="#cbd5e1" />
  <line x1="{pad}" y1="{pad}" x2="{pad}" y2="{height - pad}" stroke="#cbd5e1" />
  <polyline points="{polyline}" fill="none" stroke="{color}" stroke-width="4" />
  {dots}
  {labels}
</svg>
"""


@router.get("/", response_class=HTMLResponse)
def dashboard(db: Session = Depends(get_db), message: str = "") -> HTMLResponse:
    agri_prices = db.query(AgriPrice).order_by(desc(AgriPrice.price_date)).limit(300).all()
    agri_count = len({row.item_name for row in agri_prices})
    latest_times = [row.created_at for row in agri_prices if row.created_at]
    latest = max(latest_times).strftime("%Y-%m-%d %H:%M:%S") if latest_times else "아직 수집 전"

    body = f"""
<section class="grid">
  <div class="card">
    <h2>농산물 가격</h2>
    <strong>{agri_count}개 품목</strong>
    <p>{len(agri_prices)}건의 가격 데이터가 저장되어 있습니다.</p>
  </div>
  <div class="card">
    <h2>최근 수집 시간</h2>
    <strong>{escape(latest)}</strong>
    <p>수집 버튼 실행 후 갱신됩니다.</p>
  </div>
</section>
"""
    return layout("시장 가격 대시보드", body, message)


@router.get("/stocks-page", response_class=HTMLResponse)
def stocks_page(db: Session = Depends(get_db), message: str = "") -> HTMLResponse:
    rows = db.query(StockPrice).order_by(desc(StockPrice.trade_date), StockPrice.stock_name).limit(300).all()
    first_name = rows[0].stock_name if rows else ""
    chart_points = [
        (row.trade_date.isoformat(), float(row.close_price))
        for row in sorted(rows, key=lambda item: item.trade_date)
        if row.stock_name == first_name
    ]
    table_rows = "".join(
        f"""
<tr>
  <td>{escape(row.stock_name)}</td>
  <td>{escape(row.stock_code)}</td>
  <td>{row.trade_date.isoformat()}</td>
  <td>{money(row.close_price)}</td>
  <td>{number(row.volume)}</td>
</tr>
"""
        for row in rows
    )
    table = (
        f"""
<div class="panel">
  <h2>{escape(first_name)} 종가 변화</h2>
  {line_chart(chart_points, "#2563eb")}
</div>
<div class="table-wrap">
  <table>
    <thead><tr><th>종목명</th><th>종목코드</th><th>날짜</th><th>종가</th><th>거래량</th></tr></thead>
    <tbody>{table_rows}</tbody>
  </table>
</div>
"""
        if rows
        else '<div class="empty">주식 데이터가 없습니다. 수집 관리 화면에서 수집을 실행하세요.</div>'
    )
    return layout("주식 시세", table, message)


@router.get("/agri-page", response_class=HTMLResponse)
def agri_page(db: Session = Depends(get_db), message: str = "") -> HTMLResponse:
    rows = db.query(AgriPrice).order_by(desc(AgriPrice.price_date), AgriPrice.item_name).limit(300).all()
    first_name = rows[0].item_name if rows else ""
    chart_points = [
        (row.price_date.isoformat(), float(row.price))
        for row in sorted(rows, key=lambda item: item.price_date)
        if row.item_name == first_name
    ]
    table_rows = "".join(
        f"""
<tr>
  <td>{escape(row.item_name)}</td>
  <td>{escape(row.market_name)}</td>
  <td>{escape(row.unit)}</td>
  <td>{money(row.price)}</td>
  <td>{row.price_date.isoformat()}</td>
</tr>
"""
        for row in rows
    )
    table = (
        f"""
<div class="panel">
  <h2>{escape(first_name)} 가격 변화</h2>
  {line_chart(chart_points, "#16a34a")}
</div>
<div class="table-wrap">
  <table>
    <thead><tr><th>품목명</th><th>시장명</th><th>단위</th><th>가격</th><th>날짜</th></tr></thead>
    <tbody>{table_rows}</tbody>
  </table>
</div>
"""
        if rows
        else '<div class="empty">농산물 데이터가 없습니다. 수집 관리 화면에서 수집을 실행하세요.</div>'
    )
    return layout("농산물 가격", table, message)


@router.get("/collect-page", response_class=HTMLResponse)
def collect_page(message: str = "") -> HTMLResponse:
    body = """
<section class="panel">
  <h2>데이터 수집 관리</h2>
  <div class="buttons">
    <form method="post" action="/web-collect">
      <input type="hidden" name="target" value="agri">
      <button class="green" type="submit">농산물 가격 수집</button>
    </form>
  </div>
</section>
"""
    return layout("수집 관리", body, message)


@router.get("/journal-page", response_class=HTMLResponse)
def journal_page(db: Session = Depends(get_db), message: str = "") -> HTMLResponse:
    rows = db.query(JournalEntry).order_by(desc(JournalEntry.created_at)).limit(100).all()
    table_rows = "".join(
        f"""
<tr>
  <td>{entry.created_at.strftime("%Y-%m-%d %H:%M")}</td>
  <td>{escape(entry.category)}</td>
  <td>{escape(entry.title)}</td>
  <td>{escape(entry.content).replace(chr(10), "<br>")}</td>
</tr>
"""
        for entry in rows
    )
    journal_list = (
        f"""
<div class="table-wrap">
  <table>
    <thead><tr><th>작성 시간</th><th>구분</th><th>제목</th><th>내용</th></tr></thead>
    <tbody>{table_rows}</tbody>
  </table>
</div>
"""
        if rows
        else '<div class="empty">아직 작성된 일지가 없습니다.</div>'
    )
    body = f"""
<section class="panel">
  <h2>일지 작성</h2>
  <form method="post" action="/journal-page" class="journal-form">
    <label>
      구분
      <select name="category">
        <option value="영농">영농</option>
        <option value="기타">기타</option>
      </select>
    </label>
    <label>
      제목
      <input name="title" type="text" maxlength="200" required placeholder="예: 오늘 시장 점검">
    </label>
    <label>
      내용
      <textarea name="content" rows="7" required placeholder="오늘 확인한 내용, 작업 내용, 다음 할 일을 적어보세요."></textarea>
    </label>
    <button class="blue" type="submit">일지 저장</button>
  </form>
</section>
<section class="panel journal-list">
  <h2>최근 일지</h2>
  {journal_list}
</section>
"""
    return layout("일지 작성", body, message)


@router.post("/journal-page")
def create_journal_entry(
    category: str = Form(...),
    title: str = Form(...),
    content: str = Form(...),
    db: Session = Depends(get_db),
) -> RedirectResponse:
    allowed_categories = {"영농", "기타"}
    safe_category = category if category in allowed_categories else "기타"
    entry = JournalEntry(
        category=safe_category,
        title=title.strip()[:200] or "제목 없음",
        content=content.strip(),
    )
    db.add(entry)
    db.commit()
    return RedirectResponse(
        f"/journal-page?{urlencode({'message': '일지가 저장되었습니다.'})}",
        status_code=303,
    )


@router.post("/web-collect")
def web_collect(target: str = Form(...), db: Session = Depends(get_db)) -> RedirectResponse:
    if target == "stocks":
        result = collect_stock_prices(db)
        page = "/stocks-page"
    else:
        result = collect_agri_prices(db)
        page = "/agri-page"

    message = f"{result['message']} 신규 {result['inserted']}건, 갱신 {result['updated']}건"
    return RedirectResponse(f"{page}?{urlencode({'message': message})}", status_code=303)
