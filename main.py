import math
from typing import List, Optional, Union

import databases
import sqlalchemy
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from fastapi.responses import JSONResponse

DATABASE_URL = "sqlite:///./pois.db"
database = databases.Database(DATABASE_URL)
metadata = sqlalchemy.MetaData()

pois = sqlalchemy.Table(
    "pois",
    metadata,
    sqlalchemy.Column("id", sqlalchemy.Integer, primary_key=True),
    sqlalchemy.Column("name", sqlalchemy.String),
    sqlalchemy.Column("latitude", sqlalchemy.Float),
    sqlalchemy.Column("longitude", sqlalchemy.Float),
    sqlalchemy.Column("owner", sqlalchemy.String, nullable=True),
)

engine = sqlalchemy.create_engine(DATABASE_URL)
metadata.create_all(engine)

class Poi(BaseModel):
    id: int
    name: str
    latitude: float
    longitude: float
    owner: Optional[str] = None

class CaptureRequest(BaseModel):
    user_id: str
    latitude: float
    longitude: float


app = FastAPI()

origins = ["*"]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
async def startup():
    await database.connect()

@app.on_event("shutdown")
async def shutdown():
    await database.disconnect()
    
def calculate_distance(lat1, lon1, lat2, lon2):
    R = 6371000
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    a = math.sin(delta_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

def create_api_response(code: int, message: str, data: Union[dict, list, None] = None):
    return JSONResponse(
        status_code=200, 
        content={
            "code": code,
            "message": message,
            "data": data,
        }
    )

@app.get("/hello")
async def hello():
    """一个简单的测试接口，用于检查服务是否在线"""
    return create_api_response(code=0, message="后端服务在线！Hello from FastAPI!")

@app.get("/pois")
async def get_pois():
    """获取所有据点列表"""
    query = pois.select()
    results = await database.fetch_all(query)
    poi_list = [dict(row) for row in results]
    return create_api_response(code=0, message="获取据点列表成功", data=poi_list)

@app.post("/pois/{id}/capture")
async def capture_poi(id: int, request: CaptureRequest):
    """占领据点"""
    query = pois.select().where(pois.c.id == id)
    target_poi_row = await database.fetch_one(query)
    
    if not target_poi_row:
        return create_api_response(code=404, message=f"据点 (ID: {id}) 不存在。")
    
    target_poi = dict(target_poi_row)
        
    distance = calculate_distance(request.latitude, request.longitude, target_poi['latitude'], target_poi['longitude'])
    distance_threshold = 500
    if distance > distance_threshold:
        return create_api_response(code=400, message=f"距离太远，无法占领。当前距离: {round(distance, 2)}米。")
        
    if target_poi['owner'] == request.user_id:
        return create_api_response(code=409, message="你已经占领了该据点。")
        
    update_query = (
        pois.update()
        .where(pois.c.id == id)
        .values(owner=request.user_id)
    )
    await database.execute(update_query)
    
    updated_poi_row = await database.fetch_one(query)
    return create_api_response(code=0, message="占领成功！", data=dict(updated_poi_row))