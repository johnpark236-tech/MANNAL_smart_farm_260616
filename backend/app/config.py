from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = "sqlite:///./market_dashboard.db"
    krx_api_key: str = ""
    kamis_api_key: str = ""
    kamis_user_id: str = ""
    use_dummy_data: bool = True

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8-sig", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
