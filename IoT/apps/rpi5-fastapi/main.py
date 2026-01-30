import asyncio
import uvicorn
import os
import logging
from app.config import AP_IFACE, HOST, PORT, DEVICE_PATH, DEFAULT_DEVICE, STA_IFACE
from app.ap_manager import async_ap_up, async_get_ipv4_addr
from app.state import MonitorState
from app.api import create_app
from app.consumer import consume_events, consume_mqtt_inbound, consume_commands
from app.monitor import (
    wifi_active_loop,
    wifi_scan_loop,
    refresh_wifi_active,
    refresh_wifi_cache,
)
from app.json_store import JsonStateStore
from app.device_store import DeviceStore
from app.mqtt_client import MqttClient

def setup_logging():
    level = os.getenv("LOG_LEVEL", "INFO").upper()
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )

logger = logging.getLogger(__name__)

async def async_main():
    logger.info("[BOOT] start host=%s port=%s ap=%s sta=%s", HOST, PORT, AP_IFACE, STA_IFACE)
    last_err = None
    for i in range(3):
        try:
            await async_ap_up()
            last_err = None
            break
        except Exception as e:
            last_err = e
            logger.warning("[AP] ap_up failed (try %d/3): %s", i+1, e)
            await asyncio.sleep(1.0)
    
    if last_err is not None:
        logger.error("[AP] ap_up ultimately failed: %s", last_err)
    
    await asyncio.sleep(1.0)
    ap_ip = await async_get_ipv4_addr(AP_IFACE)
    logger.info("[AP] iface=%s, ip=%s", AP_IFACE, ap_ip)
    
    state = MonitorState()
    raw_store = JsonStateStore(DEVICE_PATH, default=DEFAULT_DEVICE)
    state.device_store = DeviceStore(raw_store)
    state.loop = asyncio.get_running_loop()
    app = create_app(state)
    config = uvicorn.Config(app, host=HOST, port=PORT)
    server = uvicorn.Server(config)
    
    token = state.device_store.get_token()
    if token:
        logger.info("[MQTT] enabled")
        state.mqtt = MqttClient(
            inbound_queue=state.mqtt_inbound,
            loop=state.loop,
            token=token,
            link_getter=state.device_store.build_pir_link,  # pir만 반영
        )
        state.mqtt.activate()
    else:
        logger.info("[MQTT] disabled (no token)")
        state.mqtt = None
    consumer_task = asyncio.create_task(consume_events(state))
    mqtt_in_task = asyncio.create_task(consume_mqtt_inbound(state))
    cmd_task = asyncio.create_task(consume_commands(state))
    await refresh_wifi_active(state)
    await refresh_wifi_cache(state)
    # Wi-Fi cache loops
    wifi_active_task = asyncio.create_task(wifi_active_loop(state, interval_sec=1.0))
    wifi_scan_task = asyncio.create_task(wifi_scan_loop(state, interval_sec=3.0, ui_recent_sec=10.0))
    try:
        logger.info("[HTTP] server starting")
        await server.serve()
    finally:
        logger.info("[SHUTDOWN] begin")
        state.shutting_down = True
        if state.mqtt:
            state.mqtt.deactivate()
        await state.queue.put(None)
        await state.mqtt_inbound.put((None, None))
        await state.cmd_queue.put(None)
        for t in (consumer_task, mqtt_in_task, cmd_task, wifi_active_task, wifi_scan_task):
            t.cancel()
        await asyncio.gather(
            consumer_task, mqtt_in_task, cmd_task, wifi_active_task, wifi_scan_task,
            return_exceptions=True
        )
        tasks = list(state.tasks.values())
        for t in tasks:
            t.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        state.tasks.clear()
        logger.info("[SHUTDOWN] done")

def main():
    setup_logging()
    try:
        asyncio.run(async_main())
    except KeyboardInterrupt:
        pass
    except Exception:
        logger.exception("[FATAL] unexpected error")
        raise

if __name__ == "__main__":
    main()
