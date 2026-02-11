import logging
import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from app.config import ALBUM_PATH, PROFILE_PATH, WEB_DIST_PATH, EEUM_DEBUG
from app.lifespan import build_lifespan
from app.routes import alerts, debug, device, slideshow, voice, wifi
from app.state import MonitorState

logger = logging.getLogger(__name__)

def create_app(state: MonitorState) -> FastAPI:
    """
    FastAPI 인스턴스를 생성하고 미들웨어/정적 서빙/라우트를 등록합니다.
    
    :param state: MonitorState
    :return: FastAPI
    """
    app = FastAPI(lifespan=build_lifespan(state))

    album_dir = os.path.abspath(ALBUM_PATH or "./album")
    os.makedirs(album_dir, exist_ok=True)
    app.mount("/album", StaticFiles(directory=album_dir), name="album")

    profile_dir = os.path.abspath(PROFILE_PATH or "./profile")
    os.makedirs(profile_dir, exist_ok=True)
    app.mount("/profile", StaticFiles(directory=profile_dir), name="profile")

    device.register(app, state)
    wifi.register(app, state)
    alerts.register(app, state)
    slideshow.register(app, state)
    voice.register(app, state)
    debug.register(app, state, enabled=EEUM_DEBUG)

    dist_dir = os.path.abspath(WEB_DIST_PATH or "./dist")
    if os.path.isdir(dist_dir):
        app.mount("/", StaticFiles(directory=dist_dir, html=True), name="dist")
        logger.info("[static] dist mounted dir=%s", dist_dir)
    else:
        logger.warning("[static] dist not found (skip mount) dir=%s", dist_dir)

    return app