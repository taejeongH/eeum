import asyncio
import subprocess
from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from .config import AP_IFACE, STA_IFACE
from .ap_manager import async_get_ipv4_addr
from .state import MonitorState, Event
from typing import Any, Dict
from .wifi_manager import (
        async_scan_wifi_wlan0,
        async_get_active_on_wlan0,
        async_list_wifi_profiles_wlan0,
        async_provision_connect_wlan0,
        async_delete_profile,
        )

class EventIn(BaseModel):
    kind: str
    device: str
    data: Dict[str, Any]

class WifiConnectIn(BaseModel):
    ssid: str
    password: str

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
    <div class="muted" id="scanInfo"></div>
    <div style="margin-top:10px" class="row">
      <button onclick="scan()">Rescan</button>
      <button onclick="loadProfiles()">Load Profiles</button>
    </div>
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
    <pre id="profiles">(not loaded)</pre>
  </div>

<script>
async function scan(){
  document.getElementById("scanInfo").innerText = "scanning...";
  const res = await fetch(`/wifi/scan`);
  let data = null;
  try {
    data = await res.json();
  } catch(e) {
    data = {
      active_ssid:null,
      aps:[],
      error:"invalid response" };
  }

  document.getElementById("active").innerText = data.active_ssid ?? "(none)";
  document.getElementById("scanInfo").innerText = `found ${data.aps.length} APs`;

  const sel = document.getElementById("ssidSelect");
  sel.innerHTML = "";
  for (const ap of data.aps){
    const opt = document.createElement("option");
    opt.value = ap.ssid;
    opt.textContent = `${ap.in_use ? "* " : ""}${ap.ssid} (${ap.signal}) ${ap.security}`;
    sel.appendChild(opt);
  }

  if (data.aps.length > 0){
    document.getElementById("ssidInput").value = data.aps[0].ssid;
  }
}

function onSelectSSID(){
  const sel = document.getElementById("ssidSelect");
  document.getElementById("ssidInput").value = sel.value;
}

async function connect(){
  const ssid = document.getElementById("ssidInput").value.trim();
  const pw = document.getElementById("pwInput").value;
  if(!ssid){
    alert("SSID required");
    return;
  }

  document.getElementById("connectMsg").innerText = "connecting...";
  const res = await fetch("/wifi/connect", {
    method:"POST",
    headers: {"Content-Type":"application/json"},
    body: JSON.stringify({ssid:ssid, password:pw})
  });

  let data = null;
  try { 
    data = await res.json();
  } catch(e) { 
    data = { ok:false, message:"invalid response" };
  }

  document.getElementById("connectMsg").innerText =
    data.ok ? ("* connected: " + (data.message ?? "")) : ("X failed: " + (data.message ?? "failed"));

  await scan();
}

async function loadProfiles(){
  const res = await fetch("/wifi/profiles");
  let data = null;
  try {
    data = await res.json();
  } catch(e) {
    data = { error: "invalid response" };
  }
  document.getElementById("profiles").innerText = JSON.stringify(data, null, 2);
}

scan();
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
                "timer_running": state._timer_task is not None
        }

    @app.post("/api/event")
    async def event(data: EventIn):
        ev = Event(**data.model_dump())
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
        try:
            aps = await async_scan_wifi_wlan0()
            active = await async_get_active_on_wlan0()
            return {
                "iface": STA_IFACE,
                "active_ssid": active,
                "aps": aps,
            }
        except Exception as e:
            return{
                "iface": STA_IFACE,
                "active_ssid": None,
                "aps": [],
                "error": str(e),
            }

    @app.get("/wifi/active")
    async def wifi_active():
        try:
            active = await async_get_active_on_wlan0()
            return {"iface": STA_IFACE, "ssid": active}
        except Exception as e:
            return {"iface": STA_IFACE, "ssid": None, "error": str(e)}
    
    @app.get("/wifi/profiles")
    async def wifi_profiles():
        try:
            profiles = await async_list_wifi_profiles_wlan0()
            active = await async_get_active_on_wlan0()
            return {
                "iface": STA_IFACE,
                "active_ssid": active,
                "profiles": [p.__dict__ for p in profiles],
            }
        except Exception as e:
            return {
                "iface": STA_IFACE,
                "active_ssid": None,
                "profiles": [],
                "error": str(e),
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

        try:
            res = await async_provision_connect_wlan0(ssid, body.password)
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
    
    @app.post("/wifi/profile/delete")
    async def wifi_profile_delete(body: WifiDeleteProfileIn):
        name = body.name.strip()
        if not name:
            return {"ok": False, "code": "bad_request", "message": "name is required"}

        try:
            await async_delete_profile(name)
        except subprocess.CalledProcessError as e:
            msg = (e.stderr or e.output or "").strip()
            if not msg:
                msg = f"nmcli failed (exit={e.returncode})"
            return {
                "ok": False,
                "code": "wifi_profile_delete_failed",
                "message": msg,
            }
        except Exception as e:
            return {"ok": False, "code": "wifi_profile_delete_error", "message": str(e)}

        return {"ok": True, "deleted": name, "message": "deleted"}

    return app
