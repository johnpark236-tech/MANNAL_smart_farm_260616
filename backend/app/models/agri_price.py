from datetime import date, datetime

from sqlalchemy import Date, DateTime, Integer, Numeric, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class AgriPrice(Base):
    __tablename__ = "agri_prices"
    __table_args__ = (
        UniqueConstraint(
            "item_name",
            "market_name",
            "unit",
            "price_date",
            name="uq_agri_price_daily",
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    item_name: Mapped[str] = mapped_column(String(100), index=True)
    category_name: Mapped[str] = mapped_column(String(100), index=True)
    price_date: Mapped[date] = mapped_column(Date, index=True)
    market_name: Mapped[str] = mapped_column(String(100), index=True)
    unit: Mapped[str] = mapped_column(String(30))
    price: Mapped[float] = mapped_column(Numeric(14, 2))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
