from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import get_settings


class Base(DeclarativeBase):
    pass


settings = get_settings()

connect_args = {}
if settings.database_url.startswith("sqlite"):
    # SQLite는 Python만으로 바로 실행할 수 있어 초보자용 로컬 MVP에 편합니다.
    connect_args = {"check_same_thread": False}

engine = create_engine(settings.database_url, pool_pre_ping=True, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def create_tables() -> None:
    # MVP 단계에서는 앱 시작 시 테이블을 자동 생성합니다.
    # 운영 단계에서는 Alembic 마이그레이션으로 바꾸는 것이 좋습니다.
    import app.models  # noqa: F401

    Base.metadata.create_all(bind=engine)
