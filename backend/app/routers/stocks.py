from fastapi import APIRouter, Depends
from sqlalchemy import desc
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.stock_price import StockPrice
from app.schemas.stock import StockPriceResponse

router = APIRouter()


@router.get("/stocks", response_model=list[StockPriceResponse])
def get_stocks(db: Session = Depends(get_db)) -> list[StockPrice]:
    return (
        db.query(StockPrice)
        .order_by(desc(StockPrice.trade_date), StockPrice.stock_name)
        .limit(300)
        .all()
    )
