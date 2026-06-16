from datetime import date, datetime

from pydantic import BaseModel, ConfigDict


class AgriPriceResponse(BaseModel):
    id: int
    item_name: str
    category_name: str
    price_date: date
    market_name: str
    unit: str
    price: float
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
