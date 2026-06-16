from pydantic import BaseModel


class CollectResponse(BaseModel):
    status: str
    message: str
    inserted: int
    updated: int
