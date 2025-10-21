import sqlalchemy

DATABASE_URL = "sqlite:///./pois.db"

engine = sqlalchemy.create_engine(DATABASE_URL)
metadata = sqlalchemy.MetaData()

pois_table = sqlalchemy.Table(
    "pois",
    metadata,
    sqlalchemy.Column("id", sqlalchemy.Integer, primary_key=True),
    sqlalchemy.Column("name", sqlalchemy.String),
    sqlalchemy.Column("latitude", sqlalchemy.Float),
    sqlalchemy.Column("longitude", sqlalchemy.Float),
    sqlalchemy.Column("owner", sqlalchemy.String, nullable=True),
)

metadata.create_all(engine)

initial_pois = [
    {'id': 1, 'name': '浙商大-图书馆', 'latitude': 30.3152, 'longitude': 120.3715, 'owner': None},
    {'id': 2, 'name': '浙商大-体育中心', 'latitude': 30.3168, 'longitude': 120.3730, 'owner': 'player_a'},
    {'id': 3, 'name': '浙商大-综合楼', 'latitude': 30.3145, 'longitude': 120.3700, 'owner': None},
    {'id': 4, 'name': '浙商大-学生活动中心', 'latitude': 30.3135, 'longitude': 120.3725, 'owner': None},
    {'id': 5, 'name': '浙商大-田径场', 'latitude': 30.3175, 'longitude': 120.3745, 'owner': None},

    {'id': 6, 'name': '文泽路地铁站-C口', 'latitude': 30.3120, 'longitude': 120.3708, 'owner': None},
    {'id': 7, 'name': '福雷德广场', 'latitude': 30.3180, 'longitude': 120.3680, 'owner': None},
    {'id': 8, 'name': '高沙商业街', 'latitude': 30.3195, 'longitude': 120.3710, 'owner': 'player_b'},
    {'id': 9, 'name': '学源街公交站', 'latitude': 30.3150, 'longitude': 120.3780, 'owner': None},
    {'id': 10, 'name': '工商大学云滨公寓', 'latitude': 30.3110, 'longitude': 120.3735, 'owner': None},

    {'id': 11, 'name': '金沙湖公园-北门', 'latitude': 30.3090, 'longitude': 120.3800, 'owner': None},
    {'id': 12, 'name': '中国计量大学-东门', 'latitude': 30.3220, 'longitude': 120.3650, 'owner': 'player_a'},
    {'id': 13, 'name': '杭州电子科技大学-体育馆', 'latitude': 30.3200, 'longitude': 120.3785, 'owner': None},
    {'id': 14, 'name': '邵逸夫医院(下沙院区)', 'latitude': 30.3060, 'longitude': 120.3750, 'owner': 'player_b'},
    {'id': 15, 'name': '和达城', 'latitude': 30.3140, 'longitude': 120.3850, 'owner': None},

    {'id': 16, 'name': '下沙银泰', 'latitude': 30.2980, 'longitude': 120.3500, 'owner': 'player_a'},
    {'id': 17, 'name': '金沙印象城', 'latitude': 30.3055, 'longitude': 120.3880, 'owner': None},
    {'id': 18, 'name': '奥特莱斯广场', 'latitude': 30.2850, 'longitude': 120.3750, 'owner': 'player_b'},
    {'id': 19, 'name': '沿江湿地公园', 'latitude': 30.2950, 'longitude': 120.4000, 'owner': None},
    {'id': 20, 'name': '杭州绕城高速-下沙出口', 'latitude': 30.3350, 'longitude': 120.3850, 'owner': None}
]

with engine.connect() as connection:
    print("正在清空旧数据...")
    connection.execute(pois_table.delete())
    print(f"正在插入 {len(initial_pois)} 条新的【浙商大】据点数据...")
    connection.execute(pois_table.insert(), initial_pois)
    connection.commit()

print("✅ 数据库和20个【浙商大】初始据点数据创建成功！")