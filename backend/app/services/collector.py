import pandas as pd
from sqlalchemy.orm import Session

from app.models.agri_price import AgriPrice
from app.models.stock_price import StockPrice
from app.services.kamis_client import KamisClient
from app.services.krx_client import KrxClient


def collect_stock_prices(db: Session) -> dict:
    rows = KrxClient().fetch_stock_prices()
    frame = pd.DataFrame(rows)
    inserted = 0
    updated = 0

    for row in frame.to_dict(orient="records"):
        existing = (
            db.query(StockPrice)
            .filter(
                StockPrice.market == row["market"],
                StockPrice.stock_code == row["stock_code"],
                StockPrice.trade_date == row["trade_date"],
            )
            .first()
        )

        if existing:
            for key, value in row.items():
                setattr(existing, key, value)
            updated += 1
        else:
            db.add(StockPrice(**row))
            inserted += 1

    db.commit()
    return {
        "status": "ok",
        "message": "주식 시세 수집이 완료되었습니다.",
        "inserted": inserted,
        "updated": updated,
    }


def collect_agri_prices(db: Session) -> dict:
    rows = KamisClient().fetch_agri_prices()
    frame = pd.DataFrame(rows)
    inserted = 0
    updated = 0

    for row in frame.to_dict(orient="records"):
        existing = (
            db.query(AgriPrice)
            .filter(
                AgriPrice.item_name == row["item_name"],
                AgriPrice.market_name == row["market_name"],
                AgriPrice.unit == row["unit"],
                AgriPrice.price_date == row["price_date"],
            )
            .first()
        )

        if existing:
            for key, value in row.items():
                setattr(existing, key, value)
            updated += 1
        else:
            db.add(AgriPrice(**row))
            inserted += 1

    db.commit()
    return {
        "status": "ok",
        "message": "농산물 가격 수집이 완료되었습니다.",
        "inserted": inserted,
        "updated": updated,
    }
