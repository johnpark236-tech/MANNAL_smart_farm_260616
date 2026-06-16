from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.schemas.collect import CollectResponse
from app.services.collector import collect_agri_prices, collect_stock_prices

router = APIRouter()


@router.post("/collect/stocks", response_model=CollectResponse)
def collect_stocks(db: Session = Depends(get_db)) -> CollectResponse:
    result = collect_stock_prices(db)
    return CollectResponse(**result)


@router.post("/collect/agri-prices", response_model=CollectResponse)
def collect_agri(db: Session = Depends(get_db)) -> CollectResponse:
    result = collect_agri_prices(db)
    return CollectResponse(**result)
