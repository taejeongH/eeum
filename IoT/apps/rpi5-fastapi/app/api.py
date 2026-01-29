import asyncio
import subprocess
import time
from queue import Queue
from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from .config import AP_IFACE, STA_IFACE
from .ap_manager import async_get_ipv4_addr
from .state import MonitorState, Event
from typing import Any, Dict, Optional
from .wifi_manager import (
        async_provision_connect_wlan0,
        async_bind_profile_to_wlan0,
        async_up_profile_on_wlan0,
        async_delete_profile,
        )
from .mqtt_client import MqttClient

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

def create_app(state: MonitorState) -> FastAPI:
    app = FastAPI()

    @app.get("/", response_class=HTMLResponse)
    def home():
        return """
<!doctype html>
<html>
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Home</title>
  <style>
    body{font-family:system-ui; margin:20px;}
    .card{border:1px solid #ddd; border-radius:10px; padding:14px; margin-bottom:12px;}
    button{padding:10px 14px; border-radius:8px; border:1px solid #ccc; background:#f7f7f7; cursor:pointer;}
    .muted{color:#666; font-size:14px;}
  </style>
</head>
<body>
  <h2>Home</h2>

  <div class="card">
    <div><b>Wi-Fi</b>: <span id="wifiState">(loading)</span></div>
    <div class="muted" id="wifiMsg"></div>
    <div style="margin-top:10px;">
      <button onclick="location.href='/wifi'">Wi-Fi 설정</button>
    </div>
  </div>

<script>
async function bootRouteOnce(){
  if(sessionStorage.getItem("bootChecked") === "1") return;
  sessionStorage.setItem("bootChecked", "1");
  document.getElementById("wifiMsg").innerText = "부팅 후 Wi-Fi 자동 연결 확인 중...";
  // 부팅 직후엔 연결이 늦게 잡힐 수 있으니 4번 정도 재시도
  for(let i=0;i<4;i++){
    const res = await fetch('/wifi/active');
    let data=null; try{ data=await res.json(); }catch(e){ data={ssid:null}; }
    if(data.ssid){
      return; // 연결됨 → 홈 유지
    }
    await new Promise(r=>setTimeout(r, 700));
  }

  // 끝까지 연결 안됨 → 설정 화면으로
  location.href = "/wifi";
}

bootRouteOnce();

async function refreshWifi(){
  const res = await fetch('/wifi/active');
  let data = null;
  try { data = await res.json(); } catch(e) { data = {ssid:null}; }
  document.getElementById('wifiState').innerText = data.ssid ? `Connected: ${data.ssid}` : 'Not connected';
}
refreshWifi();
setInterval(refreshWifi, 3000);
</script>
</body>
</html>
"""

    @app.get("/wifi", response_class=HTMLResponse)
    def wifi_page():
      return """
<!doctype html>
<html>
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>WiFi Setup</title>
  <style>
    body{font-family:system-ui; margin:20px;}
    .card{border:1px solid #ddd; border-radius:10px; padding:14px; margin-bottom:12px;}
    button{padding:8px 12px; border-radius:8px; border:1px solid #ccc; background:#f7f7f7; cursor:pointer;}
    input{padding:8px; width:100%; margin-top:6px; border-radius:8px; border:1px solid #ccc;}
    select{padding:8px; width:100%; margin-top:6px; border-radius:8px; border:1px solid #ccc;}
    .row{display:flex; gap:8px;}
    .row > *{flex:1;}
    .muted{color:#666; font-size:14px;}
    pre{background:#f7f7f7; padding:10px; border-radius:8px; overflow:auto;}
  </style>
</head>
<body>
  <h2>WiFi Setup</h2>

  <div class="card">
    <div><b>Active</b>: <span id="active">(loading)</span></div>
    <div class="muted" id="activeInfo"></div>
    <div style="margin-top:10px" class="row">
        <button id="btnHome" onclick="location.href='/'">홈으로</button>
        <button onclick="scan()">Rescan</button>
        <button onclick="loadProfiles()">Load Profiles</button>
    </div>
  </div>

  <div class="card">
    <div class="muted" id="scanInfo"></div>
  </div>
  
  <div class="card">
    <b>Connect</b>
    <label>SSID (scan list)</label>
    <select id="ssidSelect" onchange="onSelectSSID()"></select>

    <label style="margin-top:10px; display:block;">SSID (manual)</label>
    <input id="ssidInput" placeholder="SSID"/>

    <label style="margin-top:10px; display:block;">Password</label>
    <input id="pwInput" type="password" placeholder="Password"/>

    <button style="margin-top:10px" onclick="connect()">Connect</button>
    <div class="muted" id="connectMsg"></div>
  </div>

  <div class="card">
    <b>Profiles</b>
    <div id="profilesList">(not loaded)</div>
    <div class="muted" id="profilesMsg"></div>
  </div>

<script>
function sleep(ms){ return new Promise(r=>setTimeout(r, ms)); }

let connecting = false;
let visibleSsids = new Set();

function setBusy(b){
  connecting = b;
  for(const btn of document.querySelectorAll("button")){
    if(btn.id === "btnHome") continue;   // 홈으로는 항상 가능
    btn.disabled = b;
  }
  document.body.style.cursor = b ? "wait" : "default";
}

let profilesLoadedOnce = false;

async function scan(){
  document.getElementById("scanInfo").innerText = "updating from cache...";
  const res = await fetch(`/wifi/scan`, { cache:"no-store" }); // 캐시만
  let data = null;
  try { data = await res.json(); }
  catch(e){ data = { active_ssid:null, aps:[], error:"invalid response" }; }

  document.getElementById("active").innerText = data.active_ssid ?? "(none)";
  document.getElementById("scanInfo").innerText =
    `cache: ${new Date((data.ts||0)*1000).toLocaleTimeString()} / ${(data.aps||[]).length} APs`;

  // visible ssids 갱신
  visibleSsids = new Set((data.aps || []).map(ap => (ap.ssid || "").trim()).filter(Boolean));

  const sel = document.getElementById("ssidSelect");
  sel.innerHTML = "";
  for (const ap of (data.aps||[])){
    if(!ap.ssid) continue;
    const opt = document.createElement("option");
    opt.value = ap.ssid;
    opt.textContent = `${ap.in_use ? "* " : ""}${ap.ssid} (${ap.signal}) ${ap.security}`;
    sel.appendChild(opt);
  }

  if(profilesLoadedOnce) await loadProfiles();
}


function onSelectSSID(){
  const sel = document.getElementById("ssidSelect");
  document.getElementById("ssidInput").value = sel.value;
}

async function waitActiveEquals(target, timeoutMs=15000){
  const t = (target||"").trim();
  const start = Date.now();

  while(Date.now() - start < timeoutMs){
    try{
      const r = await fetch("/wifi/active", { cache:"no-store" });
      const a = await r.json();
      const cur = (a && a.ssid ? String(a.ssid).trim() : "");
      if(cur && cur === t) return true;
    }catch(e){
      // ignore
    }
    await sleep(700);
  }
  return false;
}

async function loadProfiles(){
  const res = await fetch("/wifi/profiles", { cache:"no-store" }); // 캐시만
  let data=null;
  try{ data = await res.json(); }catch(e){ data = {profiles:[], error:"invalid response"}; }

  const root = document.getElementById("profilesList");
  root.innerHTML = "";

  const profiles = data.profiles ?? [];
  if(profiles.length === 0){
    root.innerText = "(no profiles)";
    return;
  }

  for(const p of profiles){
    const name = (p.name || "").trim();
    const ssid = ((p.ssid || name) || "").trim();

    const inRange = !ssid ? true : visibleSsids.has(ssid); // ssid 없으면 그냥 허용

    const row = document.createElement("div");
    row.style.display = "flex";
    row.style.gap = "8px";
    row.style.alignItems = "center";
    row.style.marginTop = "8px";

    const info = document.createElement("div");
    info.style.flex = "1";

    const activeMark = (p.active_device ? " *ACTIVE" : "");
    const rangeMark = inRange ? "" : " (out of range)";
    info.innerText = `${name}${activeMark} (ssid=${ssid}, autoconnect=${p.autoconnect ?? "?"})${rangeMark}`;

    const btnConn = document.createElement("button");
    btnConn.innerText = inRange ? "Connect" : "Connect?";
    btnConn.disabled = connecting;
    btnConn.onclick = () => {
        if(!inRange){
            const ok = confirm(
            `${ssid} 가 스캔에 보이지 않습니다.\n` +
            `• 신호가 약하거나 범위 밖일 수 있어요\n` +
            `• (드물게) hidden SSID일 수도 있어요\n\n` +
            `그래도 연결을 시도할까요?`
            );
            if(!ok) return;
        }
        connectProfile(name);
    };

    const btnDel = document.createElement("button");
    btnDel.innerText = "Delete";
    btnDel.disabled = connecting;
    btnDel.onclick = () => deleteProfile(name);

    row.appendChild(info);
    row.appendChild(btnConn);
    row.appendChild(btnDel);
    if(!inRange) row.style.opacity = "0.65";
    root.appendChild(row);
  }
  profilesLoadedOnce = true;
}

async function connectProfile(name){
  if(connecting) return;
  if(!name) return;

  setBusy(true);
  document.getElementById("profilesMsg").innerText = `connecting: ${name} ...`;

  try{
    const res = await fetch("/wifi/profile/connect", {
      method:"POST",
      headers: {"Content-Type":"application/json"},
      body: JSON.stringify({name})
    });

    let data=null;
    try{ data = await res.json(); }catch(e){ data={ok:false,message:"invalid response"}; }

    if(!data.ok){
      document.getElementById("profilesMsg").innerText =
        `failed: ${data.message ?? "unknown"}`;
      return;
    }

    if(data.skipped){
      document.getElementById("profilesMsg").innerText = "* already connected";
      return;
    }

    document.getElementById("profilesMsg").innerText = "waiting for link...";
    // 프로필명=SSID 정책이므로 name으로 비교
    const ok = await waitActiveEquals(name, 15000);

    if(ok){
      document.getElementById("profilesMsg").innerText = "* connected!";
      location.href = "/";
    }else{
      document.getElementById("profilesMsg").innerText =
        "still not connected (out of range?)";
    }
  } finally {
    setBusy(false);
    for(let i=0;i<3;i++){
        await scan();
        await loadProfiles();
        await sleep(600);
    }
  }
}

async function deleteProfile(name){
  if(connecting) return;
  if(!name) return;
  if(!confirm(`Delete profile: ${name}?`)) return;

  setBusy(true);
  document.getElementById("profilesMsg").innerText = `deleting: ${name} ...`;

  try{
    const res = await fetch("/wifi/profile/delete", {
      method:"POST",
      headers: {"Content-Type":"application/json"},
      body: JSON.stringify({name})
    });

    let data=null;
    try{ data = await res.json(); }catch(e){ data={ok:false,message:"invalid response"}; }

    if(!data.ok){
      document.getElementById("profilesMsg").innerText =
        `delete failed: ${data.message ?? "unknown"}`;
      return;
    }

    document.getElementById("profilesMsg").innerText = `deleted: ${name}`;
  } finally {
    setBusy(false);
    await loadProfiles();
  }
}

async function connect(){
  if(connecting) return;

  const ssid = document.getElementById("ssidInput").value.trim();
  const pw = document.getElementById("pwInput").value;
  if(!ssid){ alert("SSID required"); return; }

  setBusy(true);
  document.getElementById("connectMsg").innerText = "connecting...";

  try{
    const res = await fetch("/wifi/connect", {
      method:"POST",
      headers: {"Content-Type":"application/json"},
      body: JSON.stringify({ssid, password:pw})
    });

    let data=null;
    try{ data=await res.json(); }catch(e){ data={ok:false,message:"invalid"}; }

    if(!data.ok){
      document.getElementById("connectMsg").innerText = "X failed: " + (data.message ?? "failed");
      return;
    }

    // 스킵이면 15초 대기 없이 즉시 종료
    if(data.skipped){
      document.getElementById("connectMsg").innerText = "* already connected";
      return;
    }

    document.getElementById("connectMsg").innerText = "waiting for link...";
    const ok = await waitActiveEquals(ssid, 15000);

    if(ok){
      document.getElementById("connectMsg").innerText = "* connected!";
      location.href = "/";
    }else{
      document.getElementById("connectMsg").innerText =
        "still not connected (check password/signal)";
    }
  } finally {
    setBusy(false);
    // 연결 후/실패 후 화면 갱신(캐시만)
    for(let i=0;i<3;i++){
        await scan();
        await loadProfiles();
        await sleep(600);
    }
  }
}

// 초기 로드
async function initialLoad(){
  // ping 즉시
  fetch("/wifi/ui/ping", { method:"POST" }).catch(()=>{});
  // 설정 화면 열려있다는 신호(scan loop 동작 조건)
  // connect 중엔 ping 쉬기
  let uiPingTimer = setInterval(()=>{
    if(connecting) return;
    fetch("/wifi/ui/ping", { method:"POST" }).catch(()=>{});
  }, 3000);
  await scan();
  await loadProfiles();

  setInterval(() => {
    scan().catch(()=>{});
  }, 3000);
  setInterval(() => {
    loadProfiles().catch(()=>{});
  }, 3000);
}

initialLoad();
</script>

</body>
</html>
"""

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

    @app.post("/wifi/ui/ping")
    async def wifi_ui_ping():
        state.wifi_ui_last_ping = time.time()
        return {"ok": True, "ts": state.wifi_ui_last_ping}

    @app.post("/eeum/token")
    async def set_token(req: TokenReq):
      await state.device_store.async_set_token(req.token)

      if state.mqtt is None:
          state.mqtt = MqttClient(
              inbound_queue=state.mqtt_inbound,
              loop=state.loop,
              token=req.token,
              link_getter=state.device_store.build_pir_link,
          )
          state.mqtt.activate()
      else:
          state.mqtt.set_token(req.token)   # 연결 중이면 online 갱신
          state.mqtt.activate()

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
    
    @app.get("/wifi/scan")
    async def wifi_scan():
        return {
            "iface": STA_IFACE,
            "active_ssid": state.wifi_active,
            "aps": state.wifi_scan,
            "ts": state.wifi_cache_ts,
        }
    
    @app.get("/wifi/active")
    async def wifi_active():
        return {
            "iface": STA_IFACE,
            "ssid": state.wifi_active,
            "ts": state.wifi_active_ts
        }

    @app.get("/wifi/profiles")
    async def wifi_profiles():
        return {
            "iface": STA_IFACE,
            "active_ssid": state.wifi_active,
            "profiles": state.wifi_profiles,
            "ts": state.wifi_cache_ts,
        }

    @app.post("/wifi/connect")
    async def wifi_connect(body: WifiConnectIn):
        ssid = body.ssid.strip()
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
                return {
                    "ok": False,
                    "code": "wifi_connect_failed",
                    "message": res.message,
                    "new_profile": res.new_profile
                }
            return {
                "ok": True,
                "iface": STA_IFACE,
                "ssid": ssid,
                "message": res.message
            }
        except subprocess.CalledProcessError as e:
            msg = (e.stderr or e.output or "").strip() or f"nmcli failed (exit={e.returncode})"
            return {
                "ok": False,
                "code": "wifi_connect_error",
                "message": msg
            }
        except Exception as e:
            return {
                "ok": False,
                "code": "wifi_connect_error",
                "message": str(e)
            }
        finally:
            state.wifi_busy = False
    
    @app.post("/wifi/profile/connect")
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


    @app.post("/wifi/profile/delete")
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

    return app
