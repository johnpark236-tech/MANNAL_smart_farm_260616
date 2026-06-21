from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.database import create_tables
from app.routers import agri_prices, collect, health, prices, stocks, weather, web

app = FastAPI(title="KRX KAMIS Dashboard API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def on_startup() -> None:
    create_tables()


app.include_router(health.router, prefix="/api", tags=["health"])
app.include_router(stocks.router, prefix="/api", tags=["stocks"])
app.include_router(agri_prices.router, prefix="/api", tags=["agri-prices"])
app.include_router(prices.router, prefix="/api", tags=["prices"])
app.include_router(collect.router, prefix="/api", tags=["collect"])
app.include_router(weather.router, prefix="/api", tags=["weather"])
app.include_router(web.router, tags=["web"])
