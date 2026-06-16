from datetime import date, datetime

from pydantic import BaseModel, ConfigDict


class StockPriceResponse(BaseModel):
    id: int
    market: str
    stock_code: str
    stock_name: str
    trade_date: date
    open_price: float
    high_price: float
    low_price: float
    close_price: float
    volume: int
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
