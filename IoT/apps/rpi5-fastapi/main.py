import asyncio
import uvicorn
from app.config import AP_IFACE, HOST, PORT
from app.ap_manager import async_ap_up, async_get_ipv4_addr
from app.state import MonitorState
from app.api import create_app
from app.consumer import consume_events

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
    try:
        await server.serve()
    finally:
        consumer_task.cancel()
        try:
            await consumer_task
        except asyncio.CancelledError:
            pass

def main():
    asyncio.run(async_main())

if __name__ == "__main__":
    main()
