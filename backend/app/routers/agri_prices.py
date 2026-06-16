from fastapi import APIRouter, Depends
from sqlalchemy import desc
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.agri_price import AgriPrice
from app.schemas.agri_price import AgriPriceResponse

router = APIRouter()


@router.get("/agri-prices", response_model=list[AgriPriceResponse])
def get_agri_prices(db: Session = Depends(get_db)) -> list[AgriPrice]:
    return (
        db.query(AgriPrice)
        .order_by(desc(AgriPrice.price_date), AgriPrice.item_name)
        .limit(300)
        .all()
    )
