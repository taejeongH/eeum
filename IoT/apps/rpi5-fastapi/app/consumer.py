from .state import MonitorState, Event
from .monitor import (
    async_record_event,
    handle_pir_motion,
    handle_vision,
)

def _is_pir_motion(ev: Event) -> bool:
    return (
        ev.kind == "pir"
        and ev.data.get("event") == "motion"
        and ev.data.get("value") in (1, True)
    )

def _is_vision(ev: Event) -> bool:
    return (
        ev.kind == "vision"
        and (ev.data or {}).get("event") in ("fall", "enter", "exit")
    )

async def consume_events(state: MonitorState):
    while True:
        ev = await state.queue.get()

        try:
            await async_record_event(state, ev)

            if _is_pir_motion(ev):
                print(f"[PIR] motion from {ev.device_id} at {ev.detected_at}")
                handle_pir_motion(state, ev)

            elif _is_vision(ev):
                v = (ev.data or {}).get("event")
                if v == "fall":
                    print(
                        f"[VISION] event=fall level={(ev.data or {}).get('level')} "
                        f"location={(ev.data or {}).get('location_id')} "
                        f"from {ev.device_id} at {ev.detected_at}"
                    )
                else:
                    print(
                        f"[VISION] event={v} location={(ev.data or {}).get('location_id')} "
                        f"from {ev.device_id} at {ev.detected_at}"
                    )
                handle_vision(state, ev)

        except Exception as e:
            print(f"[consumer] error: {e} (ev={ev})")
