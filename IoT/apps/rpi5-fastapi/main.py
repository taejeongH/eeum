import asyncio
import uvicorn
from app.config import AP_IFACE, HOST, PORT
from app.ap_manager import async_ap_up, async_get_ipv4_addr
from app.state import MonitorState
from app.api import create_app
from app.consumer import consume_events
from app.monitor import wifi_active_loop, wifi_scan_loop

async def async_main():
    last_err = None
    for i in range(3):
        try:
            await async_ap_up()
            last_err = None
            break
        except Exception as e:
            last_err = e
            print(f"[AP] ap_up failed (try {i+1}/3): {e}")
            await asyncio.sleep(1.0)
    
    if last_err is not None:
        print(f"[AP] ap_up ultimately failed: {last_err}")
    
    await asyncio.sleep(1.0)
    ap_ip = await async_get_ipv4_addr(AP_IFACE)
    print(f"[AP] iface={AP_IFACE}, ip={ap_ip}")
    
    state = MonitorState()
    app = create_app(state)
    config = uvicorn.Config(app, host=HOST, port=PORT)
    server = uvicorn.Server(config)

    consumer_task = asyncio.create_task(consume_events(state))

    # Wi-Fi cache loops
    wifi_active_task = asyncio.create_task(wifi_active_loop(state, interval_sec=1.0))
    wifi_scan_task = asyncio.create_task(wifi_scan_loop(state, interval_sec=3.0, ui_recent_sec=10.0))
    try:
        await server.serve()
    finally:
        for t in (consumer_task, wifi_active_task, wifi_scan_task):
            t.cancel()
        for t in (consumer_task, wifi_active_task, wifi_scan_task):
            try:
                await t
            except asyncio.CancelledError:
                pass

def main():
    asyncio.run(async_main())

if __name__ == "__main__":
    main()
