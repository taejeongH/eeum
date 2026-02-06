import os
from fastapi.staticfiles import StaticFiles
import asyncio
import subprocess
import time
import logging
import json
from typing import Optional
from fastapi import FastAPI, Query
from fastapi.responses import StreamingResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from .config import AP_IFACE, STA_IFACE, ALBUM_PATH, PROFILE_PATH, WEB_DIST_PATH
from .ap_manager import async_get_ipv4_addr
from .state import MonitorState, Event, Command
from typing import Any, Dict, Optional, List, Literal
from .wifi_manager import (
        async_provision_connect_wlan0,
        async_bind_profile_to_wlan0,
        async_up_profile_on_wlan0,
        async_delete_profile,
        )
from .mqtt_client import MqttClient
from .monitor import refresh_wifi_scan, refresh_wifi_profiles
from .slideshow import emit_slide, next_slide, prev_slide, set_playing, get_current_item, build_album_item
from .audio_manager import AudioJob, AudioPrio
from .voice_player import (
    voice_path,
    emit_voice_done,
)
from .voice_duration import get_mp3_duration_sec
from .profile_cache import ensure_profile_cached
from .sync_gate import schedule_initial_sync

logger = logging.getLogger(__name__)

class EventIn(BaseModel):
    kind: str
    device_id: str
    data: Dict[str, Any]
    detected_at: Optional[float] = None

class TokenReq(BaseModel):
    token: str

class WifiConnectIn(BaseModel):
    ssid: str
    password: str

class WifiProfileConnectIn(BaseModel):
    name: str

class WifiDeleteProfileIn(BaseModel):
    name: str
  
class PlayReq(BaseModel):
    interval_sec: Optional[float] = None

class VoicePlayReq(BaseModel):
    id: int

def ok(data: Any = None):
    return {"ok": True, "reason": None, "data": data}

def fail(reason: str, data: Any = None):
    return {"ok": False, "reason": reason, "data": data}

class AckTarget(BaseModel):
    type: Literal["voice"]
    id: int

class AckReq(BaseModel):
    target: AckTarget
    action: Literal["play", "skip"]

class AckBatchItem(BaseModel):
    target: AckTarget
    action: Optional[Literal["play", "skip"]] = None

class AckBatchReq(BaseModel):
    mode: Literal["sequential"]
    default_action: Literal["play", "skip"]
    items: List[AckBatchItem]

class DevicePingIn(BaseModel):
    device_id: str
    kind: str = "pir"          # 기본 pir
    ts: float | None = None    # optional: device time

class DebugFallReq(BaseModel):
    level:int =1
    device_id:str |None =None
    location_id:str |None =None

class DebugAlarmReq(BaseModel):
    kind:Literal["medication","schedule"] ="schedule"
    content:str ="Test alarm"
    sent_at:float |None =None
    msg_id:str |None =None
    
EEUM_DEBUG = "1"

def create_app(state: MonitorState) -> FastAPI:
    app = FastAPI()

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
        expose_headers=["*"],  # SSE/기타 헤더 노출 필요할 때 대비
    )

    # ---- static: album ----
    album_dir = os.path.abspath(ALBUM_PATH or "./album")
    os.makedirs(album_dir, exist_ok=True)
    app.mount("/album", StaticFiles(directory=album_dir), name="album")

    # ---- static: profile ----
    profile_dir = os.path.abspath(PROFILE_PATH or "./profile")
    os.makedirs(profile_dir, exist_ok=True)
    app.mount("/profile", StaticFiles(directory=profile_dir), name="profile")

    if EEUM_DEBUG:
        @app.post("/debug/fall/trigger")
        async def debug_fall_trigger(body: DebugFallReq):
            did = (body.device_id or state.device_id or "EEUM-DEBUG").strip()
            now = time.time()
        
            ev = Event(
                kind="fall",
                device_id=did,
                data={
                    "event": "fall_detected",
                    "level": int(body.level or 1),
                    "location_id": body.location_id,
                },
                detected_at=now,
            )
        
            try:
                state.queue.put_nowait(ev)
            except asyncio.QueueFull:
                try:
                    state.queue.get_nowait()
                except Exception:
                    pass
                state.queue.put_nowait(ev)
        
            return {"ok": True, "queued": True, "device_id": did, "ts": now}

        
        @app.post("/debug/alarm/trigger")
        async def debug_alarm_trigger(body: DebugAlarmReq):
            did = (state.device_id or "EEUM-DEBUG").strip()
            now = time.time()
        
            payload = {
                "kind": body.kind,
                "content": body.content,
                "sent_at": float(body.sent_at) if body.sent_at is not None else float(now),
            }
            if body.msg_id:
                payload["msg_id"] = body.msg_id
        
            topic = f"eeum/device/{did}/alarm"
            cmd = Command(topic=topic, payload=payload)
        
            try:
                state.cmd_queue.put_nowait(cmd)
            except asyncio.QueueFull:
                try:
                    state.cmd_queue.get_nowait()
                except Exception:
                    pass
                state.cmd_queue.put_nowait(cmd)
        
            return {"ok": True, "queued": True, "topic": topic, "payload": payload}


    @app.get("/ping")
    def ping():
        return {"ok": True}
    
    @app.get("/ap/ip")
    async def ap_ip():
        return {"iface": AP_IFACE, "ip": await async_get_ipv4_addr(AP_IFACE)}

    @app.get("/status")
    def status():
        return {
            "alert": state.alert,
            "last_pir_ts": state.last_pir_ts,
            "timer_running": state.tasks.get("pir_no_motion") is not None or state.tasks.get("vision_exit_absence") is not None
        }

    @app.post("/api/device/ping")
    async def device_ping(body: DevicePingIn):
        did = (body.device_id or "").strip()
        if not did:
            return {"ok": False, "code": "bad_request", "message": "device_id required"}

        now = time.time()
        # seen 처리: last_seen_ts 갱신 + online True
        ds = state.device_store
        if ds:
            # ping은 '마지막 시각'만 갱신하면 되니 detected_at=now로 통일 추천
            await ds.async_mark_seen(did, now)

        state.last_event_by_device[did] = {
            "kind": body.kind,
            "device_id": did,
            "data": {"event": "ping"},
            "detected_at": now,
        }

        return {"ok": True, "device_id": did, "ts": now}

    @app.post("/api/wifi/ui/ping")
    async def wifi_ui_ping():
        state.wifi_ui_last_ping = time.time()
        return {"ok": True, "ts": state.wifi_ui_last_ping}

    @app.post("/eeum/token")
    async def set_token(req: TokenReq):
        await state.device_store.async_set_token(req.token)
        logger.info("[API] token received: mqtt_exists=%s", state.mqtt is not None)

        if state.mqtt is None:
            state.mqtt = MqttClient(
                inbound_queue=state.mqtt_inbound,
                loop=state.loop,
                token=req.token,
                link_getter=state.device_store.build_pir_link,
            )
            state.mqtt.activate()
        else:
            state.mqtt.set_token(req.token)
            state.mqtt.activate()

        scheduled = schedule_initial_sync(state, timeout_sec=60.0)
        logger.info("[API] initial_sync scheduled=%s", scheduled)

        return {"ok": True}

    @app.post("/eeum/event")
    async def event(data: EventIn):
        ev = Event(**data.model_dump(exclude_none=True))
        try:
            state.queue.put_nowait(ev)
        except asyncio.QueueFull:
            try:
                state.queue.get_nowait()
            except Exception:
                pass
            state.queue.put_nowait(ev)
        return {"ok": True}
    
    @app.get("/api/wifi/scan")
    async def wifi_scan(scan: bool = Query(False, description="True면 실제 rescan+list scan 수행")):
        logger.info("[API] wifi scan requested scan=%s", scan)

        if scan:
            if state.wifi_busy:
                return {
                    "ok": True,
                    "iface": STA_IFACE,
                    "active_ssid": state.wifi_active,
                    "aps": state.wifi_scan,
                    "ts": state.wifi_scan_ts,
                    "skipped": True,
                    "message": "wifi busy",
                }
            try:
                await refresh_wifi_scan(state)
            except Exception as e:
                logger.exception("[API] wifi scan failed")
                return {
                    "ok": False,
                    "iface": STA_IFACE,
                    "active_ssid": state.wifi_active,
                    "aps": state.wifi_scan,
                    "ts": state.wifi_scan_ts,
                    "error": str(e),
                }

        return {
            "ok": True,
            "iface": STA_IFACE,
            "active_ssid": state.wifi_active,
            "aps": state.wifi_scan,
            "ts": state.wifi_scan_ts,
        }

    
    @app.get("/api/wifi/active")
    async def wifi_active():
        return {
            "iface": STA_IFACE,
            "ssid": state.wifi_active,
            "ts": state.wifi_active_ts
        }

    @app.get("/api/wifi/profiles")
    async def wifi_profiles(refresh: bool = Query(False, description="True면 nmcli로 profiles 재수집")):
        if refresh:
            if state.wifi_busy:
                return {
                    "ok": True,
                    "iface": STA_IFACE,
                    "active_ssid": state.wifi_active,
                    "profiles": state.wifi_profiles,
                    "ts": state.wifi_profiles_ts,
                    "skipped": True,
                    "message": "wifi busy",
                }
            try:
                await refresh_wifi_profiles(state)
            except Exception as e:
                logger.exception("[API] wifi profiles refresh failed")
                return {
                    "ok": False,
                    "iface": STA_IFACE,
                    "active_ssid": state.wifi_active,
                    "profiles": state.wifi_profiles,
                    "ts": state.wifi_profiles_ts,
                    "error": str(e),
                }

        return {
            "ok": True,
            "iface": STA_IFACE,
            "active_ssid": state.wifi_active,
            "profiles": state.wifi_profiles,
            "ts": state.wifi_profiles_ts,
        }

    @app.post("/api/wifi/connect")
    async def wifi_connect(body: WifiConnectIn):
        ssid = body.ssid.strip()
        logger.info("[API] wifi connect requested ssid=%s", ssid if ssid is not None else "<NULL>")
        if not ssid:
            return {
                "ok": False,
                "code": "bad_request",
                "message": "ssid is required"
            }

        # 이미 active면 스킵
        if state.wifi_active == ssid:
            return {
                "ok": True,
                "skipped": True,
                "ssid": ssid,
                "message": "already connected"
            }

        state.wifi_busy = True
        try:
            res = await async_provision_connect_wlan0(ssid, body.password)
            if not res.ok:
                logger.warning("[API] wifi connect failed ssid=%s msg=%s", ssid, res.message)
                return {
                    "ok": False,
                    "code": "wifi_connect_failed",
                    "message": res.message,
                    "new_profile": res.new_profile
                }
            logger.info("[API] wifi connect ok ssid=%s", ssid)
            return {
                "ok": True,
                "iface": STA_IFACE,
                "ssid": ssid,
                "message": res.message
            }
        except asyncio.CancelledError:
          raise
        except subprocess.CalledProcessError as e:
            msg = (e.stderr or e.output or "").strip() or f"nmcli failed (exit={e.returncode})"
            return {
                "ok": False,
                "code": "wifi_connect_error",
                "message": msg
            }
        except Exception as e:
            logger.exception("[API] wifi connect unexpected error")
            return {
                "ok": False,
                "code": "wifi_connect_error",
                "message": str(e)
            }
        finally:
            state.wifi_busy = False
    
    @app.post("/api/wifi/profile/connect")
    async def wifi_profile_connect(body: WifiProfileConnectIn):
        name = body.name.strip()
        if not name:
            return {
                "ok": False,
                "code": "bad_request",
                "message": "name is required"
            }

        # 이미 active면 스킵
        if state.wifi_active == name:
            return {
                "ok": True,
                "skipped": True,
                "requested": name,
                "message": "already connected"
            }

        state.wifi_busy = True
        try:
            await async_bind_profile_to_wlan0(name)
            await async_up_profile_on_wlan0(name)
            return {
                "ok": True,
                "requested": name,
                "message": "connect requested"
            }
        except subprocess.CalledProcessError as e:
            msg = (e.stderr or e.output or "").strip() or f"nmcli failed (exit={e.returncode})"
            return {
                "ok": False,
                "code": "wifi_profile_connect_failed",
                "message": msg
            }
        except Exception as e:
            return {
                "ok": False,
                "code": "wifi_profile_connect_error",
                "message": str(e)
            }
        finally:
            state.wifi_busy = False


    @app.post("/api/wifi/profile/delete")
    async def wifi_profile_delete(body: WifiDeleteProfileIn):
        name = body.name.strip()
        if not name:
            return {
                "ok": False,
                "code": "bad_request",
                "message": "name is required"
            }

        state.wifi_busy = True
        try:
            await async_delete_profile(name)
            return {
                "ok": True,
                "deleted": name,
                "message": "deleted"
            }
        except subprocess.CalledProcessError as e:
            msg = (e.stderr or e.output or "").strip() or f"nmcli failed (exit={e.returncode})"
            return {
                "ok": False,
                "code": "wifi_profile_delete_failed",
                "message": msg
            }
        except Exception as e:
            return {
                "ok": False,
                "code": "wifi_profile_delete_error",
                "message": str(e)
            }
        finally:
            state.wifi_busy = False

    @app.get("/api/alerts/stream")
    async def alerts_stream():
        q = asyncio.Queue(maxsize=32)
        state.alert_subscribers.add(q)

        async def gen():
            try:
                last_ping = time.time()
                while True:
                    # 1) alert가 오면 즉시 전송
                    try:
                        ev = q.get_nowait()
                        yield f"event: alert\ndata: {json.dumps(ev, ensure_ascii=False)}\n\n"
                        continue
                    except asyncio.QueueEmpty:
                        pass

                    # 2) ping 주기 체크 (이벤트가 없을 때만)
                    now = time.time()
                    if (now - last_ping) >= 25.0:
                        last_ping = now
                        yield f": ping {now}\n\n"   # comment ping (버퍼 깨기)

                    # 3) 너무 바쁘게 돌지 않게 잠깐 대기
                    #    (여기 sleep은 "이벤트 도착 지연"을 만들 수 있으니 짧게)
                    try:
                        ev = await asyncio.wait_for(q.get(), timeout=0.5)
                        yield f"event: alert\ndata: {json.dumps(ev, ensure_ascii=False)}\n\n"
                    except asyncio.TimeoutError:
                        pass
            finally:
                state.alert_subscribers.discard(q)

        resp = StreamingResponse(gen(), media_type="text/event-stream")
        resp.headers["X-Accel-Buffering"] = "no"
        resp.headers["Cache-Control"] = "no-cache"
        resp.headers["Connection"] = "keep-alive"
        return resp

    async def _build_sender(uid: Any) -> dict | None:
        try:
            uid_i = int(uid) if uid is not None else None
        except Exception:
            uid_i = None

        if uid_i is None:
            return {"user_id": None, "name": "", "profile_image_url": ""}

        # 1) member_cache 우선
        m = None
        try:
            m = (state.member_cache or {}).get(uid_i)
        except Exception:
            m = None

        # 2) 없으면 DB fallback
        if m is None and getattr(state, "member_repo", None):
            m = state.member_repo.get(uid_i) or None
            if m is not None:
                # 캐시에 저장
                try:
                    state.member_cache[uid_i] = dict(m)
                except Exception:
                    pass

        name = str((m or {}).get("name") or "")
        profile_url = str((m or {}).get("profile_image_url") or "")

        # 프로필 캐싱(/profile/ 치환) 기존 로직 유지
        if profile_url and state.http_session and not state.http_session.closed:
            profile_url = await ensure_profile_cached(
                state.http_session, profile_url, timeout_sec=8.0
            )

        sender = {"user_id": uid_i, "name": name, "profile_image_url": profile_url}
        return sender

    @app.get("/api/slideshow/state")
    async def slideshow_state():
        cur = get_current_item(state)
        # stream/boot과 동일 AlbumItem 생성 로직으로 통일
        current = await build_album_item(state, cur)
        return {
            "ok": True,
            "ts": time.time(),
            "playing": bool(state.slide_playing),
            "interval_sec": float(state.slide_interval_sec or 60),
            "mode": state.slide_mode or "sequential",
            "current": current,
        }
    
    @app.get("/api/slideshow/stream")
    async def slideshow_stream():
        q: asyncio.Queue = asyncio.Queue(maxsize=16)
        state.slide_subscribers.add(q)

        # boot 이벤트도 emit_slide와 동일 스키마로 통일: {ts, seq, item, reason}
        async def _boot_slide_payload() -> dict:
            async with state.slide_lock:
                state.slide_seq += 1
                seq = state.slide_seq
                cur = get_current_item(state)
                # stream/state와 동일 AlbumItem 생성 로직으로 통일
                item = await build_album_item(state, cur)
                return {
                    "ts": time.time(),
                    "seq": seq,
                    "item": item,
                    "reason": "boot",
                }

        async def gen():
            try:
                # 연결 직후 1회 boot 이벤트 (스키마 통일)
                boot = await _boot_slide_payload()
                yield f"event: slide\ndata: {json.dumps(boot, ensure_ascii=False)}\n\n"
                while True:
                    ev = await q.get()
                    # SSE 포맷: event + data
                    yield f"event: slide\ndata: {json.dumps(ev, ensure_ascii=False)}\n\n"
            except asyncio.CancelledError:
                raise
            finally:
                state.slide_subscribers.discard(q)

        resp = StreamingResponse(gen(), media_type="text/event-stream")
        resp.headers["X-Accel-Buffering"] = "no"
        resp.headers["Cache-Control"] = "no-cache"
        resp.headers["Connection"] = "keep-alive"
        return resp

    async def _handle_ack_voice(vid: int, action: str) -> dict:
        """
        규칙:
        - 다운로드 X
        - 파일이 없으면 not_found
        - (옵션) 이미 playing이면 already_done
        - play: enqueue + duration_sec 반환
        - skip: 즉시 done(skipped) emit + 정리
        """
        if not state.voice_repo:
            return fail("not_found", None)

        # DB 존재 확인: pending/playing 상관없이 id가 있으면 유효로
        v = None
        try:
            v = state.voice_repo.get(int(vid))  # repo에 get(id) 필요
        except Exception:
            v = None
        if not v:
            # 명세: 최상위 reason에 not_found
            return {"ok": True, "reason": "not_found", "data": {"target": {"type": "voice", "id": int(vid)}, "action": action}}

        # ---- (옵션) 멱등성: 이미 playing이면 already_done ----
        # 의도: 같은 id에 대해 play가 여러 번 들어와도 중복 enqueue 방지
        try:
            if action == "play" and str(v.get("status") or "") == "playing":
                return {
                    "ok": True,
                    "reason": "already_done",
                    "data": {"target": {"type": "voice", "id": int(vid)}, "action": "play"},
                }
        except Exception:
            pass

        path = voice_path(vid)
        exists = os.path.exists(path) and os.path.getsize(path) > 0
        if not exists:
            # “SSE 즈음에 다운로드”가 실패했거나 아직 안 된 케이스
            return {"ok": True, "reason": "not_found", "data": {"target": {"type": "voice", "id": int(vid)}, "action": action}}
 
        if action == "skip":
            # 파일/DB 정리 + SSE voice_done(skipped)
            try:
                state.voice_repo.delete(int(vid))
            except Exception:
                pass
            try:
                os.remove(path)
            except Exception:
                pass
            try:
                await emit_voice_done(state, int(vid), "skipped")
            except Exception:
                pass
            return ok({"target": {"type": "voice", "id": int(vid)}, "action": "skip"})

        # action == "play"
        # duration 계산(이미 로컬 파일이므로 다운로드 없음)
        dur = None
        try:
            dur = float(await get_mp3_duration_sec(path))
        except Exception:
            dur = None

        # DB 상태 반영(선택): pending -> playing
        try:
            state.voice_repo.mark_playing(int(vid))
        except Exception:
            pass

        # 재생 완료 훅: voice_done(done) emit + DB 삭제 + 파일 삭제
        def _cleanup():
            # AudioManager 콜백 컨텍스트에서 안전하게 main loop에 스케줄
            try:
                loop = getattr(state, "loop", None)
                if loop and loop.is_running():
                    loop.call_soon_threadsafe(
                        asyncio.create_task,
                        emit_voice_done(state, int(vid), "done")
                    )
            except Exception:
                pass
            try:
                state.voice_repo.delete(int(vid))
            except Exception:
                pass
            try:
                os.remove(path)
            except Exception:
                pass

        await state.audio.enqueue(AudioJob(
            prio=int(AudioPrio.VOICE),
            kind="voice",
            path=path,
            ttl_sec=300.0,
            replace_key=None,   # batch sequential 의미 살리려면 None 권장
            on_done=_cleanup,
        ))

        payload = {"target": {"type": "voice", "id": int(vid)}, "action": "play"}
        if dur is not None and dur > 0:
            delay_s = 0.25
            payload["duration_sec"] = dur + delay_s
        return ok(payload)
    
    @app.post("/api/ack")
    async def ack(req: AckReq):
        try:
            if req.target.type != "voice":
                return fail("bad_request", None)
            return await _handle_ack_voice(req.target.id, req.action)
        except Exception:
            logger.exception("[api] /api/ack failed")
            return fail("bad_request", None)

    @app.post("/api/ack/batch")
    async def ack_batch(req: AckBatchReq):
        if req.mode != "sequential":
            return fail("bad_request", None)
        results = []
        for it in req.items:
            act = it.action or req.default_action
            try:
                r = await _handle_ack_voice(it.target.id, act)
                # results item 포맷: 스펙에서 ok/reason/duration_sec 등
                # 여기서는 공통응답 data를 results에 맞게 변환
                if r.get("ok"):
                    d = r.get("data") or {}
                    item_out = {
                        "target": d.get("target") or {"type": "voice", "id": int(it.target.id)},
                        "ok": True,
                        "reason": r.get("reason"),
                    }
                    if "duration_sec" in d:
                        item_out["duration_sec"] = d["duration_sec"]
                    results.append(item_out)
                else:
                    results.append({
                        "target": {"type": "voice", "id": int(it.target.id)},
                        "ok": False,
                        "reason": r.get("reason") or "bad_request",
                    })
            except Exception:
                logger.exception("[api] /api/ack/batch item failed id=%s", it.target.id)
                results.append({
                    "target": {"type": "voice", "id": int(it.target.id)},
                    "ok": False,
                    "reason": "bad_request",
                })
        return ok({"mode": "sequential", "default_action": req.default_action, "results": results})

    @app.post("/api/playback/skip_current")
    async def skip_current():
        # audio manager에 current 노출이 없으므로: 그냥 stop_current 시도
        try:
            await state.audio.stop_current()
            return ok({"skipped": True})
        except Exception:
            return {"ok": True, "reason": "no_current", "data": {"skipped": False}}

    @app.get("/api/voice/pending")
    async def voice_pending(limit: int = Query(100), offset: int = Query(0)):
        if not state.voice_repo:
            return ok({"items": [], "limit": limit, "offset": offset})

        items = state.voice_repo.list_pending(limit=limit, offset=offset)

        out = []
        for v in items:
            sender = await _build_sender(v.get("user_id"))
            out.append({
                "id": v["id"],
                "description": v.get("description") or "",
                "created_at": v.get("created_at"),
                "sender": sender if (sender["user_id"] or sender["name"] or sender["profile_image_url"]) else None,
            })
        return ok({"items": out, "limit": limit, "offset": offset})


    @app.get("/api/voice/stream")
    async def voice_stream():
        q = asyncio.Queue(maxsize=8)
        state.voice_subscribers.add(q)

        async def gen():
            try:
                while True:
                    env = await q.get()
                    # env = {"_event": "voice|voice_done", "data": {...}}
                    et = (env or {}).get("_event") or "voice"
                    data = (env or {}).get("data") or {}
                    yield f"event: {et}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
            finally:
                state.voice_subscribers.discard(q)

        resp = StreamingResponse(gen(), media_type="text/event-stream")
        resp.headers["X-Accel-Buffering"] = "no"
        resp.headers["Cache-Control"] = "no-cache"
        resp.headers["Connection"] = "keep-alive"
        return resp

    @app.post("/api/slideshow/play")
    async def slideshow_play(body: PlayReq):
        await set_playing(state, True, interval_sec=body.interval_sec)
        await emit_slide(state, reason="play")
        return {"ok": True}
    
    @app.post("/api/slideshow/pause")
    async def slideshow_pause():
        await set_playing(state, False)
        return {"ok": True}

    @app.post("/api/slideshow/next")
    async def slideshow_next():
        await next_slide(state, reason="next")
        return {"ok": True}

    @app.post("/api/slideshow/prev")
    async def slideshow_prev():
        await prev_slide(state, reason="prev")
        return {"ok": True}

    # ---- static: dist (UI) ----
    # - /album, /profile은 기존대로 정적 서빙 유지
    # - dist는 SPA 배포물: html=True로 index.html fallback 지원
    dist_dir = os.path.abspath(WEB_DIST_PATH or "./dist")
    if os.path.isdir(dist_dir):
        app.mount(
            "/",
            StaticFiles(directory=dist_dir, html=True),
            name="dist",
        )
        logger.info("[static] dist mounted dir=%s", dist_dir)
    else:
        logger.warning("[static] dist not found (skip mount) dir=%s", dist_dir)

    return app
